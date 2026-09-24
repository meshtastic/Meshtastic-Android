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
import dev.mokkery.mock
import dev.mokkery.verify
import org.meshtastic.core.repository.MessageFilter
import org.meshtastic.core.testing.FakeFilterPrefs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterSettingsViewModelTest {

    private val filterPrefs = FakeFilterPrefs()
    private val messageFilter: MessageFilter = mock(MockMode.autofill)

    private lateinit var viewModel: FilterSettingsViewModel

    @BeforeTest
    fun setUp() {
        filterPrefs.setFilterEnabled(true)
        filterPrefs.setFilterWords(setOf("apple", "banana"))

        viewModel = FilterSettingsViewModel(filterPrefs = filterPrefs, messageFilter = messageFilter)
    }

    @Test
    fun setFilterEnabled_updates_prefs_and_state() {
        viewModel.setFilterEnabled(false)
        assertEquals(false, filterPrefs.filterEnabled.value)
        assertEquals(false, viewModel.filterEnabled.value)
    }

    @Test
    fun state_follows_prefs_that_load_after_creation() {
        val coldPrefs = FakeFilterPrefs()
        val coldViewModel = FilterSettingsViewModel(filterPrefs = coldPrefs, messageFilter = messageFilter)

        coldPrefs.setFilterEnabled(true)
        coldPrefs.setFilterWords(setOf("cherry"))

        assertEquals(true, coldViewModel.filterEnabled.value)
        assertEquals(setOf("cherry"), coldViewModel.filterWords.value)
    }

    @Test
    fun addFilterWord_updates_prefs_and_rebuilds_patterns() {
        viewModel.addFilterWord("cherry")

        verify { messageFilter.rebuildPatterns() }
        assertEquals(setOf("apple", "banana", "cherry"), viewModel.filterWords.value)
    }

    @Test
    fun removeFilterWord_updates_prefs_and_rebuilds_patterns() {
        viewModel.removeFilterWord("apple")

        verify { messageFilter.rebuildPatterns() }
        assertEquals(setOf("banana"), viewModel.filterWords.value)
    }
}
