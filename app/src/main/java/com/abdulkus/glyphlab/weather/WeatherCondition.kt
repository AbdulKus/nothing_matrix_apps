package com.abdulkus.glyphlab.weather

/** Visual groups for all 29 documented Open-Meteo WMO codes, plus derived/demo scenes. */
enum class WeatherCondition(val ru: String, val en: String) {
    SUNNY("Ясно", "Sunny"),
    CLEAR_NIGHT("Ясно", "Clear night"),
    PARTLY_CLOUDY_DAY("Малооблачно", "Partly cloudy"),
    PARTLY_CLOUDY_NIGHT("Малооблачно", "Partly cloudy"),
    CLOUDY("Пасмурно", "Overcast"),
    FOG("Туман", "Fog"),
    RIME_FOG("Ледяной туман", "Rime fog"),
    DRIZZLE("Морось", "Drizzle"),
    FREEZING_DRIZZLE("Ледяная морось", "Freezing drizzle"),
    RAIN("Дождь", "Rain"),
    SHOWERS("Кратковременный дождь", "Rain showers"),
    HEAVY_RAIN("Ливень", "Heavy rain"),
    FREEZING_RAIN("Ледяной дождь", "Freezing rain"),
    SLEET("Мокрый снег", "Sleet"),
    SNOW("Снег", "Snow"),
    HEAVY_SNOW("Снегопад", "Heavy snow"),
    SNOW_GRAINS("Снежная крупа", "Snow grains"),
    SNOW_SHOWERS("Метель", "Snow showers"),
    THUNDERSTORM("Гроза", "Thunderstorm"),
    HEAVY_THUNDERSTORM("Сильная гроза", "Heavy thunderstorm"),
    HAIL("Град", "Hail"),
    THUNDERSTORM_HAIL("Гроза с градом", "Thunderstorm with hail"),
    WIND("Ветрено", "Windy"),
    UNKNOWN("Нет данных", "No data");

    companion object {
        fun fromWmo(code: Int, isDay: Boolean, windKmh: Double = 0.0,
                    temperature: Double = 10.0, rain: Double = 0.0, snowfall: Double = 0.0): WeatherCondition {
            if (code in setOf(61, 63, 65, 71, 73, 75, 77, 80, 81, 82, 85, 86) && rain > 0 && snowfall > 0 && temperature > -2) return SLEET
            return when (code) {
                0, 1 -> if (windKmh >= 35) WIND else if (isDay) SUNNY else CLEAR_NIGHT
                2 -> if (windKmh >= 35) WIND else if (isDay) PARTLY_CLOUDY_DAY else PARTLY_CLOUDY_NIGHT
                3 -> if (windKmh >= 35) WIND else CLOUDY
                45 -> FOG
                48 -> RIME_FOG
                51, 53, 55 -> DRIZZLE
                56, 57 -> FREEZING_DRIZZLE
                61, 63 -> RAIN
                80, 81 -> SHOWERS
                65, 82 -> HEAVY_RAIN
                66, 67 -> FREEZING_RAIN
                71, 73 -> SNOW
                75, 86 -> HEAVY_SNOW
                77 -> SNOW_GRAINS
                85 -> SNOW_SHOWERS
                95 -> THUNDERSTORM
                97 -> HEAVY_THUNDERSTORM
                96, 99 -> THUNDERSTORM_HAIL
                else -> UNKNOWN
            }
        }
    }
}
