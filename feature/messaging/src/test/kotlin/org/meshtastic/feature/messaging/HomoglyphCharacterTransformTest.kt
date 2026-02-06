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

import org.junit.Test

class HomoglyphCharacterTransformTest {

    @Test
    fun `optimizeUtf8StringWithHomoglyphs shrinks cyrillic text message binary size`() {
        val testString = "Мештастик - это проект с открытым исходным кодом"
        val optimizedTestString = HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs(testString)

        val testStringBytes = testString.toByteArray(charset = Charsets.UTF_8)
        val optimizedTestStringBytes = optimizedTestString.toByteArray(charset = Charsets.UTF_8)

        val optimizedStringBinarySizeShrinked = optimizedTestStringBytes.size < testStringBytes.size
        assert(optimizedStringBinarySizeShrinked)
    }
}
