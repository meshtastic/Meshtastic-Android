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
package org.meshtastic.app.filter

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.test.KoinTest
import org.koin.test.inject
import org.meshtastic.core.repository.FilterPrefs
import org.meshtastic.core.repository.MessageFilter

@RunWith(AndroidJUnit4::class)
class MessageFilterIntegrationTest : KoinTest {

    private val filterPrefs: FilterPrefs by inject()

    private val filterService: MessageFilter by inject()

    @org.junit.Ignore("Flaky integration test, needs Koin test rule setup")
    @Test
    fun filterPrefsIntegration() = runTest {
        filterPrefs.setFilterEnabled(true)
        filterPrefs.setFilterWords(setOf("test", "spam"))
        // Wait briefly for DataStore to process the writes and flows to emit
        kotlinx.coroutines.delay(100)
        filterService.rebuildPatterns()

        assertTrue(filterService.shouldFilter("this is a test message"))
        assertTrue(filterService.shouldFilter("spam content"))
    }
}
