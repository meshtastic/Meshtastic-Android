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
package org.meshtastic.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import org.meshtastic.core.model.EventFirmwareFonts

/**
 * Resolved heading/body typefaces for an active event edition. Either component may be `null` (fall back to default).
 */
data class EventFonts(val heading: FontFamily? = null, val body: FontFamily? = null)

/**
 * Resolves an edition's [EventFirmwareFonts] (Google Font *family names*, e.g. `Lato`) into loadable [FontFamily]s.
 *
 * The default binding returns `null` (no custom fonts). The **Google** flavor binds a downloadable-fonts
 * implementation; **F-Droid** keeps the null default (no Play font provider). Resolved via Koin and applied at the app
 * theme via [LocalEventTypographyFonts].
 */
fun interface EventFontResolver {
    fun resolve(fonts: EventFirmwareFonts?): EventFonts?
}

/**
 * The event typeface to apply app-wide, or `null` for the default typography. Populated at the composition root only
 * when a device is on event firmware, the fonts resolved, and the user hasn't opted out via [LocalEventFontsToggle].
 * [AppTheme] reads this to swap [AppTypography].
 */
@Suppress("CompositionLocalAllowlist")
val LocalEventTypographyFonts = compositionLocalOf<EventFonts?> { null }

/** Sheet-level opt-out for event fonts. [available] gates whether the toggle is shown at all (false on F-Droid). */
data class EventFontsToggle(
    val available: Boolean = false,
    val enabled: Boolean = true,
    val onChange: (Boolean) -> Unit = {},
)

@Suppress("CompositionLocalAllowlist")
val LocalEventFontsToggle = compositionLocalOf { EventFontsToggle() }

private fun TextStyle.family(family: FontFamily?): TextStyle = if (family != null) copy(fontFamily = family) else this

/**
 * Applies [fonts] across the full M3 typescale: [EventFonts.heading] to display/headline/title styles,
 * [EventFonts.body] to body/label styles. A `null` component leaves those styles unchanged.
 */
fun Typography.withEventFonts(fonts: EventFonts): Typography {
    val h = fonts.heading
    val b = fonts.body
    return copy(
        displayLarge = displayLarge.family(h),
        displayMedium = displayMedium.family(h),
        displaySmall = displaySmall.family(h),
        headlineLarge = headlineLarge.family(h),
        headlineMedium = headlineMedium.family(h),
        headlineSmall = headlineSmall.family(h),
        titleLarge = titleLarge.family(h),
        titleMedium = titleMedium.family(h),
        titleSmall = titleSmall.family(h),
        bodyLarge = bodyLarge.family(b),
        bodyMedium = bodyMedium.family(b),
        bodySmall = bodySmall.family(b),
        labelLarge = labelLarge.family(b),
        labelMedium = labelMedium.family(b),
        labelSmall = labelSmall.family(b),
    )
}
