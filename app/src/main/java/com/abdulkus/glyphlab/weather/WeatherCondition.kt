package com.abdulkus.glyphlab.weather

/** Explicit scenes: no random weather is shown when current data is unavailable. */
enum class WeatherCondition(val ru: String, val en: String) {
    SUNNY("Солнце", "Sunny"),
    CLEAR_NIGHT("Ясная ночь", "Clear night"),
    PARTLY_CLOUDY_DAY("Солнце за облаком", "Partly cloudy day"),
    PARTLY_CLOUDY_NIGHT("Луна за облаком", "Partly cloudy night"),
    CLOUDY("Облачно", "Overcast"),
    FOG("Туман", "Fog"),
    DRIZZLE("Морось", "Drizzle"),
    RAIN("Дождь", "Rain"),
    HEAVY_RAIN("Ливень", "Heavy rain"),
    FREEZING_RAIN("Ледяной дождь", "Freezing rain"),
    SLEET("Мокрый снег", "Sleet"),
    SNOW("Снег", "Snow"),
    HEAVY_SNOW("Снегопад", "Heavy snow"),
    THUNDERSTORM("Гроза", "Thunderstorm"),
    HAIL("Град", "Hail"),
    WIND("Ветер", "Wind"),
    UNKNOWN("Нет данных", "No data");

    companion object {
        fun fromWmo(code: Int, isDay: Boolean, windKmh: Double = 0.0,
                    temperature: Double = 10.0, rain: Double = 0.0, snowfall: Double = 0.0): WeatherCondition {
            if (code in setOf(71, 73, 75, 77, 85, 86) && rain > 0 && snowfall > 0 && temperature > -2) return SLEET
            return when (code) {
                0, 1 -> if (windKmh >= 35) WIND else if (isDay) SUNNY else CLEAR_NIGHT
                2 -> if (windKmh >= 35) WIND else if (isDay) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
                3 -> if (windKmh >= 35) WIND else CLOUDY
                45, 48 -> FOG
                51, 53, 55 -> DRIZZLE
                56, 57, 66, 67 -> FREEZING_RAIN
                61, 63, 80, 81 -> RAIN
                65, 82 -> HEAVY_RAIN
                71, 73, 77, 85 -> SNOW
                75, 86 -> HEAVY_SNOW
                95 -> THUNDERSTORM
                96, 99 -> HAIL
                else -> UNKNOWN
            }
        }
    }
}
