package com.abdulkus.glyphlab.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.round
import kotlin.math.abs

/** Weather uses approximate foreground location, then refreshes the saved area without tracking. */
class WeatherRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("glyph_weather", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(WeatherState(readSnapshot()))
    val state = _state.asStateFlow()
    @Volatile private var lastAttempt = 0L

    fun hasLocationPermission() = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Called only from a visible activity, after the location button or permission grant. */
    suspend fun updateLocation() {
        if (!hasLocationPermission()) { _state.value = _state.value.copy(message = "weather_permission"); return }
        mutex.withLock {
            _state.value = _state.value.copy(loading = true, message = null)
            try {
                val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
                    .filter { lm.allProviders.contains(it) && lm.isProviderEnabled(it) }
                val recent = providers.mapNotNull { lm.getLastKnownLocation(it) }
                    .filter { (android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) in 0L..(30L * 60 * 1_000_000_000) }
                    .maxByOrNull { it.elapsedRealtimeNanos }
                val location = recent ?: providers.firstOrNull()?.let { provider ->
                    withTimeoutOrNull(20_000L) {
                        suspendCancellableCoroutine<Location?> { continuation ->
                            val signal = CancellationSignal()
                            continuation.invokeOnCancellation { signal.cancel() }
                            lm.getCurrentLocation(provider, signal, app.mainExecutor) { result ->
                                if (continuation.isActive) continuation.resume(result)
                            }
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
                val oldLat = prefs.getString("latitude", null)?.toDoubleOrNull()
                val oldLon = prefs.getString("longitude", null)?.toDoubleOrNull()
                if (oldLat != null && oldLon != null && (abs(oldLat - lat) > 0.1 || abs(oldLon - lon) > 0.1)) {
                    prefs.edit().remove("current").apply()
                    _state.value = WeatherState(loading = true)
                }
                prefs.edit().putString("latitude", lat.toString()).putString("longitude", lon.toString()).apply()
                fetch(lat, lon)
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

    /** Cheap even from an animation loop. Network retries are bounded to once per 5 min. */
    @Synchronized fun refreshIfNeeded() {
        if (!hasLocationPermission()) return
        val now = System.currentTimeMillis()
        if (_state.value.loading || now - lastAttempt in 0 until RETRY_MS) return
        if (_state.value.snapshot?.let { now - it.observedAt in 0 until REFRESH_MS } == true) return
        if (!prefs.contains("latitude") || !prefs.contains("longitude")) return
        lastAttempt = now
        scope.launch {
            mutex.withLock {
                val lat = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return@withLock
                val lon = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return@withLock
                fetch(lat, lon)
            }
        }
    }

    private suspend fun fetch(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        lastAttempt = System.currentTimeMillis()
        _state.value = _state.value.copy(loading = true, message = null)
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=weather_code,is_day,temperature_2m,wind_speed_10m,rain,snowfall&timeformat=unixtime&forecast_days=1")
            val connection = url.openConnection() as HttpURLConnection
            val body = try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                check(connection.responseCode == 200)
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally { connection.disconnect() }
            val current = JSONObject(body).getJSONObject("current")
            val snapshot = parse(current)
            check(System.currentTimeMillis() - snapshot.observedAt in -900_000L..WeatherSnapshot.MAX_AGE_MS)
            prefs.edit().putString("current", current.toString()).apply()
            _state.value = WeatherState(snapshot)
        } catch (cancelled: CancellationException) {
            _state.value = _state.value.copy(loading = false)
            throw cancelled
        } catch (_: Exception) {
            _state.value = _state.value.copy(loading = false, message = "weather_network_error")
        }
    }

    private fun readSnapshot(): WeatherSnapshot? = runCatching {
        parse(JSONObject(prefs.getString("current", null) ?: return null))
    }.getOrNull()

    private fun parse(json: JSONObject) = WeatherSnapshot(
        observedAt = json.getLong("time") * 1000,
        condition = WeatherCondition.fromWmo(json.getInt("weather_code"), json.getInt("is_day") == 1,
            json.getDouble("wind_speed_10m"), json.getDouble("temperature_2m"), json.getDouble("rain"), json.getDouble("snowfall")),
        temperature = json.getDouble("temperature_2m")
    )

    companion object {
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

data class WeatherState(val snapshot: WeatherSnapshot? = null, val loading: Boolean = false, val message: String? = null)

data class WeatherSnapshot(val observedAt: Long, val condition: WeatherCondition, val temperature: Double) {
    fun currentCondition(now: Long = System.currentTimeMillis()) =
        if (now - observedAt in -900_000L..MAX_AGE_MS) condition else WeatherCondition.UNKNOWN
    companion object { const val MAX_AGE_MS = 3L * 60 * 60 * 1000 }
}
