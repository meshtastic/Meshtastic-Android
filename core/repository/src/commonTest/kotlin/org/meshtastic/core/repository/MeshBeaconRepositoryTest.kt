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
package org.meshtastic.core.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.MeshBeacon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshBeaconRepositoryTest {

    private class InMemoryBeaconPrefs : MeshBeaconPrefs {
        override val storedBeacons: StateFlow<List<String>> = MutableStateFlow(emptyList())

        override fun setStoredBeacons(records: List<String>) = Unit
    }

    private fun offer(fromNodeNum: Int) = MeshBeaconOffer(
        fromNodeNum = fromNodeNum,
        beacon =
        MeshBeacon.Builder()
            .also { wb ->
                wb.offer_channel = ChannelSettings.Builder().also { cs -> cs.name = "Invite" }.build()
            }
            .build(),
    )

    private fun repository(scope: CoroutineScope) = MeshBeaconRepository(InMemoryBeaconPrefs(), scope)

    @Test
    fun `a rebroadcast of a standing invitation is not new`() = runTest {
        val repository = repository(backgroundScope)

        assertTrue(repository.add(offer(1)))
        assertFalse(repository.add(offer(1)))
        assertTrue(repository.add(offer(2)))
    }

    @Test
    fun `an invitation is new again after it is dismissed`() = runTest {
        val repository = repository(backgroundScope)
        repository.add(offer(1))

        repository.dismiss(offer(1).key)

        assertTrue(repository.add(offer(1)))
    }

    @Test
    fun `concurrent arrivals of one invitation report it new exactly once`() = runTest {
        repeat(ROUNDS) {
            val repository = repository(backgroundScope)
            val results =
                withContext(Dispatchers.Default) { List(ARRIVALS) { async { repository.add(offer(7)) } }.awaitAll() }

            assertEquals(1, results.count { it }, "round $it")
        }
    }

    private companion object {
        const val ROUNDS = 200
        const val ARRIVALS = 8
    }
}
