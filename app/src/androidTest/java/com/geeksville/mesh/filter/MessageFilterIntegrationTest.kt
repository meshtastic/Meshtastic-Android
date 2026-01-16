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
package com.geeksville.mesh.filter

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.prefs.filter.FilterPrefs
import org.meshtastic.core.service.filter.MessageFilterService
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MessageFilterIntegrationTest {

    @get:Rule var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var filterPrefs: FilterPrefs

    @Inject lateinit var filterService: MessageFilterService

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun filterPrefsIntegration() = runTest {
        filterPrefs.filterEnabled = true
        filterPrefs.filterWords = setOf("test", "spam")
        filterService.rebuildPatterns()

        assertTrue(filterService.shouldFilter("this is a test message"))
        assertTrue(filterService.shouldFilter("spam content"))
    }
}
