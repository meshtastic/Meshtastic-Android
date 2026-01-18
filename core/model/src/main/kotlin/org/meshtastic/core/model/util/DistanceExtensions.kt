/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
@file:Suppress("MatchingDeclarationName")

package org.meshtastic.core.model.util

import android.icu.util.LocaleData
import android.icu.util.ULocale
import org.meshtastic.proto.Config
import java.util.Locale

enum class DistanceUnit(val symbol: String, val multiplier: Float, val system: Int) {
    METER("m", multiplier = 1F, Config.DisplayConfig.DisplayUnits.METRIC.value),
    KILOMETER("km", multiplier = 0.001F, Config.DisplayConfig.DisplayUnits.METRIC.value),
    FOOT("ft", multiplier = 3.28084F, Config.DisplayConfig.DisplayUnits.IMPERIAL.value),
    MILE("mi", multiplier = 0.000621371F, Config.DisplayConfig.DisplayUnits.IMPERIAL.value),
    ;

    companion object {
        fun getFromLocale(locale: Locale = Locale.getDefault()): Config.DisplayConfig.DisplayUnits =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                when (LocaleData.getMeasurementSystem(ULocale.forLocale(locale))) {
                    LocaleData.MeasurementSystem.SI -> Config.DisplayConfig.DisplayUnits.METRIC
                    else -> Config.DisplayConfig.DisplayUnits.IMPERIAL
                }
            } else {
                when (locale.country.uppercase(locale)) {
                    "US",
                    "LR",
                    "MM",
                    "GB",
                    -> Config.DisplayConfig.DisplayUnits.IMPERIAL
                    else -> Config.DisplayConfig.DisplayUnits.METRIC
                }
            }
    }
}

fun Int.metersIn(unit: DistanceUnit): Float = this * unit.multiplier

fun Int.metersIn(system: Config.DisplayConfig.DisplayUnits): Float {
    val unit =
        when (system.value) {
            Config.DisplayConfig.DisplayUnits.IMPERIAL.value -> DistanceUnit.FOOT
            else -> DistanceUnit.METER
        }
    return this.metersIn(unit)
}

fun Float.toString(unit: DistanceUnit): String = if (unit in setOf(DistanceUnit.METER, DistanceUnit.FOOT)) {
    "%.0f %s"
} else {
    "%.1f %s"
}
    .format(this, unit.symbol)

fun Float.toString(system: Config.DisplayConfig.DisplayUnits): String {
    val unit =
        when (system.value) {
            Config.DisplayConfig.DisplayUnits.IMPERIAL.value -> DistanceUnit.FOOT
            else -> DistanceUnit.METER
        }
    return this.toString(unit)
}

private const val KILOMETER_THRESHOLD = 1000
private const val MILE_THRESHOLD = 1609

fun Int.toDistanceString(system: Config.DisplayConfig.DisplayUnits): String {
    val unit =
        if (system.value == Config.DisplayConfig.DisplayUnits.METRIC.value) {
            if (this < KILOMETER_THRESHOLD) DistanceUnit.METER else DistanceUnit.KILOMETER
        } else {
            if (this < MILE_THRESHOLD) DistanceUnit.FOOT else DistanceUnit.MILE
        }
    val valueInUnit = this * unit.multiplier
    return valueInUnit.toString(unit)
}

@Suppress("MagicNumber")
fun Float.toSpeedString(system: Config.DisplayConfig.DisplayUnits): String =
    if (system == Config.DisplayConfig.DisplayUnits.METRIC) {
        "%.0f km/h".format(this * 3.6)
    } else {
        "%.0f mph".format(this * 2.23694f)
    }

@Suppress("MagicNumber")
fun Float.toSmallDistanceString(system: Config.DisplayConfig.DisplayUnits): String =
    if (system == Config.DisplayConfig.DisplayUnits.IMPERIAL) {
        "%.2f in".format(this / 25.4f)
    } else {
        "%.0f mm".format(this)
    }
