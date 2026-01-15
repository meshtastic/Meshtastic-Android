/*
 * Copyright (c) 2025 Meshtastic LLC
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FilterPrefsTest {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var filterPrefs: FilterPrefs

    @Before
    fun setup() {
        editor = mockk(relaxed = true)
        sharedPreferences = mockk {
            every { getBoolean(FilterPrefs.KEY_FILTER_ENABLED, false) } returns false
            every { getStringSet(FilterPrefs.KEY_FILTER_WORDS, emptySet()) } returns emptySet()
            every { edit() } returns editor
        }
        filterPrefs = FilterPrefsImpl(sharedPreferences)
    }

    @Test
    fun `filterEnabled defaults to false`() {
        assertFalse(filterPrefs.filterEnabled)
    }

    @Test
    fun `filterWords defaults to empty set`() {
        assertTrue(filterPrefs.filterWords.isEmpty())
    }

    @Test
    fun `setting filterEnabled updates preference`() {
        filterPrefs.filterEnabled = true
        verify { editor.putBoolean(FilterPrefs.KEY_FILTER_ENABLED, true) }
    }

    @Test
    fun `setting filterWords updates preference`() {
        val words = setOf("test", "word")
        filterPrefs.filterWords = words
        verify { editor.putStringSet(FilterPrefs.KEY_FILTER_WORDS, words) }
    }
}
