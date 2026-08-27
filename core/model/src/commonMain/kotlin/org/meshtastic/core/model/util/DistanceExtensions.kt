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

import org.meshtastic.core.common.util.MeasureUnitKind
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.formatElevationLocalized
import org.meshtastic.core.common.util.formatLengthLocalized
import org.meshtastic.core.common.util.formatMeasure
import org.meshtastic.core.common.util.formatRainfallLocalized
import org.meshtastic.core.common.util.formatSpeedLocalized
import kotlin.math.roundToInt

@Suppress("MagicNumber")
enum class DistanceUnit(val multiplier: Float, val system: MeasurementSystem, val kind: MeasureUnitKind) {
    METER(multiplier = 1F, MeasurementSystem.METRIC, MeasureUnitKind.METER),
    KILOMETER(multiplier = 0.001F, MeasurementSystem.METRIC, MeasureUnitKind.KILOMETER),
    FOOT(multiplier = 3.28084F, MeasurementSystem.IMPERIAL, MeasureUnitKind.FOOT),
    MILE(multiplier = 0.000621371F, MeasurementSystem.IMPERIAL, MeasureUnitKind.MILE),
}

fun Int.metersIn(unit: DistanceUnit): Float = this * unit.multiplier

fun Int.metersIn(system: MeasurementSystem): Float {
    val unit =
        when (system) {
            MeasurementSystem.IMPERIAL -> DistanceUnit.FOOT
            MeasurementSystem.METRIC -> DistanceUnit.METER
        }
    return this.metersIn(unit)
}

/** Whole units for the small denominations, one decimal for the large ones — a node 1.2 km away, not 1.234 km. */
fun Float.toString(unit: DistanceUnit): String {
    val fractionDigits = if (unit == DistanceUnit.METER || unit == DistanceUnit.FOOT) 0 else 1
    return formatMeasure(this.toDouble(), unit.kind, fractionDigits)
}

fun Float.toString(system: MeasurementSystem): String {
    val unit =
        when (system) {
            MeasurementSystem.IMPERIAL -> DistanceUnit.FOOT
            MeasurementSystem.METRIC -> DistanceUnit.METER
        }
    return this.toString(unit)
}

private const val KILOMETER_THRESHOLD = 1000
private const val MILE_THRESHOLD = 1609

/**
 * Formats a distance in metres for display, choosing the unit from the magnitude.
 *
 * An earlier revision consulted ICU's `usage("road")` so CLDR could pick the unit. It was removed: CLDR's road
 * preferences also impose road rounding, which snaps to the nearest 10 m under 300 m and 50 m above it — so a node 87 m
 * away read "90 m", and the GNSS-accuracy and position-precision labels that share this function are not road distances
 * at all. It also disagreed with the requested system on the locales ICU reports as US but CLDR has no road entry for.
 */
fun Int.toDistanceString(system: MeasurementSystem): String {
    formatLengthLocalized(this.toDouble(), system)?.let {
        return it
    }
    val unit =
        if (system == MeasurementSystem.METRIC) {
            if (this < KILOMETER_THRESHOLD) DistanceUnit.METER else DistanceUnit.KILOMETER
        } else {
            if (this < MILE_THRESHOLD) DistanceUnit.FOOT else DistanceUnit.MILE
        }
    val valueInUnit = this * unit.multiplier
    return valueInUnit.toString(unit)
}

/**
 * Formats an altitude/elevation in metres for display, in the whole metres or feet of [system].
 *
 * Elevation stays in the small unit at any magnitude — 7,431 ft, never 1.4 mi — which is also what CLDR's default
 * length precision renders, so engine and fallback agree.
 */
fun Int.toElevationString(system: MeasurementSystem): String =
    formatElevationLocalized(this.toDouble(), system) ?: this.metersIn(system).toString(system)

@Suppress("MagicNumber")
fun Float.toSpeedString(system: MeasurementSystem): String = formatSpeedLocalized(this.toDouble(), system)
    ?: if (system == MeasurementSystem.METRIC) {
        formatMeasure(this * 3.6, MeasureUnitKind.KILOMETER_PER_HOUR, 0)
    } else {
        formatMeasure(this * 2.23694, MeasureUnitKind.MILE_PER_HOUR, 0)
    }

/**
 * Converts a speed already expressed in km/h (e.g. protobuf `Position.ground_speed`) to [system]'s unit, rounded to a
 * whole number. Callers render the result through a localized string resource so the unit label itself stays
 * translatable (see `speed_kmh`/`speed_mph`).
 */
@Suppress("MagicNumber")
fun Int.kmhIn(system: MeasurementSystem): Int =
    if (system == MeasurementSystem.IMPERIAL) (this * 0.621371f).roundToInt() else this

@Suppress("MagicNumber")
fun Float.toSmallDistanceString(system: MeasurementSystem): String = formatRainfallLocalized(this.toDouble(), system)
    ?: if (system == MeasurementSystem.IMPERIAL) {
        formatMeasure(this / 25.4, MeasureUnitKind.INCH, 2)
    } else {
        formatMeasure(this.toDouble(), MeasureUnitKind.MILLIMETER, 0)
    }
