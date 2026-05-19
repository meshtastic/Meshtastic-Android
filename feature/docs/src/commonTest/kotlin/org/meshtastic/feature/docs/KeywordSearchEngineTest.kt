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
package org.meshtastic.feature.docs

import kotlinx.coroutines.test.runTest
import org.meshtastic.feature.docs.data.DefaultDocBundleLoader
import org.meshtastic.feature.docs.data.KeywordSearchEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeywordSearchEngineTest {

    private val loader = DefaultDocBundleLoader()
    private val engine = KeywordSearchEngine(loader)

    @Test
    fun `search for bluetooth returns connections page`() = runTest {
        val results = engine.search("bluetooth")
        assertTrue(results.isNotEmpty())
        assertEquals("connections", results.first().page.id)
    }

    @Test
    fun `search for messages returns messages-and-channels page`() = runTest {
        val results = engine.search("messages channels")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.page.id == "messages-and-channels" })
    }

    @Test
    fun `empty query returns no results`() = runTest {
        val results = engine.search("")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `stop words are filtered`() {
        val query = engine.normalize("how do I connect the bluetooth")
        assertTrue("how" !in query.normalizedTerms)
        assertTrue("do" !in query.normalizedTerms)
        assertTrue("the" !in query.normalizedTerms)
        assertTrue("connect" in query.normalizedTerms)
        assertTrue("bluetooth" in query.normalizedTerms)
    }

    @Test
    fun `title matches score higher than keyword matches`() = runTest {
        val results = engine.search("firmware")
        assertTrue(results.isNotEmpty())
        // "Firmware Updates" page has "firmware" directly in title — should rank first
        assertEquals("firmware", results.first().page.id)
        assertTrue(results.first().score >= KeywordSearchEngine.TITLE_MATCH_SCORE)
    }

    @Test
    fun `alias matches produce results`() = runTest {
        val results = engine.search("direct-messages")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.page.id == "messages-and-channels" })
    }

    @Test
    fun `token budget selection limits pages`() = runTest {
        // Budget large enough for some but not all matching pages
        val pages = engine.selectForTokenBudget("settings module", maxChars = 15000)
        assertTrue(pages.isNotEmpty())
        assertTrue(pages.sumOf { it.charCount } <= 15000)
    }

    @Test
    fun `results are sorted by score descending`() = runTest {
        val results = engine.search("settings module")
        if (results.size >= 2) {
            for (i in 0 until results.size - 1) {
                assertTrue(results[i].score >= results[i + 1].score)
            }
        }
    }

    @Test
    fun `search is case insensitive`() = runTest {
        val lower = engine.search("mqtt")
        val upper = engine.search("MQTT")
        assertEquals(lower.map { it.page.id }, upper.map { it.page.id })
    }
}
