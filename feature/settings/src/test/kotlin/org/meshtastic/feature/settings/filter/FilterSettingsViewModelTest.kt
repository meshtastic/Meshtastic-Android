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
package org.meshtastic.feature.settings.filter

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.repository.FilterPrefs
import org.meshtastic.core.repository.MessageFilter

class FilterSettingsViewModelTest {

    private val filterPrefs: FilterPrefs = mockk(relaxed = true)
    private val messageFilter: MessageFilter = mockk(relaxed = true)

    private lateinit var viewModel: FilterSettingsViewModel

    @Before
    fun setUp() {
        every { filterPrefs.filterEnabled.value } returns true
        every { filterPrefs.filterWords.value } returns setOf("apple", "banana")

        viewModel = FilterSettingsViewModel(filterPrefs = filterPrefs, messageFilter = messageFilter)
    }

    @Test
    fun `setFilterEnabled updates prefs and state`() {
        viewModel.setFilterEnabled(false)
        verify { filterPrefs.setFilterEnabled(false) }
        assertEquals(false, viewModel.filterEnabled.value)
    }

    @Test
    fun `addFilterWord updates prefs and rebuilds patterns`() {
        viewModel.addFilterWord("cherry")

        verify { filterPrefs.setFilterWords(any()) }
        verify { messageFilter.rebuildPatterns() }
        assertEquals(listOf("apple", "banana", "cherry"), viewModel.filterWords.value)
    }

    @Test
    fun `removeFilterWord updates prefs and rebuilds patterns`() {
        viewModel.removeFilterWord("apple")

        verify { filterPrefs.setFilterWords(any()) }
        verify { messageFilter.rebuildPatterns() }
        assertEquals(listOf("banana"), viewModel.filterWords.value)
    }
}
