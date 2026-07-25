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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
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
    HAZARDOUS(AqiSeverityColors.Hazardous, Res.string.aqi_hazardous, 301..Int.MAX_VALUE),
    ;

    /** The category color for the active theme, legible as body text on `surface` and `surfaceVariant` in both. */
    @Composable fun color(): Color = if (isSystemInDarkTheme()) tones.dark else tones.light

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
