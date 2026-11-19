package com.abdulkus.glyphlab.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.round
import kotlin.math.abs

/** Weather uses approximate foreground location, then refreshes the saved area without tracking. */
class WeatherRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("glyph_weather", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(WeatherState(readSnapshot(), city = prefs.getString("city", null)))
    val state = _state.asStateFlow()
    private val refreshQueued = AtomicBoolean()
    @Volatile private var lastAttempt: Long? = null

    fun hasLocationPermission() = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasSavedLocation() = prefs.contains("latitude") && prefs.contains("longitude")

    /** Called only from a visible activity, after the location button or permission grant. */
    suspend fun updateLocation() {
        if (!hasLocationPermission()) { _state.value = _state.value.copy(message = "weather_permission"); return }
        mutex.withLock {
            _state.value = _state.value.copy(loading = true, message = null)
            try {
                val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .filter { lm.allProviders.contains(it) && lm.isProviderEnabled(it) }
                val cachedProviders = providers + LocationManager.PASSIVE_PROVIDER
                val recent = cachedProviders.mapNotNull { provider ->
                    if (provider in lm.allProviders) lm.getLastKnownLocation(provider) else null
                }.filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0L..RECENT_LOCATION_NS }
                    .maxByOrNull { it.elapsedRealtimeNanos }
                val location = recent ?: withTimeoutOrNull(15_000) {
                    // Providers may return null independently. Take the first usable fix.
                    val fixes = kotlinx.coroutines.channels.Channel<Location?>(providers.size)
                    coroutineScope {
                        val requests = providers.map { provider ->
                            launch {
                                fixes.send(requestLocation(lm, provider))
                            }
                        }
                        try {
                            var fix: Location? = null
                            repeat(requests.size) {
                                if (fix == null) fix = fixes.receive()
                            }
                            fix
                        } finally {
                            requests.forEach { it.cancel() }
                        }
                    }
                }
                if (location == null) {
                    _state.value = _state.value.copy(loading = false, message = "weather_location_error")
                    return@withLock
                }
                // About 1 km precision is enough for a model weather grid.
                val lat = round(location.latitude * 100) / 100
                val lon = round(location.longitude * 100) / 100
                setArea(lat, lon, null)
            } catch (_: SecurityException) {
                _state.value = _state.value.copy(loading = false, message = "weather_permission")
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(loading = false)
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false, message = "weather_location_error")
            }
        }
    }

    suspend fun selectCity(city: WeatherCity) = mutex.withLock {
        setArea(city.latitude, city.longitude, city.label)
    }

    suspend fun searchCities(query: String, language: String): List<WeatherCity> = withContext(Dispatchers.IO) {
        val url = Uri.parse("https://geocoding-api.open-meteo.com/v1/search").buildUpon()
            .appendQueryParameter("name", query.trim())
            .appendQueryParameter("language", language)
            .appendQueryParameter("count", "5")
            .build()
        val results = readJson(URL(url.toString())).optJSONArray("results") ?: return@withContext emptyList()
        List(results.length()) { index ->
            val city = results.getJSONObject(index)
            val label = listOf(city.getString("name"), city.optString("admin1"), city.optString("country"))
                .filter { it.isNotBlank() }.distinct().joinToString(", ")
            WeatherCity(label, city.getDouble("latitude"), city.getDouble("longitude"))
        }
    }

    private suspend fun setArea(lat: Double, lon: Double, city: String?) {
        val oldLat = prefs.getString("latitude", null)?.toDoubleOrNull()
        val oldLon = prefs.getString("longitude", null)?.toDoubleOrNull()
        val moved = oldLat == null || oldLon == null || abs(oldLat - lat) > 0.1 || abs(oldLon - lon) > 0.1
        prefs.edit().apply {
            putString("latitude", lat.toString())
            putString("longitude", lon.toString())
            putString("city", city)
            if (moved) remove("current")
        }.apply()
        _state.value = _state.value.copy(
            snapshot = _state.value.snapshot.takeUnless { moved },
            city = city
        )
        fetch(lat, lon)
    }

    private suspend fun requestLocation(lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                lm.getCurrentLocation(provider, signal, app.mainExecutor) { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
            } catch (denied: SecurityException) {
                if (continuation.isActive) continuation.resumeWithException(denied)
            }
        }

    /** Cheap even from an animation loop. Network retries are bounded to once per five minutes. */
    fun refreshIfNeeded() {
        if (!shouldRefresh() || !refreshQueued.compareAndSet(false, true)) return
        scope.launch {
            try {
                refreshSavedLocation()
            } finally {
                refreshQueued.set(false)
            }
        }
    }

    suspend fun refreshSavedLocation() = mutex.withLock {
        if (!shouldRefresh()) return@withLock
        val lat = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return@withLock
        val lon = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return@withLock
        fetch(lat, lon)
    }

    private fun shouldRefresh(): Boolean {
        if (_state.value.loading || !hasSavedLocation()) return false
        if (_state.value.city == null && !hasLocationPermission()) return false
        if (lastAttempt?.let { SystemClock.elapsedRealtime() - it < RETRY_MS } == true) return false
        val now = System.currentTimeMillis()
        return _state.value.snapshot?.let { now - it.observedAt !in -900_000L until REFRESH_MS } ?: true
    }

    private suspend fun fetch(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        lastAttempt = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(loading = true, message = null)
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=weather_code,is_day,temperature_2m,wind_speed_10m,rain,showers,snowfall&timeformat=unixtime&forecast_days=1")
            val current = readJson(url).getJSONObject("current")
            val snapshot = parse(current)
            check(System.currentTimeMillis() - snapshot.observedAt in -900_000L..WeatherSnapshot.MAX_AGE_MS)
            prefs.edit().putString("current", current.toString()).apply()
            _state.value = WeatherState(snapshot, city = _state.value.city)
            runCatching { com.abdulkus.glyphlab.widget.WeatherWidgetProvider.updateStatic(app) }
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(loading = false)
            throw cancelled
        } catch (_: Exception) {
            _state.value = _state.value.copy(loading = false, message = "weather_network_error")
        }
    }

    private fun readJson(url: URL): JSONObject {
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readSnapshot(): WeatherSnapshot? = runCatching {
        parse(JSONObject(prefs.getString("current", null) ?: return null))
    }.getOrNull()

    private fun parse(json: JSONObject): WeatherSnapshot {
        val temperature = json.getDouble("temperature_2m")
        check(temperature.isFinite())
        return WeatherSnapshot(
            observedAt = json.getLong("time") * 1000,
            condition = WeatherCondition.fromWmo(
                json.optInt("weather_code", -1),
                json.optInt("is_day", 1) == 1,
                json.optDouble("wind_speed_10m", 0.0),
                temperature,
                json.optDouble("rain", 0.0) + json.optDouble("showers", 0.0),
                json.optDouble("snowfall", 0.0)
            ),
            temperature = temperature
        )
    }

    companion object {
        private const val RECENT_LOCATION_NS = 30L * 60 * 1_000_000_000
        private const val REFRESH_MS = 30L * 60 * 1000
        private const val RETRY_MS = 5L * 60 * 1000
        // The singleton retains only applicationContext, never an Activity.
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile private var instance: WeatherRepository? = null
        fun get(context: Context): WeatherRepository = instance ?: synchronized(this) {
            instance ?: WeatherRepository(context).also { instance = it }
        }
    }
}

data class WeatherCity(val label: String, val latitude: Double, val longitude: Double)

data class WeatherState(
    val snapshot: WeatherSnapshot? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val city: String? = null
)

data class WeatherSnapshot(val observedAt: Long, val condition: WeatherCondition, val temperature: Double) {
    fun isCurrent(now: Long = System.currentTimeMillis()) = now - observedAt in -900_000L..MAX_AGE_MS

    fun currentCondition(now: Long = System.currentTimeMillis()) =
        if (isCurrent(now)) condition else WeatherCondition.UNKNOWN
    companion object { const val MAX_AGE_MS = 3L * 60 * 60 * 1000 }
}
