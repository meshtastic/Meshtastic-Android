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
package org.meshtastic.feature.settings.filter

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.meshtastic.core.repository.FilterPrefs
import org.meshtastic.core.repository.MessageFilter
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterSettingsViewModelTest {

    private val filterPrefs: FilterPrefs = mock(MockMode.autofill)
    private val messageFilter: MessageFilter = mock(MockMode.autofill)

    private lateinit var viewModel: FilterSettingsViewModel

    @BeforeTest
    fun setUp() {
        every { filterPrefs.filterEnabled } returns MutableStateFlow(true)
        every { filterPrefs.filterWords } returns MutableStateFlow(setOf("apple", "banana"))

        viewModel = FilterSettingsViewModel(filterPrefs = filterPrefs, messageFilter = messageFilter)
    }

    @Test
    fun setFilterEnabled_updates_prefs_and_state() {
        viewModel.setFilterEnabled(false)
        verify { filterPrefs.setFilterEnabled(false) }
        assertEquals(false, viewModel.filterEnabled.value)
    }

    @Test
    fun addFilterWord_updates_prefs_and_rebuilds_patterns() {
        viewModel.addFilterWord("cherry")

        verify { filterPrefs.setFilterWords(any()) }
        verify { messageFilter.rebuildPatterns() }
        assertEquals(listOf("apple", "banana", "cherry"), viewModel.filterWords.value)
    }

    @Test
    fun removeFilterWord_updates_prefs_and_rebuilds_patterns() {
        viewModel.removeFilterWord("apple")

        verify { filterPrefs.setFilterWords(any()) }
        verify { messageFilter.rebuildPatterns() }
        assertEquals(listOf("banana"), viewModel.filterWords.value)
    }
}
