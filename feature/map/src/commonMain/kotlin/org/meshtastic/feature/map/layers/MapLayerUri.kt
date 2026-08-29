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
package org.meshtastic.feature.map.layers

import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLPath
import okio.Path
import okio.Path.Companion.toPath

/**
 * The characters Android's `Uri.fromFile` leaves unescaped in a path: `Uri.encode` treats alphanumerics and `_-!.~'()*`
 * as safe, and the path encoder adds `/`.
 */
private const val ANDROID_PATH_SAFE = "_-!.~'()*/"

/** `%` plus the two hex digits behind it. */
private const val ESCAPE_LENGTH = 3

/** Two hex digits, zero-padded, as a percent escape carries. */
private const val HEX_DIGITS = 2

/** Hexadecimal. */
private const val HEX_RADIX = 16

/**
 * The `file://` URI for [this], byte-identical to what Android's `Uri.fromFile` writes.
 *
 * Identical on purpose rather than merely valid: a layer's URI string is the key its hidden/shown state persists under,
 * so an installation upgrading onto this common store has to produce the same key for the same file, or every layer the
 * user had hidden comes back visible.
 *
 * Ktor's encoder does the work — it is the URL type this module already depends on, and it handles the UTF-8 and
 * non-ASCII cases correctly. It is not quite Android's set, though: Ktor keeps the RFC 3986 sub-delimiters that are
 * legal in a path — comma, ampersand, plus and friends — and Android escapes them. So its output is walked once more
 * and those are escaped too, which is a much smaller thing to get right than the whole encoding.
 */
internal fun Path.toFileUri(): String = "file://" + toString().encodeURLPath().escapeSubDelimiters()

/**
 * The local path behind a `file://` URI (or a bare path, which is what a hand-made item may carry). Public because the
 * renderer's conversion cache also stores its files under `file://` URIs and reads them back through this.
 */
fun String.toLocalPath(): Path = removePrefix("file://").decodeURLPart().toPath()

/**
 * Escape what Ktor left and Android would not have.
 *
 * Ktor has already escaped every non-ASCII byte, so anything still unescaped here is a single ASCII character and can
 * be tested directly. Existing `%XX` escapes are copied through untouched — re-escaping their `%` would double-encode
 * the path.
 */
private fun String.escapeSubDelimiters(): String = buildString {
    var index = 0
    while (index < this@escapeSubDelimiters.length) {
        val char = this@escapeSubDelimiters[index]
        when {
            char == '%' && index + ESCAPE_LENGTH <= this@escapeSubDelimiters.length -> {
                append(this@escapeSubDelimiters, index, index + ESCAPE_LENGTH)
                index += ESCAPE_LENGTH
            }

            char.isSafeInAndroidPath() -> {
                append(char)
                index++
            }

            else -> {
                append('%').append(char.code.toString(HEX_RADIX).uppercase().padStart(HEX_DIGITS, '0'))
                index++
            }
        }
    }
}

private fun Char.isSafeInAndroidPath(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in ANDROID_PATH_SAFE
