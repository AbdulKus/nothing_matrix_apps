package com.abdulkus.glyphlab

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.abdulkus.glyphlab.data.ConfigStore
import com.abdulkus.glyphlab.data.EffectType
import com.abdulkus.glyphlab.data.MatrixConfig
import com.abdulkus.glyphlab.data.SolidType
import com.abdulkus.glyphlab.engine.MatrixEngine
import com.abdulkus.glyphlab.glyph.GlyphConnection
import com.abdulkus.glyphlab.glyph.GlyphRuntime
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var runtime: GlyphRuntime
    private lateinit var store: ConfigStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = ConfigStore(this)
        runtime = GlyphRuntime(this).also { it.start(store.load()) }

        setContent {
            GlyphLabTheme {
                GlyphLabScreen(runtime, store, store.load())
            }
        }
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
private fun GlyphLabScreen(
    runtime: GlyphRuntime,
    store: ConfigStore,
    initial: MatrixConfig
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(initial) }
    var outputEnabled by remember { mutableStateOf(false) }
    val frame by runtime.frame.collectAsState()
    val connection by runtime.connection.collectAsState()

    LaunchedEffect(config) {
        runtime.updateConfig(config)
        store.save(config)
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
        Header(connection)
        MatrixPreview(frame)

        Text(
            text = config.effect.title.uppercase(),
            color = White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )

        EffectSelector(config.effect) { config = config.copy(effect = it) }
        Spacer(Modifier.height(18.dp))

        SettingsPanel(config) { config = it }
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
            shape = RoundedCornerShape(3.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(
                if (outputEnabled) "■  ОСТАНОВИТЬ МАТРИЦУ" else "▶  ЗАПУСТИТЬ НА МАТРИЦЕ",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp
            )
        }

        Button(
            onClick = { openGlyphToyManager(context) },
            colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = White),
            shape = RoundedCornerShape(3.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp)
        ) {
            Text("ДОБАВИТЬ В ALWAYS-ON GLYPH TOYS", fontSize = 12.sp, letterSpacing = 0.5.sp)
        }

        Text(
            "Интерактивный режим работает, пока приложение открыто. Для AOD используются последние сохранённые настройки.",
            color = Muted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 18.dp, horizontal = 10.dp)
        )
    }
}

@Composable
private fun Header(connection: GlyphConnection) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "GLYPH LAB",
                color = White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
            Text("PHONE (4a) PRO • 13×13", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        }
        val (label, color) = when (connection) {
            GlyphConnection.CONNECTED -> "ONLINE" to Color(0xFF8BCB69)
            GlyphConnection.CONNECTING -> "WAIT" to Color(0xFFE0B25A)
            GlyphConnection.UNAVAILABLE -> "PREVIEW" to Muted
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.border(1.dp, Line, RoundedCornerShape(20.dp)).padding(10.dp, 6.dp)
        ) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MatrixPreview(frame: IntArray) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.72f)
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
                    val color = lerp(Color(0xFF181818), White, value)
                    drawCircle(
                        color = color,
                        radius = radius,
                        center = Offset((x + 0.5f) * step, (y + 0.5f) * step)
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectSelector(selected: EffectType, onSelect: (EffectType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EffectType.entries.forEachIndexed { index, effect ->
            val active = selected == effect
            Column(
                modifier = Modifier
                    .width(86.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) White else Panel)
                    .border(1.dp, if (active) White else Line, RoundedCornerShape(3.dp))
                    .clickable { onSelect(effect) }
                    .padding(9.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0${index + 1}", color = if (active) Red else Muted, fontSize = 10.sp)
                Text(
                    effect.shortTitle,
                    color = if (active) Black else White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SettingsPanel(config: MatrixConfig, onChange: (MatrixConfig) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(4.dp))
            .border(1.dp, Line, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        Text("НАСТРОЙКИ / ${config.effect.shortTitle}", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(15.dp))

        if (config.effect == EffectType.WIREFRAME) {
            Label("ФОРМА")
            ChoiceRow(
                items = SolidType.entries.toList(),
                selected = config.solid,
                title = { it.title }
            ) { onChange(config.copy(solid = it)) }
            Spacer(Modifier.height(8.dp))
            ToggleRow("Яркие вершины", config.showVertices) {
                onChange(config.copy(showVertices = it))
            }
        }

        when (config.effect) {
            EffectType.FIRE, EffectType.PLASMA -> ConfigSlider(
                "ИНТЕНСИВНОСТЬ",
                config.intensity,
                { "${(it * 100).roundToInt()}%" }
            ) { onChange(config.copy(intensity = it)) }

            EffectType.GRAVITY -> {
                ConfigSlider(
                    "ЧАСТИЦЫ",
                    (config.particleCount - 8) / 48f,
                    { "${(8 + it * 48).roundToInt()}" }
                ) { onChange(config.copy(particleCount = (8 + it * 48).roundToInt())) }
                ConfigSlider("ШЛЕЙФ", config.trail, { "${(it * 100).roundToInt()}%" }) {
                    onChange(config.copy(trail = it))
                }
            }

            EffectType.STARFIELD -> ConfigSlider(
                "ДЛИНА ШЛЕЙФА",
                config.trail,
                { "${(it * 100).roundToInt()}%" }
            ) { onChange(config.copy(trail = it)) }

            EffectType.WIREFRAME -> Unit
        }

        ConfigSlider("СКОРОСТЬ", config.speed, { "${(it * 100).roundToInt()}%" }) {
            onChange(config.copy(speed = it))
        }
        ConfigSlider("ЯРКОСТЬ", config.brightness, { "${(it * 100).roundToInt()}%" }) {
            onChange(config.copy(brightness = it))
        }
        ToggleRow("Акселерометр", config.accelerometer) {
            onChange(config.copy(accelerometer = it))
        }
        if (config.accelerometer) {
            ConfigSlider(
                "РЕАКЦИЯ НА НАКЛОН",
                config.sensorStrength,
                { "${(it * 100).roundToInt()}%" }
            ) { onChange(config.copy(sensorStrength = it)) }
        }

        Spacer(Modifier.height(8.dp))
        Label("ЧАСТОТА КАДРОВ")
        ChoiceRow(
            items = listOf(12, 18, 24, 30),
            selected = config.frameRate,
            title = { "$it" }
        ) { onChange(config.copy(frameRate = it)) }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Float,
    display: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(top = 9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(label)
            Text(display(value), color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = White,
                activeTrackColor = Red,
                inactiveTrackColor = Line
            )
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
        Text(label.uppercase(), color = White, fontSize = 12.sp, letterSpacing = 0.4.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = Red,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Line
            )
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    items: List<T>,
    selected: T,
    title: (T) -> String,
    onSelect: (T) -> Unit
) {
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
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) White else PanelLight)
                    .border(1.dp, if (active) White else Line, RoundedCornerShape(2.dp))
                    .clickable { onSelect(item) }
                    .padding(horizontal = 11.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, letterSpacing = 0.7.sp)
}

private fun openGlyphToyManager(context: Context) {
    // Phone (4a) Pro has a dedicated AOD selector. Older/other matrix devices
    // expose the generic toy manager documented by Nothing.
    val managerComponents = listOf(
        ComponentName(
            "com.nothing.thirdparty",
            "com.nothing.thirdparty.matrix.toys.manager.AodToySelectActivity"
        ),
        ComponentName(
            "com.nothing.thirdparty",
            "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"
        )
    )

    managerComponents.forEach { component ->
        val opened = runCatching {
            context.startActivity(Intent().setComponent(component))
            true
        }.getOrDefault(false)
        if (opened) return
    }

    val glyphSettingsOpened = runCatching {
        context.startActivity(Intent("android.settings.GLYPH_INTERFACE_SETTINGS"))
        true
    }.getOrDefault(false)
    if (glyphSettingsOpened) return

    context.startActivity(Intent(Settings.ACTION_SETTINGS))
    Toast.makeText(
        context,
        "Откройте: Glyph Interface → Flip to Glyph → Always-on Glyph Toy",
        Toast.LENGTH_LONG
    ).show()
}
