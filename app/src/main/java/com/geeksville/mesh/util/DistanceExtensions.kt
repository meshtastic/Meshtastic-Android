package com.geeksville.mesh.util

import com.geeksville.mesh.ConfigProtos

enum class DistanceUnit(
    val symbol: String,
    val multiplier: Float,
    val system: Int
) {
    METERS("m", 1F, ConfigProtos.Config.DisplayConfig.DisplayUnits.METRIC_VALUE),
    KILOMETERS("km", 0.001F, ConfigProtos.Config.DisplayConfig.DisplayUnits.METRIC_VALUE),
    FEET("ft", 3.28084F, ConfigProtos.Config.DisplayConfig.DisplayUnits.IMPERIAL_VALUE),
    MILES("mi", 0.000621371F, ConfigProtos.Config.DisplayConfig.DisplayUnits.IMPERIAL_VALUE),
}

fun Int.metersIn(unit: DistanceUnit): Float {
    return this * unit.multiplier
}

fun Int.metersIn(system: ConfigProtos.Config.DisplayConfig.DisplayUnits): Float {
    return this * when (system.number) {
        ConfigProtos.Config.DisplayConfig.DisplayUnits.METRIC_VALUE -> DistanceUnit.METERS.multiplier
        ConfigProtos.Config.DisplayConfig.DisplayUnits.IMPERIAL_VALUE -> DistanceUnit.FEET.multiplier
        else -> throw IllegalArgumentException("Unknown distance system $system")
    }
}

fun Float.toString(unit: DistanceUnit): String {
    return "%.1f %s".format(this, unit.symbol)
}

fun Float.toString(
    system: ConfigProtos.Config.DisplayConfig.DisplayUnits
): String {
    return "%.1f %s".format(this,
        when (system.number) {
            ConfigProtos.Config.DisplayConfig.DisplayUnits.METRIC_VALUE -> {
                DistanceUnit.METERS.symbol
            }
            ConfigProtos.Config.DisplayConfig.DisplayUnits.IMPERIAL_VALUE -> {
                DistanceUnit.FEET.symbol
            }
            else -> throw IllegalArgumentException("Unknown distance system $system")
        },
    )
}