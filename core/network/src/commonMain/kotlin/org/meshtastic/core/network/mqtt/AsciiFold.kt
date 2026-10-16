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
package org.meshtastic.core.network.mqtt

/** Canonical decomposition (NFD), so a combining mark can be dropped from the letter it sits on. */
internal expect fun decomposeCanonical(text: String): String

/** Latin letters with no decomposition, folded the way ICU's Latin-ASCII transform folds them. */
private val LATIN_LIGATURES =
    mapOf(
        'ß' to "ss",
        'æ' to "ae",
        'Æ' to "AE",
        'œ' to "oe",
        'Œ' to "OE",
        'ø' to "o",
        'Ø' to "O",
        'ł' to "l",
        'Ł' to "L",
        'đ' to "d",
        'Đ' to "D",
        'ð' to "d",
        'Ð' to "D",
        'þ' to "th",
        'Þ' to "TH",
        'ı' to "i",
    )

/** Diacritics stripped and Latin letters folded to ASCII; anything else passes through for the caller to filter. */
internal fun foldToAscii(text: String): String {
    val decomposed = decomposeCanonical(text)
    return buildString {
        for (char in decomposed) {
            when {
                char.category == CharCategory.NON_SPACING_MARK -> Unit
                else -> append(LATIN_LIGATURES[char] ?: char.toString())
            }
        }
    }
}
