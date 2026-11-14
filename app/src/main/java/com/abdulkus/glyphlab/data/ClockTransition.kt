package com.abdulkus.glyphlab.data

enum class ClockTransition(val ru: String, val en: String) {
    RANDOM("Чередовать", "Alternate"),
    DISSOLVE("Растворение", "Pixel dissolve"),
    SPLIT("Расщепление", "Split"),
    REASSEMBLE("Перестройка", "Reassemble"),
    RIPPLE("Волна", "Ripple"),
    NONE("Без перехода", "None")
}
