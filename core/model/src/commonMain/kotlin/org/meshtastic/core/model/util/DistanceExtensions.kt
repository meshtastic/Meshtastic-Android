/*
 * Copyright (c) 2026 Meshtastic LLC
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

import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.common.util.getSystemMeasurementSystem
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits

@Suppress("MagicNumber")
enum class DistanceUnit(val symbol: String, val multiplier: Float, val system: Int) {
    METER("m", multiplier = 1F, DisplayUnits.METRIC.value),
    KILOMETER("km", multiplier = 0.001F, DisplayUnits.METRIC.value),
    FOOT("ft", multiplier = 3.28084F, DisplayUnits.IMPERIAL.value),
    MILE("mi", multiplier = 0.000621371F, DisplayUnits.IMPERIAL.value),
    ;

    companion object {
        fun getFromLocale(): DisplayUnits = when (getSystemMeasurementSystem()) {
            MeasurementSystem.METRIC -> DisplayUnits.METRIC
            MeasurementSystem.IMPERIAL -> DisplayUnits.IMPERIAL
        }
    }
}

fun Int.metersIn(unit: DistanceUnit): Float = this * unit.multiplier

fun Int.metersIn(system: DisplayUnits): Float {
    val unit =
        when (system.value) {
            DisplayUnits.IMPERIAL.value -> DistanceUnit.FOOT
            else -> DistanceUnit.METER
        }
    return this.metersIn(unit)
}

fun Float.toString(unit: DistanceUnit): String {
    val pattern =
        if (unit in setOf(DistanceUnit.METER, DistanceUnit.FOOT)) {
            "%.0f %s"
        } else {
            "%.1f %s"
        }
    return formatString(pattern, this, unit.symbol)
}

fun Float.toString(system: DisplayUnits): String {
    val unit =
        when (system.value) {
            DisplayUnits.IMPERIAL.value -> DistanceUnit.FOOT
            else -> DistanceUnit.METER
        }
    return this.toString(unit)
}

private const val KILOMETER_THRESHOLD = 1000
private const val MILE_THRESHOLD = 1609

fun Int.toDistanceString(system: DisplayUnits): String {
    val unit =
        if (system.value == DisplayUnits.METRIC.value) {
            if (this < KILOMETER_THRESHOLD) DistanceUnit.METER else DistanceUnit.KILOMETER
        } else {
            if (this < MILE_THRESHOLD) DistanceUnit.FOOT else DistanceUnit.MILE
        }
    val valueInUnit = this * unit.multiplier
    return valueInUnit.toString(unit)
}

@Suppress("MagicNumber")
fun Float.toSpeedString(system: DisplayUnits): String = if (system == DisplayUnits.METRIC) {
    formatString("%.0f km/h", this * 3.6)
} else {
    formatString("%.0f mph", this * 2.23694f)
}

@Suppress("MagicNumber")
fun Float.toSmallDistanceString(system: DisplayUnits): String = if (system == DisplayUnits.IMPERIAL) {
    formatString("%.2f in", this / 25.4f)
} else {
    formatString("%.0f mm", this)
}
