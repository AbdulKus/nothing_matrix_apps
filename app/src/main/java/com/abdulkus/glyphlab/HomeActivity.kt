package com.abdulkus.glyphlab

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdulkus.glyphlab.data.AutoBrightnessSource
import com.abdulkus.glyphlab.data.ConfigStore
import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.data.SolidType
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.abdulkus.glyphlab.glyph.GlyphRuntime
import com.abdulkus.glyphlab.ui.AppLanguage
import com.abdulkus.glyphlab.ui.t
import com.abdulkus.glyphlab.update.GitHubUpdater
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class HomeActivity : ComponentActivity() {
    private lateinit var runtime: GlyphRuntime
    private lateinit var store: ConfigStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = ConfigStore(this)
        runtime = GlyphRuntime(this).also { it.start(store.load()) }
        val prefs = getSharedPreferences("glyphlab_ui", Context.MODE_PRIVATE)
        val initialLanguage = AppLanguage.fromStorageCode(prefs.getString("language", null))

        setContent {
            GlyphLabTheme {
                var language by remember { mutableStateOf(initialLanguage) }
                if (language == null) {
                    FirstRunLanguageScreen { selected ->
                        prefs.edit().putString("language", selected.storageCode).apply()
                        language = selected
                    }
                } else {
                    GlyphLabScreen(
                        runtime = runtime,
                        store = store,
                        initial = store.load(),
                        initialLanguage = language!!,
                        onLanguageChanged = { selected ->
                            prefs.edit().putString("language", selected.storageCode).apply()
                            language = selected
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GitHubUpdater.resumePendingInstall(this)
    }

    override fun onDestroy() {
        runtime.stop()
        super.onDestroy()
    }
}

private val Black = Color(0xFF050505)
private val Panel = Color(0xFF121212)
private val PanelLight = Color(0xFF1B1B1B)
private val White = Color(0xFFF4F4F2)
private val Muted = Color(0xFF949494)
private val Red = Color(0xFFD71921)
private val Line = Color(0xFF303030)
private val Success = Color(0xFF8BCB69)

private sealed interface UpdateState {
    data object Hidden : UpdateState
    data class Available(val info: GitHubUpdater.UpdateInfo) : UpdateState
    data class Downloading(val info: GitHubUpdater.UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: GitHubUpdater.UpdateInfo, val file: File) : UpdateState
    data class Error(val info: GitHubUpdater.UpdateInfo) : UpdateState
}

@Composable
private fun GlyphLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = White,
            secondary = Red,
            background = Black,
            surface = Panel,
            onPrimary = Black,
            onBackground = White,
            onSurface = White
        ),
        typography = MaterialTheme.typography.copy(
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
        ),
        content = content
    )
}

@Composable
private fun FirstRunLanguageScreen(onSelected: (AppLanguage) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "GLYPH LAB",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 29.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.6.sp
        )
        Text(
            "FOR PHONE (4a) PRO",
            color = Muted,
            fontSize = 10.sp,
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )
        Text(
            "CHOOSE LANGUAGE",
            color = White,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        AppLanguage.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { language ->
                    LanguageChoice(language, Modifier.weight(1f)) { onSelected(language) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LanguageChoice(language: AppLanguage, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(language.pickerCode, color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(9.dp))
        Text(language.nativeName, color = White, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun GlyphLabScreen(
    runtime: GlyphRuntime,
    store: ConfigStore,
    initial: MatrixConfig,
    initialLanguage: AppLanguage,
    onLanguageChanged: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(initial) }
    var outputEnabled by remember { mutableStateOf(false) }
    var language by remember(initialLanguage) { mutableStateOf(initialLanguage) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Hidden) }
    val frame by runtime.frame.collectAsState()

    LaunchedEffect(config) {
        runtime.updateConfig(config)
        store.save(config)
    }

    LaunchedEffect(Unit) {
        updateState = runCatching { GitHubUpdater.checkForUpdate() }
            .getOrNull()
            ?.let { UpdateState.Available(it) }
            ?: UpdateState.Hidden
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Header()

        UpdateBanner(
            language = language,
            state = updateState,
            onDownload = { info ->
                updateState = UpdateState.Downloading(info, 0f)
                scope.launch {
                    runCatching {
                        GitHubUpdater.download(context, info) { progress ->
                            scope.launch { updateState = UpdateState.Downloading(info, progress) }
                        }
                    }.onSuccess { file ->
                        updateState = UpdateState.Ready(info, file)
                    }.onFailure {
                        updateState = UpdateState.Error(info)
                    }
                }
            },
            onInstall = { file ->
                val started = GitHubUpdater.install(context, file)
                if (!started) {
                    Toast.makeText(context, language.t("install_permission"), Toast.LENGTH_LONG).show()
                }
            }
        )

        MatrixPreview(frame)

        Text(
            text = effectTitle(language, config.effect),
            color = White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )

        EffectSelector(language, config.effect) { config = config.copy(effect = it) }
        Spacer(Modifier.height(18.dp))

        SettingsPanel(language, config) { config = it }
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                outputEnabled = !outputEnabled
                runtime.setOutputEnabled(outputEnabled)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (outputEnabled) Red else White,
                contentColor = if (outputEnabled) White else Black
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(
                if (outputEnabled) language.t("stop") else language.t("start"),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.7.sp,
                fontSize = 12.sp
            )
        }

        Button(
            onClick = { openGlyphToyManager(context, language) },
            colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = White),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp)
        ) {
            Text(language.t("add_toy"), fontSize = 11.sp, letterSpacing = 0.45.sp)
        }

        Footer(
            language = language,
            onLanguage = { showLanguageDialog = true },
            onGitHub = { openUrl(context, "https://github.com/AbdulKus/nothing_matrix_apps") },
            onDonate = { openUrl(context, "https://abdulkus.github.io/donate") }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            language = language,
            onDismiss = { showLanguageDialog = false },
            onSelected = { selected ->
                language = selected
                onLanguageChanged(selected)
                showLanguageDialog = false
            }
        )
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp)
    ) {
        Text(
            "GLYPH LAB",
            color = White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.5.sp
        )
        Text(
            "FOR PHONE (4a) PRO",
            color = Muted,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun UpdateBanner(
    language: AppLanguage,
    state: UpdateState,
    onDownload: (GitHubUpdater.UpdateInfo) -> Unit,
    onInstall: (File) -> Unit
) {
    if (state == UpdateState.Hidden) return
    val info = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Error -> state.info
        UpdateState.Hidden -> return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .background(Panel, RoundedCornerShape(5.dp))
            .border(1.dp, Line, RoundedCornerShape(5.dp))
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(language.t("update_available"), color = Success, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
            Text(info.shortSha, color = Muted, fontSize = 10.sp)
        }
        Text(
            if (state is UpdateState.Error) language.t("update_error") else language.t("update_body"),
            color = if (state is UpdateState.Error) Red else White,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 7.dp, bottom = 10.dp)
        )
        when (state) {
            is UpdateState.Downloading -> {
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = Red,
                    trackColor = Line
                )
                Text(
                    "${language.t("downloading")} ${(state.progress * 100).roundToInt()}%",
                    color = Muted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
            is UpdateState.Ready -> CompactActionButton(language.t("install")) { onInstall(state.file) }
            is UpdateState.Available -> CompactActionButton(language.t("download")) { onDownload(state.info) }
            is UpdateState.Error -> CompactActionButton(language.t("download")) { onDownload(state.info) }
            UpdateState.Hidden -> Unit
        }
    }
}

@Composable
private fun CompactActionButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Black,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun MatrixPreview(frame: IntArray) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.70f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(Color.Black)
            .border(1.dp, Line, CircleShape)
            .padding(13.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.minDimension / MatrixEngine.SIZE
            val radius = step * 0.29f
            for (y in 0 until MatrixEngine.SIZE) {
                for (x in 0 until MatrixEngine.SIZE) {
                    if (!MatrixEngine.isInsideMatrix(x, y)) continue
                    val value = frame.getOrElse(y * MatrixEngine.SIZE + x) { 0 } / 255f
                    drawCircle(
                        color = lerp(Color(0xFF181818), White, value),
                        radius = radius,
                        center = Offset((x + 0.5f) * step, (y + 0.5f) * step)
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectSelector(language: AppLanguage, selected: EffectType, onSelect: (EffectType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EffectType.entries.forEachIndexed { index, effect ->
            val active = selected == effect
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) White else Panel)
                    .border(1.dp, if (active) White else Line, RoundedCornerShape(4.dp))
                    .clickable { onSelect(effect) }
                    .padding(9.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0${index + 1}", color = if (active) Red else Muted, fontSize = 10.sp)
                Text(
                    effectTitle(language, effect),
                    color = if (active) Black else White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SettingsPanel(language: AppLanguage, config: MatrixConfig, onChange: (MatrixConfig) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(5.dp))
            .border(1.dp, Line, RoundedCornerShape(5.dp))
            .padding(16.dp)
    ) {
        Text(
            "${language.t("settings")} · ${effectTitle(language, config.effect)}",
            color = Muted,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(15.dp))

        if (config.effect == EffectType.WIREFRAME) {
            Label(language.t("shape"))
            ChoiceRow(
                items = SolidType.entries.toList(),
                selected = config.solid,
                title = { solidTitle(language, it) }
            ) { onChange(config.copy(solid = it)) }
            Spacer(Modifier.height(8.dp))
            ToggleRow(language.t("bright_vertices"), config.showVertices) { onChange(config.copy(showVertices = it)) }
            Spacer(Modifier.height(8.dp))
            Label(language.t("rotation_axes"))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                AxisChoice("X", config.autoRotateX) { onChange(config.copy(autoRotateX = !config.autoRotateX)) }
                AxisChoice("Y", config.autoRotateY) { onChange(config.copy(autoRotateY = !config.autoRotateY)) }
                AxisChoice("Z", config.autoRotateZ) { onChange(config.copy(autoRotateZ = !config.autoRotateZ)) }
            }
        }

        when (config.effect) {
            EffectType.FIRE, EffectType.PLASMA -> ConfigSlider(language.t("intensity"), config.intensity, { "${(it * 100).roundToInt()}%" }) {
                onChange(config.copy(intensity = it))
            }
            EffectType.GRAVITY -> {
                ConfigSlider(language.t("particles"), (config.particleCount - 8) / 48f, { "${(8 + it * 48).roundToInt()}" }) {
                    onChange(config.copy(particleCount = (8 + it * 48).roundToInt()))
                }
                ConfigSlider(language.t("flow"), config.trail, { "${(it * 100).roundToInt()}%" }) {
                    onChange(config.copy(trail = it))
                }
            }
            EffectType.STARFIELD -> ConfigSlider(language.t("trail"), config.trail, { "${(it * 100).roundToInt()}%" }) {
                onChange(config.copy(trail = it))
            }
            EffectType.WIREFRAME, EffectType.CLOCK -> Unit
        }

        if (config.effect != EffectType.CLOCK) {
            ConfigSlider(language.t("speed"), config.speed, { "${(it * 100).roundToInt()}%" }) { onChange(config.copy(speed = it)) }
        } else {
            Label(language.t("minute_update"))
        }

        ToggleRow(language.t("auto_brightness"), config.autoBrightness) { onChange(config.copy(autoBrightness = it)) }
        if (config.autoBrightness) {
            Label(language.t("auto_source"))
            ChoiceRow(
                items = AutoBrightnessSource.entries.toList(),
                selected = config.autoBrightnessSource,
                title = { if (it == AutoBrightnessSource.AMBIENT_LIGHT) language.t("ambient") else language.t("screen") }
            ) { onChange(config.copy(autoBrightnessSource = it)) }
            ConfigSlider(language.t("min_brightness"), config.minimumBrightness, { "${(it * 100).roundToInt()}%" }) {
                onChange(config.copy(minimumBrightness = it.coerceAtMost(config.brightness)))
            }
        }

        ConfigSlider(
            if (config.autoBrightness) language.t("max_brightness") else language.t("matrix_brightness"),
            config.brightness,
            { "${(it * 100).roundToInt()}%" }
        ) {
            onChange(config.copy(brightness = it, minimumBrightness = config.minimumBrightness.coerceAtMost(it)))
        }

        if (config.effect != EffectType.CLOCK) {
            ToggleRow(language.t("accelerometer"), config.accelerometer) { onChange(config.copy(accelerometer = it)) }
            if (config.accelerometer) {
                ConfigSlider(language.t("tilt_response"), config.sensorStrength, { "${(it * 100).roundToInt()}%" }) {
                    onChange(config.copy(sensorStrength = it))
                }
            }
            Spacer(Modifier.height(8.dp))
            Label(language.t("frame_rate"))
            ChoiceRow(items = listOf(12, 18, 24, 30), selected = config.frameRate, title = { "$it" }) {
                onChange(config.copy(frameRate = it))
            }
        }

        Spacer(Modifier.height(18.dp))
        Label(if (config.effect == EffectType.CLOCK) language.t("display") else language.t("sleep_mode"))
        if (config.effect == EffectType.CLOCK) {
            ToggleRow(language.t("lock_only"), config.clockLockScreenOnly) { onChange(config.copy(clockLockScreenOnly = it)) }
        } else {
            ToggleRow(language.t("sleep_clock"), config.sleepClockEnabled) { onChange(config.copy(sleepClockEnabled = it)) }
        }
    }
}

@Composable
private fun ConfigSlider(label: String, value: Float, display: (Float) -> String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(label)
            Text(display(value), color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = White, activeTrackColor = Red, inactiveTrackColor = Line)
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label.uppercase(), color = White, fontSize = 11.sp, letterSpacing = 0.35.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White, checkedTrackColor = Red,
                uncheckedThumbColor = Muted, uncheckedTrackColor = Line
            )
        )
    }
}

@Composable
private fun RowScope.AxisChoice(label: String, checked: Boolean, onClick: () -> Unit) {
    Text(
        text = if (checked) "✓ $label" else label,
        color = if (checked) Black else White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(3.dp))
            .background(if (checked) White else PanelLight)
            .border(1.dp, if (checked) White else Line, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun <T> ChoiceRow(items: List<T>, selected: T, title: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items.forEach { item ->
            val active = item == selected
            Text(
                text = title(item).uppercase(),
                color = if (active) Black else White,
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) White else PanelLight)
                    .border(1.dp, if (active) White else Line, RoundedCornerShape(3.dp))
                    .clickable { onSelect(item) }
                    .padding(horizontal = 11.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, letterSpacing = 0.65.sp)
}

@Composable
private fun Footer(language: AppLanguage, onLanguage: () -> Unit, onGitHub: () -> Unit, onDonate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        FooterButton("${language.pickerCode} · ${language.t("language")}", onLanguage)
        FooterButton(language.t("github"), onGitHub)
        FooterButton(language.t("donate"), onDonate)
    }
}

@Composable
private fun RowScope.FooterButton(text: String, onClick: () -> Unit) {
    Text(
        text = text.uppercase(),
        color = White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Panel)
            .border(1.dp, Line, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp)
    )
}

@Composable
private fun LanguageDialog(language: AppLanguage, onDismiss: () -> Unit, onSelected: (AppLanguage) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(language.t("choose_language"), color = White, fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
        text = {
            Column {
                AppLanguage.entries.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { onSelected(item) }
                            .padding(vertical = 11.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.pickerCode, color = if (item == language) Red else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                        Text(item.nativeName, color = White, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = White) } }
    )
}

private fun effectTitle(language: AppLanguage, effect: EffectType): String = language.t(
    when (effect) {
        EffectType.WIREFRAME -> "effect_wireframe"
        EffectType.FIRE -> "effect_fire"
        EffectType.GRAVITY -> "effect_gravity"
        EffectType.PLASMA -> "effect_plasma"
        EffectType.STARFIELD -> "effect_starfield"
        EffectType.CLOCK -> "effect_clock"
    }
)

private fun solidTitle(language: AppLanguage, solid: SolidType): String = language.t(
    when (solid) {
        SolidType.CUBE -> "solid_cube"
        SolidType.TETRAHEDRON -> "solid_tetra"
        SolidType.OCTAHEDRON -> "solid_octa"
        SolidType.PYRAMID -> "solid_pyramid"
    }
)

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun openGlyphToyManager(context: Context, language: AppLanguage) {
    val managerComponents = listOf(
        ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.AodToySelectActivity"),
        ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")
    )
    managerComponents.forEach { component ->
        if (runCatching { context.startActivity(Intent().setComponent(component)); true }.getOrDefault(false)) return
    }
    if (runCatching { context.startActivity(Intent("android.settings.GLYPH_INTERFACE_SETTINGS")); true }.getOrDefault(false)) return
    context.startActivity(Intent(Settings.ACTION_SETTINGS))
    Toast.makeText(context, language.t("toy_hint"), Toast.LENGTH_LONG).show()
}
