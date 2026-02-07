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
    fun `optimizeUtf8StringWithHomoglyphs shrinks binary size of cyrillic text containing some homoglyphs`() {
        val testString = "Мештастик - это проект с открытым исходным кодом"
        val transformedTestString = HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs(testString)
        val testStringBytes = testString.toByteArray(charset = Charsets.UTF_8)
        val transformedTestStringBytes = transformedTestString.toByteArray(charset = Charsets.UTF_8)
        val transformedStringBinarySizeShrinked = transformedTestStringBytes.size < testStringBytes.size
        assert(transformedStringBinarySizeShrinked)
    }

    @Test
    fun `optimizeUtf8StringWithHomoglyphs shrinks binary size in half of cyrillic text containing only homoglyphs`() {
        val testString = "Косуха"
        val transformedTestString = HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs(testString)
        val testStringBytes = testString.toByteArray(charset = Charsets.UTF_8)
        val transformedTestStringBytes = transformedTestString.toByteArray(charset = Charsets.UTF_8)
        val transformedStringBinarySizeShrinksInHalf = transformedTestStringBytes.size == (testStringBytes.size / 2)
        assert(transformedStringBinarySizeShrinksInHalf)
    }

    @Test
    fun `optimizeUtf8StringWithHomoglyphs does not transform cyrillic text without any homoglyphs`() {
        val testString = "Близкий"
        val transformedTestString = HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs(testString)
        val stringObjectsContentsAreEqual = transformedTestString == testString
        assert(stringObjectsContentsAreEqual)
    }

    @Test
    fun `optimizeUtf8StringWithHomoglyphs does not transform latin text message`() {
        val testString = "Meshtastic is an open source, off-grid, decentralized mesh network"
        val transformedTestString = HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs(testString)
        val stringObjectsContentsAreEqual = transformedTestString == testString
        assert(stringObjectsContentsAreEqual)
    }

    @Test
    fun `optimizeUtf8StringWithHomoglyphs does not transform characters impossible to present by latin letters`() {
        val testString = "ميشتاستيك هو مصدر مفتوح ، خارج الشبكة ، شبكة شبكة"
        val transformedTestString = HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs(testString)
        val stringObjectsContentsAreEqual = transformedTestString == testString
        assert(stringObjectsContentsAreEqual)
    }
}
