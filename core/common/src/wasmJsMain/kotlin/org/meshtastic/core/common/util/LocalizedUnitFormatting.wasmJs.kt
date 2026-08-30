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
package org.meshtastic.core.common.util

import kotlin.math.abs

/**
 * `Intl.NumberFormat`'s `style: 'unit'` is the browser's CLDR-driven measure renderer, the wasmJs analogue of Android's
 * `android.icu.number.NumberFormatter`. It has no `usage()` concept, though: ICU's `usage()` lets the engine itself
 * pick the unit and magnitude-appropriate conversion (e.g. rendering a large metre count in kilometres), so that choice
 * is made by hand here in Kotlin -- the raw metric value is converted to the target unit's raw number before it is ever
 * handed to `Intl.NumberFormat`, same as the JVM/Android actual already does for [formatElevationLocalized] (which
 * likewise bypasses `usage()` for a fixed unit).
 *
 * Every function returns `null` for a non-finite input, matching the platform-neutral contract every other actual of
 * these functions honors.
 */
actual fun formatLengthLocalized(meters: Double, system: MeasurementSystem): String? {
    if (!meters.isFinite()) return null
    val locale = browserLanguage()
    return if (system == MeasurementSystem.METRIC) {
        if (abs(meters) < METERS_PER_KILOMETER) {
            unitFormatter(locale, "meter").format(meters)
        } else {
            unitFormatter(locale, "kilometer").format(meters / METERS_PER_KILOMETER)
        }
    } else {
        val feet = meters * METERS_TO_FEET
        if (abs(feet) < FEET_PER_MILE) {
            unitFormatter(locale, "foot").format(feet)
        } else {
            unitFormatter(locale, "mile").format(feet / FEET_PER_MILE)
        }
    }
}

/**
 * See [formatLengthLocalized]; elevation pins the small unit (metres or feet) at every magnitude, so unlike length
 * there is no kilometre/mile threshold -- matching the JVM/Android actual's `Precision.integer()` fixed-unit behavior
 * via `unitIntegerFormatter`'s forced zero fraction digits.
 */
actual fun formatElevationLocalized(meters: Double, system: MeasurementSystem): String? {
    if (!meters.isFinite()) return null
    val locale = browserLanguage()
    return if (system == MeasurementSystem.METRIC) {
        unitIntegerFormatter(locale, "meter").format(meters)
    } else {
        unitIntegerFormatter(locale, "foot").format(meters * METERS_TO_FEET)
    }
}

/** See [formatLengthLocalized]; input is m/s, rendered as km/h or mph -- one fixed unit each, no threshold. */
actual fun formatSpeedLocalized(metersPerSecond: Double, system: MeasurementSystem): String? {
    if (!metersPerSecond.isFinite()) return null
    val locale = browserLanguage()
    return if (system == MeasurementSystem.METRIC) {
        unitFormatter(locale, "kilometer-per-hour").format(metersPerSecond * METERS_PER_SECOND_TO_KMH)
    } else {
        unitFormatter(locale, "mile-per-hour").format(metersPerSecond * METERS_PER_SECOND_TO_MPH)
    }
}

/** See [formatLengthLocalized]; input is mm, rendered as mm or inches -- one fixed unit each, no threshold. */
actual fun formatRainfallLocalized(millimeters: Double, system: MeasurementSystem): String? {
    if (!millimeters.isFinite()) return null
    val locale = browserLanguage()
    return if (system == MeasurementSystem.METRIC) {
        unitFormatter(locale, "millimeter").format(millimeters)
    } else {
        unitFormatter(locale, "inch").format(millimeters / MILLIMETERS_PER_INCH)
    }
}

private const val METERS_PER_KILOMETER = 1000.0
private const val FEET_PER_MILE = 5280.0
private const val METERS_TO_FEET = 3.28084
private const val METERS_PER_SECOND_TO_KMH = 3.6
private const val METERS_PER_SECOND_TO_MPH = 2.23694
private const val MILLIMETERS_PER_INCH = 25.4
