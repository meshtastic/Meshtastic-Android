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
package org.meshtastic.core.prefs.filter

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.FilterPrefs

class FilterPrefsTest {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var filterPrefs: FilterPrefs
    private lateinit var dispatchers: CoroutineDispatchers

    @Before
    fun setup() {
        editor = mockk(relaxed = true)
        sharedPreferences = mockk {
            every { getBoolean(FilterPrefsImpl.KEY_FILTER_ENABLED, false) } returns false
            every { getStringSet(FilterPrefsImpl.KEY_FILTER_WORDS, emptySet()) } returns emptySet()
            every { edit() } returns editor
        }
        dispatchers = mockk { every { default } returns Dispatchers.Unconfined }
        filterPrefs = FilterPrefsImpl(sharedPreferences, dispatchers)
    }

    @Test
    fun `filterEnabled defaults to false`() {
        assertFalse(filterPrefs.filterEnabled.value)
    }

    @Test
    fun `filterWords defaults to empty set`() {
        assertTrue(filterPrefs.filterWords.value.isEmpty())
    }

    @Test
    fun `setting filterEnabled updates preference`() {
        filterPrefs.setFilterEnabled(true)
        verify { editor.putBoolean(FilterPrefsImpl.KEY_FILTER_ENABLED, true) }
    }

    @Test
    fun `setting filterWords updates preference`() {
        val words = setOf("test", "word")
        filterPrefs.setFilterWords(words)
        verify { editor.putStringSet(FilterPrefsImpl.KEY_FILTER_WORDS, words) }
    }
}
