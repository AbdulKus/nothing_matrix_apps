package com.abdulkus.glyphlab.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.abdulkus.glyphlab.data.*
import com.abdulkus.glyphlab.engine.*
import com.abdulkus.glyphlab.weather.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val Ink = Color(0xFFF4F4F2)
private val Dim = Color(0xFF949494)

@Composable
fun WeatherSettings(
    language: AppLanguage,
    preview: @Composable (WeatherSnapshot?, WeatherCondition) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { WeatherRepository.get(context) }
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var showGallery by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var showCities by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) scope.launch { repository.updateLocation() }
    }
    LaunchedEffect(repository, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (repository.hasLocationPermission() && !repository.hasSavedLocation()) repository.updateLocation()
            while (true) {
                now = System.currentTimeMillis()
                repository.refreshIfNeeded()
                delay(60_000)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        val snapshot = state.snapshot
        val condition = snapshot?.condition ?: WeatherCondition.UNKNOWN
        preview(snapshot, condition)
        state.city?.let { Text(it, color = Ink, fontWeight = FontWeight.Medium) }
        if (snapshot != null) {
            val updated = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(snapshot.observedAt))
            val stale = if (snapshot.isCurrent(now)) "" else " · ${language.t("weather_stale")}"
            Text("${language.t("weather_updated")} $updated$stale", color = Dim, fontSize = 12.sp)
        }
        val message = if (permissionDenied) "weather_permission" else state.message
        if (message != null) Text(language.t(message), color = Color(0xFFE0B25A), fontSize = 12.sp)
        Button(onClick = {
            if (repository.hasLocationPermission()) scope.launch { repository.updateLocation() }
            else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Text(language.t(if (state.loading) "weather_loading" else "weather_locate"), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showCities = true }, modifier = Modifier.weight(1f)) {
                Text(language.t("weather_choose_city"), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = { showGallery = true }, modifier = Modifier.weight(1f)) {
                Text(language.t("weather_gallery_short"), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        Text(language.t("weather_source"), color = Dim, fontSize = 11.sp)
    }
    if (showGallery) WeatherGallery(language) { showGallery = false }
    if (showCities) {
        WeatherCityDialog(language, repository, onDismiss = { showCities = false }) { city ->
            showCities = false
            permissionDenied = false
            scope.launch { repository.selectCity(city) }
        }
    }
}

@Composable
private fun WeatherCityDialog(
    language: AppLanguage,
    repository: WeatherRepository,
    onDismiss: () -> Unit,
    onSelect: (WeatherCity) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var cities by remember { mutableStateOf<List<WeatherCity>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.t("weather_choose_city")) },
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        cities = emptyList()
                        message = null
                    },
                    enabled = !searching,
                    label = { Text(language.t("weather_city_name")) },
                    singleLine = true
                )
                message?.let { Text(language.t(it), color = Dim) }
                cities.forEach { city ->
                    TextButton(onClick = { onSelect(city) }, modifier = Modifier.fillMaxWidth()) {
                        Text(city.label)
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = query.trim().length >= 2 && !searching, onClick = {
                searching = true
                message = null
                cities = emptyList()
                scope.launch {
                    try {
                        cities = repository.searchCities(query, language.storageCode)
                        if (cities.isEmpty()) message = "weather_no_cities"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        message = "weather_network_error"
                    } finally {
                        searching = false
                    }
                }
            }) { Text(language.t(if (searching) "weather_loading" else "weather_search")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(language.t("close")) } }
    )
}

@Composable
fun WeatherGallery(language: AppLanguage, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(WeatherCondition.SUNNY) }
    var seconds by remember { mutableDoubleStateOf(0.0) }
    val preview by WeatherPreview.state.collectAsState()
    DisposableEffect(Unit) {
        WeatherPreview.show(selected)
        onDispose { WeatherPreview.clear() }
    }
    LaunchedEffect(selected) { WeatherPreview.show(selected) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) { seconds = (System.nanoTime() - start) / 1e9; delay(100) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.t("weather_gallery").replace("{count}", WeatherCondition.entries.size.toString()), fontSize = 17.sp) },
        text = {
            Column(Modifier.heightIn(max = 550.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelMatrix(preview.frame, Modifier.size(174.dp))
                Text(if (language == AppLanguage.RU) selected.ru else selected.en, color = Ink, modifier = Modifier.padding(8.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                WeatherCondition.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { condition ->
                            Column(Modifier.weight(1f).border(1.dp, if (selected == condition) Ink else Color(0xFF333333), RoundedCornerShape(8.dp))
                                .clickable { selected = condition }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                PixelMatrix(WeatherRenderer.render(condition, seconds), Modifier.size(78.dp))
                                Text(if (language == AppLanguage.RU) condition.ru else condition.en, color = Ink, fontSize = 10.sp, textAlign = TextAlign.Center)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(language.t("close")) } }
    )
}

@Composable
fun ClockSettings(language: AppLanguage, config: MatrixConfig, onChange: (MatrixConfig) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(language.t("clock_layout"), color = Dim, fontSize = 11.sp)
        ClockLayout.entries.forEach { layout ->
            Row(Modifier.fillMaxWidth().border(1.dp, if (config.clockLayout == layout) Ink else Color(0xFF333333), RoundedCornerShape(6.dp))
                .clickable { onChange(config.copy(clockLayout = layout)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PixelMatrix(ClockRenderer.render(now, config.clockFont, layout), Modifier.size(72.dp))
                Text(if (language == AppLanguage.RU) layout.ru else layout.en, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
        if (config.clockLayout != ClockLayout.ANALOG) {
            Text(language.t("clock_font"), color = Dim, fontSize = 11.sp)
            ClockFont.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { font ->
                        Column(Modifier.weight(1f).border(1.dp, if (font == config.clockFont) Ink else Color(0xFF333333), RoundedCornerShape(6.dp))
                            .clickable { onChange(config.copy(clockFont = font)) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            PixelMatrix(ClockRenderer.render(now, font, config.clockLayout), Modifier.size(90.dp))
                            Text(if (language == AppLanguage.RU) font.ru else font.en, color = Ink, fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Text(language.t("clock_transition"), color = Dim, fontSize = 11.sp)
        ClockTransition.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { style ->
                    OutlinedButton(onClick = { onChange(config.copy(clockTransition = style)) }, modifier = Modifier.weight(1f)) {
                        Text((if (config.clockTransition == style) "✓ " else "") + if (language == AppLanguage.RU) style.ru else style.en, fontSize = 11.sp)
                    }
                }
            }
        }
        ClockTransitionPreview(config)
        Text(language.t("clock_sleep_style"), color = Dim, fontSize = 11.sp)
    }
}

@Composable
fun PixelMatrix(frame: IntArray, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f).background(Color.Black, CircleShape).padding(5.dp)) {
        val step = size.minDimension / 13
        for (y in 0..12) for (x in 0..12) {
            if (MatrixEngine.isInsideMatrix(x, y)) {
                val value = frame[y * 13 + x] / 255f
                drawCircle(Color(0.075f + value * 0.88f, 0.075f + value * 0.88f, 0.075f + value * 0.88f), step * 0.29f,
                    Offset((x + 0.5f) * step, (y + 0.5f) * step))
            }
        }
    }
}

@Composable
private fun ClockTransitionPreview(config: MatrixConfig) {
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(config.clockTransition) {
        val start = System.nanoTime()
        while (true) { elapsed = (System.nanoTime() - start) / 1e9; delay(33) }
    }
    val phase = elapsed % 3.5
    val selected = if (config.clockTransition == ClockTransition.RANDOM)
        ClockTransitionRenderer.STYLES[(elapsed / 3.5).toInt() % ClockTransitionRenderer.STYLES.size] else config.clockTransition
    val clock = ClockRenderer.render(font = config.clockFont, layout = config.clockLayout)
    val destination = WeatherRenderer.render(WeatherCondition.RAIN, elapsed)
    val frame = ClockTransitionRenderer.compose(clock, destination, selected, ((phase - 0.7) / 0.95).coerceIn(0.0, 1.0), WeatherRenderer.render(WeatherCondition.RAIN, elapsed - phase))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PixelMatrix(frame, Modifier.size(110.dp)) }
}
