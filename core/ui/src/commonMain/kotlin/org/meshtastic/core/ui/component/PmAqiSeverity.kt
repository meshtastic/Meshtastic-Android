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
package org.meshtastic.core.ui.component

import androidx.compose.ui.graphics.Color

/**
 * EPA AQI severity categories for PM2.5-derived AQI (0-500), per meshtastic/design#54. Mirrors [Co2Severity]'s
 * ppm→severity pattern, keyed on AQI value instead.
 */
@Suppress("MagicNumber")
enum class PmAqiSeverity(val color: Color, val label: String, val range: IntRange) {
    GOOD(Color(0xFF00E400), "Good", 0..50),
    MODERATE(Color(0xFFFFFF00), "Moderate", 51..100),
    UNHEALTHY_SENSITIVE(Color(0xFFFF7E00), "Unhealthy for Sensitive Groups", 101..150),
    UNHEALTHY(Color(0xFFFF0000), "Unhealthy", 151..200),
    VERY_UNHEALTHY(Color(0xFF8F3F97), "Very Unhealthy", 201..300),
    HAZARDOUS(Color(0xFF7E0023), "Hazardous", 301..Int.MAX_VALUE),
    ;

    companion object {
        /** Returns the [PmAqiSeverity] for the given 0-500 EPA [aqi] value, or null if negative. */
        fun fromAqi(aqi: Int): PmAqiSeverity? = when {
            aqi < 0 -> null
            aqi <= GOOD.range.last -> GOOD
            aqi <= MODERATE.range.last -> MODERATE
            aqi <= UNHEALTHY_SENSITIVE.range.last -> UNHEALTHY_SENSITIVE
            aqi <= UNHEALTHY.range.last -> UNHEALTHY
            aqi <= VERY_UNHEALTHY.range.last -> VERY_UNHEALTHY
            else -> HAZARDOUS
        }
    }
}
