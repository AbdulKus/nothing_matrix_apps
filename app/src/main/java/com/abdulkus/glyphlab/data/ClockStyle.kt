package com.abdulkus.glyphlab.data

enum class ClockFont(val ru: String, val en: String, val width: Int) {
    CLASSIC("Классика · 3×5", "Classic · 3×5", 3),
    SLIM("Прямой · 3×5", "Straight · 3×5", 3),
    ROUND("Скруглённый · 3×5", "Rounded · 3×5", 3),
    WIDE("Широкий · 4×5", "Wide · 4×5", 4),
    TECH("Техно · 4×5", "Tech · 4×5", 4)
}

enum class ClockLayout(val ru: String, val en: String) {
    STACKED("Две строки", "Two rows"),
    HORIZON("Горизонт", "Horizon"),
    ANALOG("Циферблат", "Analog dial")
}
