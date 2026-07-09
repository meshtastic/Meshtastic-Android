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
package org.meshtastic.app.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import org.meshtastic.app.R
import org.meshtastic.core.model.EventFirmwareFonts
import org.meshtastic.core.ui.theme.EventFontResolver
import org.meshtastic.core.ui.theme.EventFonts

/**
 * Google-flavor [EventFontResolver]: turns an edition's Google Font family names into downloadable [FontFamily]s via
 * the Play Services font provider. Unknown/typo names or an unavailable provider fail gracefully — Compose falls back
 * to the default typeface. The cert array is provided transitively by play-services (basement) that the Google flavor
 * already pulls for Maps. F-Droid never binds this (no provider); it keeps the null default resolver.
 */
class GoogleFontsEventFontResolver : EventFontResolver {

    override fun resolve(fonts: EventFirmwareFonts?): EventFonts? {
        if (fonts == null) return null
        val heading = fonts.heading.toFontFamily(FontWeight.Bold)
        val body = fonts.body.toFontFamily(FontWeight.Normal)
        return if (heading == null && body == null) null else EventFonts(heading = heading, body = body)
    }

    private fun String?.toFontFamily(weight: FontWeight): FontFamily? = this?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { FontFamily(Font(googleFont = GoogleFont(it), fontProvider = PROVIDER, weight = weight)) }

    private companion object {
        private val PROVIDER =
            GoogleFont.Provider(
                providerAuthority = "com.google.android.gms.fonts",
                providerPackage = "com.google.android.gms",
                certificates = R.array.com_google_android_gms_fonts_certs,
            )
    }
}
