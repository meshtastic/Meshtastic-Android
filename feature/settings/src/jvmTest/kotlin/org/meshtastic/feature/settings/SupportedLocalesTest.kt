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
package org.meshtastic.feature.settings

import org.meshtastic.core.resources.supportedLocaleTags
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SupportedLocalesTest {

    @Test
    fun everyTagParsesToALanguage() {
        supportedLocaleTags.forEach { tag ->
            val locale = Locale.forLanguageTag(tag)
            assertTrue(locale.language.isNotEmpty(), "malformed values directory for tag $tag")
            assertEquals(tag, locale.toLanguageTag(), "tag $tag does not round-trip")
        }
    }

    @Test
    fun includesDefaultAndQualifiedLocales() {
        listOf("en", "be", "he", "sr", "sr-Latn", "pt-BR", "zh-CN").forEach { tag ->
            assertTrue(tag in supportedLocaleTags, "missing $tag")
        }
    }

    @Test
    fun tagsAreSortedAndUnique() {
        assertEquals(supportedLocaleTags.sorted().distinct(), supportedLocaleTags)
    }

    @Test
    fun serbianScriptsGetDistinctNames() {
        assertNotEquals(nativeLanguageName("sr"), nativeLanguageName("sr-Latn"))
    }
}
