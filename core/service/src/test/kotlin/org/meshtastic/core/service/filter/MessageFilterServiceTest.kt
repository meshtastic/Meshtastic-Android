/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.service.filter

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.prefs.filter.FilterPrefs

class MessageFilterServiceTest {
    private lateinit var filterPrefs: FilterPrefs
    private lateinit var filterService: MessageFilterService

    @Before
    fun setup() {
        filterPrefs = mockk {
            every { filterEnabled } returns true
            every { filterWords } returns setOf("spam", "bad")
        }
        filterService = MessageFilterService(filterPrefs)
    }

    @Test
    fun `shouldFilter returns false when filter is disabled`() {
        every { filterPrefs.filterEnabled } returns false
        assertFalse(filterService.shouldFilter("spam message"))
    }

    @Test
    fun `shouldFilter returns false when filter words is empty`() {
        every { filterPrefs.filterWords } returns emptySet()
        filterService.rebuildPatterns()
        assertFalse(filterService.shouldFilter("any message"))
    }

    @Test
    fun `shouldFilter returns true for exact word match`() {
        filterService.rebuildPatterns()
        assertTrue(filterService.shouldFilter("this is spam"))
    }

    @Test
    fun `shouldFilter is case insensitive`() {
        filterService.rebuildPatterns()
        assertTrue(filterService.shouldFilter("This is SPAM"))
    }

    @Test
    fun `shouldFilter matches whole words only`() {
        filterService.rebuildPatterns()
        assertFalse(filterService.shouldFilter("antispam software"))
    }

    @Test
    fun `shouldFilter supports regex patterns`() {
        every { filterPrefs.filterWords } returns setOf("regex:test\\d+")
        filterService.rebuildPatterns()
        assertTrue(filterService.shouldFilter("this is test123"))
        assertFalse(filterService.shouldFilter("this is test"))
    }

    @Test
    fun `shouldFilter handles invalid regex gracefully`() {
        every { filterPrefs.filterWords } returns setOf("regex:[invalid")
        filterService.rebuildPatterns()
        assertFalse(filterService.shouldFilter("any message"))
    }

    @Test
    fun `shouldFilter returns false when contact has filtering disabled`() {
        filterService.rebuildPatterns()
        assertFalse(filterService.shouldFilter("spam message", isFilteringDisabled = true))
    }

    @Test
    fun `shouldFilter filters when contact has filtering enabled`() {
        filterService.rebuildPatterns()
        assertTrue(filterService.shouldFilter("spam message", isFilteringDisabled = false))
    }
}
