package com.abdulkus.glyphlab.data

enum class EffectType(val title: String, val shortTitle: String) {
    WIREFRAME("3D-фигуры", "3D"),
    FIRE("Живой огонь", "ОГОНЬ"),
    GRAVITY("Гравитация", "ПЕСОК"),
    PLASMA("Плазменные волны", "ПЛАЗМА"),
    STARFIELD("Гиперпрыжок", "ЗВЁЗДЫ"),
    CLOCK("Часы", "ЧАСЫ"),
    WEATHER("Погода", "ПОГОДА")
}

enum class SolidType(val title: String) {
    CUBE("Куб"),
    TETRAHEDRON("Тетраэдр"),
    OCTAHEDRON("Октаэдр"),
    PYRAMID("Пирамида")
}

enum class AutoBrightnessSource(val title: String) {
    AMBIENT_LIGHT("Датчик света"),
    SCREEN_BRIGHTNESS("Яркость экрана")
}

enum class WidgetTheme(val titleEn: String, val titleRu: String, val isLight: Boolean = false) {
    SYSTEM("Frosted Wallpaper", "Матовое стекло (обои)", false),
    WALLPAPER_SOLID("Solid Wallpaper", "Цвет обоев (непрозрачный)", false),
    GLASS("Nothing Dark Glass", "Тёмное матовое стекло", false),
    SOLID_DARK("Solid Dark", "Тёмный непрозрачный", false),
    BLACK("AMOLED Black", "Чёрный AMOLED", false),
    LIGHT_GLASS("Nothing Light Glass", "Светлое матовое стекло", true),
    LIGHT_WALLPAPER("Light Wallpaper", "Светлый цвет обоев", true),
    SOLID_LIGHT("Solid Light", "Светлый непрозрачный", true),
    WHITE("Pure White", "Белый (Pure White)", true),
    TRANSPARENT("Transparent", "Без фона (только матрица)", false)
}

enum class WidgetAccentColor(val titleEn: String, val titleRu: String, val hexDarkBg: Int, val hexLightBg: Int) {
    WHITE("Nothing White", "Классический белый", 0xFFFFFFFF.toInt(), 0xFF121212.toInt()),
    RED("Nothing Red", "Фирменный красный", 0xFFD71921.toInt(), 0xFFD71921.toInt()),
    AMBER("Warm Amber", "Тёплый янтарный", 0xFFFF9800.toInt(), 0xFFE65100.toInt()),
    GREEN("Matrix Green", "Матричный зелёный", 0xFF4CAF50.toInt(), 0xFF2E7D32.toInt()),
    BLUE("Cyan Blue", "Неоновый синий", 0xFF00E5FF.toInt(), 0xFF0097A7.toInt()),
    MONOCHROME("Monochrome", "Монохромный контраст", 0xFFEEEEEE.toInt(), 0xFF101010.toInt())
}

data class MatrixConfig(
    val effect: EffectType = EffectType.WIREFRAME,
    val solid: SolidType = SolidType.CUBE,
    val speed: Float = 0.42f,
    val autoRotateX: Boolean = true,
    val autoRotateY: Boolean = true,
    val autoRotateZ: Boolean = false,
    val accelerometer: Boolean = true,
    val sensorStrength: Float = 0.75f,
    val brightness: Float = 0.9f,
    val minimumBrightness: Float = 0.01f,
    val autoBrightness: Boolean = false,
    val autoBrightnessSource: AutoBrightnessSource = AutoBrightnessSource.AMBIENT_LIGHT,
    val intensity: Float = 0.7f,
    val trail: Float = 0.35f,
    val particleCount: Int = 28,
    val showVertices: Boolean = true,
    val frameRate: Int = 24,
    val sleepClockEnabled: Boolean = false,
    val clockLockScreenOnly: Boolean = false,
    val clockFont: ClockFont = ClockFont.CLASSIC,
    val clockLayout: ClockLayout = ClockLayout.STACKED,
    val clockTransition: ClockTransition = ClockTransition.RANDOM,
    val widgetTheme: WidgetTheme = WidgetTheme.SYSTEM,
    val widgetAccent: WidgetAccentColor = WidgetAccentColor.WHITE
)