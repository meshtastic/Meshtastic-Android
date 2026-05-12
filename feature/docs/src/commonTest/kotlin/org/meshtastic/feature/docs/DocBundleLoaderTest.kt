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
import org.meshtastic.feature.docs.model.DocSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DocBundleLoaderTest {

    private val loader = DefaultDocBundleLoader()

    @Test
    fun `load returns non-empty bundle`() = runTest {
        val bundle = loader.load()
        assertTrue(bundle.pages.isNotEmpty())
        assertEquals(bundle.pages.size, bundle.pageIndex.size)
    }

    @Test
    fun `page index keys match page ids`() = runTest {
        val bundle = loader.load()
        assertEquals(bundle.pages.map { it.id }.toSet(), bundle.pageIndex.keys)
    }

    @Test
    fun `pages by section returns correct grouping`() = runTest {
        loader.load()
        val userPages = loader.pagesBySection(DocSection.UserGuide)
        val devPages = loader.pagesBySection(DocSection.DeveloperGuide)

        assertTrue(userPages.isNotEmpty())
        assertTrue(devPages.isNotEmpty())
        assertTrue(userPages.all { it.section == DocSection.UserGuide })
        assertTrue(devPages.all { it.section == DocSection.DeveloperGuide })
    }

    @Test
    fun `pages by section are sorted by navOrder`() = runTest {
        loader.load()
        val userPages = loader.pagesBySection(DocSection.UserGuide)
        val navOrders = userPages.map { it.navOrder }
        assertEquals(navOrders.sorted(), navOrders)
    }

    @Test
    fun `read page returns content for valid page id`() = runTest {
        val content = loader.readPage("onboarding")
        assertNotNull(content)
        assertEquals("onboarding", content.page.id)
        assertEquals("Getting Started", content.page.title)
    }

    @Test
    fun `read page returns null for invalid page id`() = runTest {
        val content = loader.readPage("nonexistent-page")
        assertEquals(null, content)
    }

    @Test
    fun `all pages have positive char count`() = runTest {
        val bundle = loader.load()
        assertTrue(bundle.pages.all { it.charCount > 0 })
    }

    @Test
    fun `all pages have non-empty keywords`() = runTest {
        val bundle = loader.load()
        assertTrue(bundle.pages.all { it.keywords.isNotEmpty() })
    }
}
