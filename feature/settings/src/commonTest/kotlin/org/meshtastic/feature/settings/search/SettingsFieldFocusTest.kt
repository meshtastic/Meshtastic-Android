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
package org.meshtastic.feature.settings.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.navigation.SettingsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsFieldFocusTest {

    private val focus = SettingsFieldFocus()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun choosingAResultLeavesItsFieldPendingForTheScreen() {
        val viewModel = SettingsSearchViewModel(focus)
        val entry =
            ResolvedSettingsEntry(
                id = "hop",
                title = "Hop limit",
                description = null,
                screenTitle = "LoRa",
                route = SettingsRoute.LoRa,
                fieldKey = "schema_lora_hop_limit",
            )

        val route = viewModel.select(entry)

        assertEquals(entry.route, route)
        assertEquals("schema_lora_hop_limit", focus.pending.value?.key)
    }

    @Test
    fun choosingAScreenClearsAnEarlierFieldRequest() {
        focus.request("schema_lora_hop_limit")

        focus.request(null)

        assertNull(focus.pending.value)
    }

    @Test
    fun consumingAnOlderRequestDoesNotDropANewerRequest() {
        focus.request("schema_lora_hop_limit")
        val older = focus.pending.value!!
        focus.request("schema_lora_region")

        focus.consume(older)

        assertEquals("schema_lora_region", focus.pending.value?.key)
    }

    @Test
    fun consumingThePendingRequestClearsIt() {
        focus.request("schema_lora_hop_limit")

        focus.consume(focus.pending.value!!)

        assertNull(focus.pending.value)
    }

    @Test
    fun disposingTheScreenThatConsumedARequestKeepsTheNextRequestPending() {
        focus.request("schema_lora_hop_limit")
        val a = focus.pending.value!!
        focus.consume(a)
        focus.request("schema_lora_region")

        focus.clear(a)

        assertEquals("schema_lora_region", focus.pending.value?.key)
    }

    @Test
    fun selectingTheSameKeyTwiceMakesDistinctRequests() {
        focus.request("schema_lora_hop_limit")
        val first = focus.pending.value!!
        focus.request("schema_lora_hop_limit")
        val second = focus.pending.value!!

        focus.clear(first)

        assertNotEquals(first, second)
        assertEquals(second, focus.pending.value)
    }

    @Test
    fun clearingTheStillPendingRequestDropsIt() {
        focus.request("schema_lora_hop_limit")

        focus.clear(focus.pending.value)

        assertNull(focus.pending.value)
    }
}
