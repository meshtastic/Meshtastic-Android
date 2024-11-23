package com.geeksville.mesh.util

import android.icu.util.LocaleData
import android.icu.util.ULocale
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig
import java.util.Locale

enum class DistanceUnit(
    val symbol: String,
    val multiplier: Float,
    val system: Int
) {
    METER("m", multiplier = 1F, DisplayConfig.DisplayUnits.METRIC_VALUE),
    KILOMETER("km", multiplier = 0.001F, DisplayConfig.DisplayUnits.METRIC_VALUE),
    FOOT("ft", multiplier = 3.28084F, DisplayConfig.DisplayUnits.IMPERIAL_VALUE),
    MILE("mi", multiplier = 0.000621371F, DisplayConfig.DisplayUnits.IMPERIAL_VALUE),
    ;

    companion object {
        fun getFromLocale(locale: Locale = Locale.getDefault()): DisplayConfig.DisplayUnits {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                when (LocaleData.getMeasurementSystem(ULocale.forLocale(locale))) {
                    LocaleData.MeasurementSystem.SI -> DisplayConfig.DisplayUnits.METRIC
                    else -> DisplayConfig.DisplayUnits.IMPERIAL
                }
            } else {
                when (locale.country.uppercase(locale)) {
                    "US", "LR", "MM", "GB" -> DisplayConfig.DisplayUnits.IMPERIAL
                    else -> DisplayConfig.DisplayUnits.METRIC
                }
            }
        }
    }
}

fun Int.metersIn(unit: DistanceUnit): Float {
    return this * unit.multiplier
}

fun Int.metersIn(system: DisplayConfig.DisplayUnits): Float {
    val unit = when (system.number) {
        DisplayConfig.DisplayUnits.IMPERIAL_VALUE -> DistanceUnit.FOOT
        else -> DistanceUnit.METER
    }
    return this.metersIn(unit)
}

fun Float.toString(unit: DistanceUnit): String {
    return if (unit in setOf(DistanceUnit.METER, DistanceUnit.FOOT)) {
        "%.0f %s"
    } else {
        "%.1f %s"
    }.format(this, unit.symbol)
}

fun Float.toString(system: DisplayConfig.DisplayUnits): String {
    val unit = when (system.number) {
        DisplayConfig.DisplayUnits.IMPERIAL_VALUE -> DistanceUnit.FOOT
        else -> DistanceUnit.METER
    }
    return this.toString(unit)
}

private const val KILOMETER_THRESHOLD = 1000
private const val MILE_THRESHOLD = 1609
fun Int.toDistanceString(system: DisplayConfig.DisplayUnits): String {
    val unit = if (system.number == DisplayConfig.DisplayUnits.METRIC_VALUE) {
        if (this < KILOMETER_THRESHOLD) DistanceUnit.METER else DistanceUnit.KILOMETER
    } else {
        if (this < MILE_THRESHOLD) DistanceUnit.FOOT else DistanceUnit.MILE
    }
    val valueInUnit = this * unit.multiplier
    return valueInUnit.toString(unit)
}
