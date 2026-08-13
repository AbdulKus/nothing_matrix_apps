package com.abdulkus.glyphlab.data

enum class EffectType(val title: String, val shortTitle: String) {
    WIREFRAME("3D-фигуры", "3D"),
    FIRE("Живой огонь", "ОГОНЬ"),
    GRAVITY("Гравитация", "ПЕСОК"),
    PLASMA("Плазменные волны", "ПЛАЗМА"),
    STARFIELD("Гиперпрыжок", "ЗВЁЗДЫ")
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
    val autoBrightness: Boolean = false,
    val autoBrightnessSource: AutoBrightnessSource = AutoBrightnessSource.AMBIENT_LIGHT,
    val intensity: Float = 0.7f,
    val trail: Float = 0.35f,
    val particleCount: Int = 28,
    val showVertices: Boolean = true,
    val frameRate: Int = 24,
    val sleepClockEnabled: Boolean = false
)
