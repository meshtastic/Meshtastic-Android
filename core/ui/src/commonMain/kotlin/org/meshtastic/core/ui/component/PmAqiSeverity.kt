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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.aqi_good
import org.meshtastic.core.resources.aqi_hazardous
import org.meshtastic.core.resources.aqi_moderate
import org.meshtastic.core.resources.aqi_unhealthy
import org.meshtastic.core.resources.aqi_unhealthy_sensitive
import org.meshtastic.core.resources.aqi_very_unhealthy
import org.meshtastic.core.ui.theme.AqiSeverityColors

/**
 * EPA AQI severity categories for PM2.5-derived AQI (0-500), per meshtastic/design#54. Mirrors [Co2Severity]'s
 * ppm→severity pattern, keyed on AQI value instead.
 *
 * The category name is a string resource (not a hardcoded literal like [Co2Severity]'s) because it is rendered next to
 * the AQI value everywhere the category is shown — the category must never be conveyed by [color] alone.
 */
@Stable
@Suppress("MagicNumber")
enum class PmAqiSeverity(
    @Stable val tones: AqiSeverityColors.Tones,
    @Stable val labelRes: StringResource,
    val range: IntRange,
) {
    GOOD(AqiSeverityColors.Good, Res.string.aqi_good, 0..50),
    MODERATE(AqiSeverityColors.Moderate, Res.string.aqi_moderate, 51..100),
    UNHEALTHY_SENSITIVE(AqiSeverityColors.UnhealthySensitive, Res.string.aqi_unhealthy_sensitive, 101..150),
    UNHEALTHY(AqiSeverityColors.Unhealthy, Res.string.aqi_unhealthy, 151..200),
    VERY_UNHEALTHY(AqiSeverityColors.VeryUnhealthy, Res.string.aqi_very_unhealthy, 201..300),

    /**
     * EPA ends the scale at 500; the range saturates instead of stopping there so an out-of-scale value still reads as
     * the worst category rather than losing its label. `aqiFromPm25` already clamps to 500, so this is defensive only.
     */
    HAZARDOUS(AqiSeverityColors.Hazardous, Res.string.aqi_hazardous, 301..Int.MAX_VALUE),
    ;

    /** The category color for [darkTheme], legible as body text on `surface` and `surfaceVariant` in both. */
    fun colorFor(darkTheme: Boolean): Color = if (darkTheme) tones.dark else tones.light

    /**
     * The category color for the *applied* theme. Resolved from the surface actually in use rather than
     * `isSystemInDarkTheme()`, so a user who forces light while the system is dark (or vice versa — `AppTheme` takes an
     * explicit `darkTheme`) still gets the tone that was contrast-checked against the surface they are looking at.
     */
    @Composable fun color(): Color = colorFor(MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE)

    companion object {
        /**
         * Midpoint luminance separating a light surface from a dark one. Well clear of both static schemes and of any
         * plausible dynamic-color surface, which stay near the extremes.
         */
        private const val DARK_SURFACE_LUMINANCE = 0.5f

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
