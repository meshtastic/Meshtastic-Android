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

import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Android and the desktop JVM share this: both resolve separators and grouping from the platform locale data.
 *
 * Rounds half-up rather than the JDK default of half-even, so moving a displayed telemetry value onto locale formatting
 * changes its separators and nothing else.
 */
actual fun formatDecimalLocalized(value: Double, fractionDigits: Int): String =
    NumberFormat.getInstance(Locale.getDefault())
        .apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
            // NumberFormat's own contract lets an implementation reject this; only DecimalFormat, which every locale
            // returns in practice, honors it. Falling back to the platform default beats crashing on an odd locale.
            runCatching { roundingMode = RoundingMode.HALF_UP }
        }
        .format(value)
