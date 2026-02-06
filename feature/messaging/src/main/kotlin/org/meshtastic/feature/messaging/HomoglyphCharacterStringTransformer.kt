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
package org.meshtastic.feature.messaging

/**
 * This util class allows you to optimize the binary size of the transmitted text message strings. It replaces certain
 * characters from national alphabets with the characters from the latin alphabet that have an identical appearance
 * (homoglyphs)
 *
 * In UTF8 unicode the characters of the latin alphabet occupy only 1 byte each. In national alphabets each character
 * occupy at least 2 bytes. This reduces the maximum transmitted message size by almost half, to about 115-120
 * characters (200/2 = 100 + some characters are spaces and special signs that occupy only 1 byte)
 *
 * For instance, in cyrillic alphabets many characters have exactly the same visual appearance as latin characters.
 * These are fairly common symbols - half of the vowels and frequently used consonants. According to statistics, they
 * contain about 20-25% of all letters in the average text. Replacing them with Latin characters will save the same
 * proportion of available message space. The average transmitted message volume can then be increased from ~115-120
 * characters to ~140-145
 */
internal object HomoglyphCharacterStringTransformer {

    /**
     * Unicode characters from the basic cyrillic block (U+0400-U+04FF), each of which occupies 2 bytes
     * https://www.compart.com/en/unicode/block/U+0400 Mapped with the corresponding similarly written latin characters,
     * each of which occupies 1 byte
     *
     * Please note that only 100% "reliable", completely visually identical characters are presented will here The
     * characters that look like latin but contain various descenders, hooks, strokes, etc are not replaced with
     * "simplified" latin appearance and will remain 2 byte unicode, as usual
     */
    private val homoglyphCharactersSubstitutionMapping: Map<Char, Char> =
        mapOf(
            '\u0405' to 'S', // https://www.compart.com/en/unicode/U+0405 - Cyrillic Capital Letter Dze
            '\u0406' to
                'I', // https://www.compart.com/en/unicode/U+0406 - Cyrillic Capital Letter Byelorussian-Ukrainian I
            '\u0408' to 'J', // https://www.compart.com/en/unicode/U+0408 - Cyrillic Capital Letter Je
            '\u0410' to 'A', // https://www.compart.com/en/unicode/U+0410 - Cyrillic Capital Letter A
            '\u0412' to 'B', // https://www.compart.com/en/unicode/U+0412 - Cyrillic Capital Letter Ve
            '\u0415' to 'E', // https://www.compart.com/en/unicode/U+0415 - Cyrillic Capital Letter Ie
            '\u0417' to '3', // https://www.compart.com/en/unicode/U+0417 - Cyrillic Capital Letter Ze
            '\u041A' to 'K', // https://www.compart.com/en/unicode/U+041A - Cyrillic Capital Letter Ka
            '\u041C' to 'M', // https://www.compart.com/en/unicode/U+041C - Cyrillic Capital Letter Em
            '\u041D' to 'H', // https://www.compart.com/en/unicode/U+041D - Cyrillic Capital Letter En
            '\u041E' to 'O', // https://www.compart.com/en/unicode/U+041E - Cyrillic Capital Letter O
            '\u0420' to 'P', // https://www.compart.com/en/unicode/U+0420 - Cyrillic Capital Letter Er
            '\u0421' to 'C', // https://www.compart.com/en/unicode/U+0421 - Cyrillic Capital Letter Es
            '\u0422' to 'T', // https://www.compart.com/en/unicode/U+0422 - Cyrillic Capital Letter Te
            '\u0425' to 'X', // https://www.compart.com/en/unicode/U+0425 - Cyrillic Capital Letter Ha
            '\u0430' to 'a', // https://www.compart.com/en/unicode/U+0430 - Cyrillic Small Letter A
            '\u0435' to 'e', // https://www.compart.com/en/unicode/U+0435 - Cyrillic Small Letter Ie
            '\u043E' to 'o', // https://www.compart.com/en/unicode/U+043E - Cyrillic Small Letter O
            '\u0440' to 'p', // https://www.compart.com/en/unicode/U+0440 - Cyrillic Small Letter Er
            '\u0441' to 'c', // https://www.compart.com/en/unicode/U+0441 - Cyrillic Small Letter Es
            '\u0443' to 'y', // https://www.compart.com/en/unicode/U+0443 - Cyrillic Small Letter U
            '\u0445' to 'x', // https://www.compart.com/en/unicode/U+0445 - Cyrillic Small Letter Ha
            '\u0455' to 's', // https://www.compart.com/en/unicode/U+0455 - Cyrillic Small Letter Dze
            '\u0456' to
                'i', // https://www.compart.com/en/unicode/U+0456 - Cyrillic Small Letter Byelorussian-Ukrainian I
            '\u0458' to 'j', // https://www.compart.com/en/unicode/U+0458 - Cyrillic Small Letter Je
            '\u04AE' to 'Y', // https://www.compart.com/en/unicode/U+04AE - Cyrillic Capital Letter Straight U
        )

    /**
     * Returns the transformed optimized [String] value, in which some characters of the national alphabets are replaced
     * with identical Latin characters so that the text takes up fewer bytes and is more compact for transmission.
     *
     * @param value original string value.
     * @return optimized string value.
     */
    fun optimizeUtf8StringWithHomoglyphs(value: String): String {
        val stringBuilder = StringBuilder()
        for (c in value.toCharArray()) stringBuilder.append(homoglyphCharactersSubstitutionMapping.getOrDefault(c, c))
        return stringBuilder.toString()
    }
}
