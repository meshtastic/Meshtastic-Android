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
package org.meshtastic.core.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.testing.FakeBleService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [KableMeshtasticRadioProfile] — the GATT characteristic orchestration layer.
 *
 * Uses [FakeBleService] from `core:testing`. Since [FakeBleService] inherits the default [BleService.observe] overload
 * (which invokes `onSubscription` via `onStart`), `awaitSubscriptionReady()` completes immediately — matching the
 * behaviour expected from non-Kable implementations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KableMeshtasticRadioProfileTest {

    private fun createService(): FakeBleService = FakeBleService().apply {
        addCharacteristic(MeshtasticBleConstants.FROMNUM_CHARACTERISTIC)
        addCharacteristic(MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC)
        addCharacteristic(MeshtasticBleConstants.TORADIO_CHARACTERISTIC)
    }

    @Test
    fun `awaitSubscriptionReady completes when using FakeBleService`() = runTest {
        val service = createService()
        val profile = KableMeshtasticRadioProfile(service)

        // Start collecting fromRadio to activate the observe() flow (which triggers onSubscription)
        val collectJob = launch { profile.fromRadio.first() }
        advanceUntilIdle()

        // Should not hang — FakeBleService's default observe(char, onSubscription) fires onSubscription eagerly
        profile.awaitSubscriptionReady()

        collectJob.cancel()
    }

    @Test
    fun `sendToRadio writes to TORADIO and triggers drain`() = runTest {
        val service = createService()
        val profile = KableMeshtasticRadioProfile(service)
        val testData = byteArrayOf(1, 2, 3)

        // Enqueue empty read so the drain loop terminates
        service.enqueueRead(MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC, ByteArray(0))

        profile.sendToRadio(testData)

        assertEquals(1, service.writes.size)
        assertTrue(service.writes[0].data.contentEquals(testData))
    }

    @Test
    fun `fromRadio emits packets from FROMRADIO reads`() = runTest {
        val service = createService()
        val profile = KableMeshtasticRadioProfile(service)

        val packet1 = byteArrayOf(10, 20, 30)
        service.enqueueRead(MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC, packet1)
        // Empty read terminates the drain loop
        service.enqueueRead(MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC, ByteArray(0))

        val received = async { profile.fromRadio.first() }
        advanceUntilIdle()

        assertTrue(received.await().contentEquals(packet1))
    }

    @Test
    fun `requestDrain triggers additional FROMRADIO reads`() = runTest {
        val service = createService()
        val profile = KableMeshtasticRadioProfile(service)

        val received = mutableListOf<ByteArray>()

        // Start the fromRadio collector
        val collectJob = launch { profile.fromRadio.collect { received.add(it) } }
        advanceUntilIdle()

        // First drain should have completed (initial seed) with nothing queued.
        // Now enqueue a packet and trigger a manual drain.
        val latePacket = byteArrayOf(99)
        service.enqueueRead(MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC, latePacket)
        service.enqueueRead(MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC, ByteArray(0))
        profile.requestDrain()
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertTrue(received[0].contentEquals(latePacket))

        collectJob.cancel()
    }

    @Test
    fun `MeshtasticRadioProfile default awaitSubscriptionReady returns immediately`() = runTest {
        val profile =
            object : MeshtasticRadioProfile {
                override val fromRadio = kotlinx.coroutines.flow.emptyFlow<ByteArray>()
                override val logRadio = kotlinx.coroutines.flow.emptyFlow<ByteArray>()

                override suspend fun sendToRadio(packet: ByteArray) {}
            }
        // Should not hang — default implementation is a no-op
        profile.awaitSubscriptionReady()
    }
}
