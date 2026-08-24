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

package org.meshtastic.core.common.util

/** A physical unit this app renders. [symbol] is fixed; the number beside it is localized. */
enum class MeasureUnitKind(val symbol: String) {
    METER("m"),
    KILOMETER("km"),
    FOOT("ft"),
    MILE("mi"),
    MILLIMETER("mm"),
    INCH("in"),
    KILOMETER_PER_HOUR("km/h"),
    MILE_PER_HOUR("mph"),
    METER_PER_SECOND("m/s"),
    KILOGRAM("kg"),
    POUND("lb"),
}

/**
 * Formats [value] with its unit for display: the number follows the OS locale, the symbol does not.
 *
 * ICU's `MeasureFormat` would also translate the symbol and pick the locale's own spacing, and an earlier revision of
 * this used it. It cannot be reached from here: this function is called from `commonTest`, which runs against the
 * Android target's stub `android.jar` where every `android.icu` call throws, and `commonTest` cannot switch to
 * Robolectric because it also compiles for iOS. Symbols were fixed English before this function existed, so nothing
 * regressed — only the translation improvement was given up.
 *
 * NaN and infinity render as the shared placeholder, because telemetry floats use NaN as an in-band "absent" marker.
 */
fun formatMeasure(value: Double, unit: MeasureUnitKind, fractionDigits: Int): String {
    if (value.isNaN() || value.isInfinite()) return NumberFormatter.format(value, fractionDigits)
    return "${formatDecimalLocalized(value, fractionDigits)} ${unit.symbol}"
}
