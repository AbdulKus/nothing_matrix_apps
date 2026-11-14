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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val Ink = Color(0xFFF4F4F2)
private val Dim = Color(0xFF949494)

@Composable
fun WeatherSettings(language: AppLanguage) {
    val context = LocalContext.current
    val repository = remember { WeatherRepository.get(context) }
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var showGallery by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) scope.launch { repository.updateLocation() }
    }
    LaunchedEffect(repository, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (repository.hasLocationPermission()) repository.updateLocation()
            while (true) {
                now = System.currentTimeMillis()
                repository.refreshIfNeeded()
                delay(60_000)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        val snapshot = state.snapshot
        val condition = snapshot?.currentCondition(now) ?: WeatherCondition.UNKNOWN
        Text(language.t("effect_weather"), color = Ink, fontWeight = FontWeight.Bold)
        Text(buildString {
            append(if (language == AppLanguage.RU) condition.ru else condition.en)
            if (snapshot != null && condition != WeatherCondition.UNKNOWN) append(" · ${snapshot.temperature.roundToInt()}°")
            if (snapshot != null) append("\n${language.t("weather_updated")} ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(snapshot.observedAt))}")
        }, color = Dim, fontSize = 12.sp)
        Text(language.t("weather_source"), color = Dim, fontSize = 11.sp)
        TextButton(onClick = {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://open-meteo.com/")))
        }) { Text("Open-Meteo.com ↗", fontSize = 11.sp) }
        val message = if (permissionDenied) "weather_permission" else state.message
        if (message != null) Text(language.t(message), color = Color(0xFFE0B25A), fontSize = 12.sp)
        Button(onClick = {
            if (repository.hasLocationPermission()) scope.launch { repository.updateLocation() }
            else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Text(language.t(if (state.loading) "weather_loading" else "weather_locate"), fontSize = 12.sp)
        }
        OutlinedButton(onClick = { showGallery = true }, modifier = Modifier.fillMaxWidth()) {
            Text(language.t("weather_gallery"), fontSize = 12.sp)
        }
    }
    if (showGallery) WeatherGallery(language) { showGallery = false }
}

@Composable
private fun WeatherGallery(language: AppLanguage, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(WeatherCondition.SUNNY) }
    var seconds by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) { seconds = (System.nanoTime() - start) / 1e9; delay(100) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.t("weather_gallery"), fontSize = 17.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelMatrix(WeatherRenderer.render(selected, seconds), Modifier.size(174.dp))
                Text(if (language == AppLanguage.RU) selected.ru else selected.en, color = Ink, modifier = Modifier.padding(8.dp))
                Text(language.t("weather_preview_hint"), color = Dim, fontSize = 11.sp, textAlign = TextAlign.Center)
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
