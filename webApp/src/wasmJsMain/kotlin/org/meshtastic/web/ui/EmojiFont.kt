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
package org.meshtastic.web.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import co.touchlab.kermit.Logger
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.fetch.Response

/**
 * Compose draws text with Skia and never sees the browser's fonts, so emoji need a font of their own: a preloaded
 * family is the fallback for glyphs the theme's typeface lacks. It loads after the first frame, and the service worker
 * keeps it with the app shell.
 */
@Composable
internal fun PreloadEmojiFont() {
    val resolver = LocalFontFamilyResolver.current
    LaunchedEffect(resolver) {
        try {
            val response = window.fetch(EMOJI_FONT).await<Response>()
            check(response.ok) { "HTTP ${response.status}" }
            val bytes = Int8Array(response.arrayBuffer().await<ArrayBuffer>()).toByteArray()
            resolver.preload(FontFamily(Font(EMOJI_FONT, bytes)))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Emoji font did not load; emoji draw as boxes" }
        }
    }
}

// Noto Color Emoji's COLRv1 build from googlefonts/noto-emoji, under the SIL Open Font License beside it.
private const val EMOJI_FONT = "NotoColorEmoji.ttf"
