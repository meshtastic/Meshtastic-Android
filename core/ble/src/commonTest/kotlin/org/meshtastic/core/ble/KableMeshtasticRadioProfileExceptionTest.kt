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

import com.juul.kable.GattStatusException
import com.juul.kable.NotConnectedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.testing.FakeBleService
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests that [KableMeshtasticRadioProfile.fromRadio] propagates session-fatal BLE exceptions (so the transport layer
 * can detect broken sessions) while suppressing transient errors.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KableMeshtasticRadioProfileExceptionTest {

    private fun createService(): FakeBleService = FakeBleService().apply {
        addCharacteristic(MeshtasticBleConstants.FROMNUM_CHARACTERISTIC)
        addCharacteristic(MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC)
        addCharacteristic(MeshtasticBleConstants.TORADIO_CHARACTERISTIC)
    }

    @Test
    fun `fromRadio propagates NotConnectedException to collector`() = runTest {
        val service = createService()
        val profile = KableMeshtasticRadioProfile(service)

        // Set up the read to throw NotConnectedException immediately
        service.readException = NotConnectedException("session closed")

        val result = assertFailsWith<NotConnectedException> { profile.fromRadio.first() }
        assertTrue(result.message!!.contains("session closed"))
    }

    @Test
    fun `fromRadio propagates fatal GattStatusException to collector`() = runTest {
        val service = createService()
        val profile = KableMeshtasticRadioProfile(service)

        // Fatal GATT status (133 = GATT_ERROR, in FATAL_GATT_STATUSES)
        service.readException = GattStatusException(message = "GATT error", status = 133)

        val result = assertFailsWith<GattStatusException> { profile.fromRadio.first() }
        assertTrue(result.message!!.contains("GATT error"))
    }

    @Test
    fun `fromRadio propagates CancellationException`() = runTest {
        val service = createService()
        val profile = KableMeshtasticRadioProfile(service)

        // Set up the read to throw CancellationException
        service.readException = CancellationException("cancelled")

        val result = assertFailsWith<CancellationException> { profile.fromRadio.first() }
        assertTrue(result.message!!.contains("cancelled"))
    }

    @Test
    fun `fromRadio suppresses transient GattStatusException and continues`() = runTest {
        val service = createService()
        val profile = KableMeshtasticRadioProfile(service)

        // Non-fatal GATT status (e.g. status 6 = GATT_BUSY, not in FATAL_GATT_STATUSES)
        service.readException = GattStatusException(message = "transient busy", status = 6)

        // Collect from the flow in a coroutine; after the transient error is suppressed and the
        // delay elapses, the next drain trigger should restart reading. Enqueue a real packet so
        // the collector eventually receives something.
        var collected = false
        val collectJob = launch { profile.fromRadio.collect { collected = true } }
        advanceUntilIdle()

        // The transient error was suppressed (500ms delay). Advance past it.
        advanceTimeBy(600)

        // Now enqueue a packet so the next drain cycle emits data, proving the flow survived.
        service.enqueueRead(MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC, byteArrayOf(42))
        service.enqueueRead(MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC, ByteArray(0))
        profile.requestDrain()
        advanceUntilIdle()

        assertTrue(collected, "Flow should have emitted a packet after transient error was suppressed")

        collectJob.cancel()
    }

    // --- logRadio fatal exception propagation tests ---

    @Test
    fun `logRadio propagates NotConnectedException from observation`() = runTest {
        val service = createService().apply { addCharacteristic(MeshtasticBleConstants.LOGRADIO_CHARACTERISTIC) }
        val profile = KableMeshtasticRadioProfile(service)
        service.observeException = NotConnectedException("log radio session closed")
        assertFailsWith<NotConnectedException> { profile.logRadio.first() }
    }

    @Test
    fun `logRadio propagates fatal GattStatusException from observation`() = runTest {
        val service = createService().apply { addCharacteristic(MeshtasticBleConstants.LOGRADIO_CHARACTERISTIC) }
        val profile = KableMeshtasticRadioProfile(service)
        service.observeException = GattStatusException(status = 133, message = "GATT error")
        assertFailsWith<GattStatusException> { profile.logRadio.first() }
    }

    // --- subscriptionReady exceptional completion tests ---

    @Test
    fun `awaitSubscriptionReady throws promptly when FROMNUM observe fails before readiness`() = runTest {
        val service = createService()
        val profile = KableMeshtasticRadioProfile(service)

        // Set observeException — when fromRadio is collected, the FROMNUM observe will throw
        // before subscriptionReady is completed. The fix completes it exceptionally.
        service.observeException = NotConnectedException("observe failed before CCCD")

        // Start collecting fromRadio in a regular child coroutine so it's properly scoped
        // and cancelled by the test framework. The exception is caught inside the coroutine,
        // so it won't crash the test scope.
        val collectJob = launch {
            try {
                profile.fromRadio.collect {}
            } catch (e: Exception) {
                // Expected — the observe failure propagates through the channelFlow
            }
        }
        try {
            advanceUntilIdle()

            // awaitSubscriptionReady should throw the exception promptly, not hang
            val result = assertFailsWith<NotConnectedException> { profile.awaitSubscriptionReady() }
            assertTrue(result.message!!.contains("observe failed before CCCD"))
        } finally {
            collectJob.cancel()
        }
    }
}
