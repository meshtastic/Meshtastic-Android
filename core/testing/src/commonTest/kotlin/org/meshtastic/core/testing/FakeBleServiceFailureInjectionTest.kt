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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.ble.BleCharacteristic
import org.meshtastic.core.ble.BleWriteType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class FakeBleServiceFailureInjectionTest {
    private val char = BleCharacteristic(Uuid.random())
    private val service = FakeBleService()

    @Test
    fun writeExceptionIsPersistent() = runTest {
        service.writeException = Exception("write-fail")
        val ex1 = assertFailsWith<Exception> { service.write(char, ByteArray(0), BleWriteType.WITH_RESPONSE) }
        assertTrue(ex1.message == "write-fail", "Expected write-fail message")
        // Prove persistence — second write also throws because exception persists
        val ex2 = assertFailsWith<Exception> { service.write(char, ByteArray(1), BleWriteType.WITH_RESPONSE) }
        assertTrue(ex2.message == "write-fail", "Expected write-fail message on second call")
        // tests must explicitly clear it
        service.writeException = null
        service.write(char, ByteArray(2), BleWriteType.WITH_RESPONSE)
        assertTrue(service.writes.size == 1)
    }

    @Test
    fun readExceptionIsThrownAndReset() = runTest {
        service.readException = Exception("read-fail")
        assertFailsWith<Exception> { service.read(char) }
        assertTrue(service.read(char).isEmpty(), "Read should return empty after exception reset")
    }

    @Test
    fun observeExceptionCausesFlowException() = runTest {
        service.observeException = Exception("observe-fail")
        val flow = service.observe(char)
        assertFailsWith<Exception> { flow.collect() }
    }

    @Test
    fun observeExceptionByCharacteristicOnlyFailsTargetCharacteristic() = runTest {
        val otherChar = BleCharacteristic(Uuid.random())
        service.observeExceptionsByCharacteristic[char.uuid] = Exception("target-observe-fail")

        val ex = assertFailsWith<Exception> { service.observe(char).collect() }
        assertTrue(ex.message == "target-observe-fail", "Expected target observe failure")

        var subscribed = false
        service.emitNotification(otherChar.uuid, byteArrayOf(1))
        withTimeoutOrNull(100) { service.observe(otherChar) { subscribed = true }.first() }
        assertTrue(subscribed, "Other characteristic should still subscribe normally")
    }

    @Test
    fun observeBeforeSubscriptionExceptionDoesNotInvokeOnSubscription() = runTest {
        var subscribed = false
        service.observeBeforeSubscriptionExceptionByCharacteristic[char.uuid] = Exception("before-subscribe-fail")

        val ex = assertFailsWith<Exception> { service.observe(char) { subscribed = true }.collect() }

        assertTrue(ex.message == "before-subscribe-fail", "Expected before-subscription failure")
        assertFalse(subscribed, "onSubscription must not run before the injected failure")
    }

    @Test
    fun observeExceptionByCharacteristicInTwoArgAlsoThrowsBeforeSubscription() = runTest {
        // observeExceptionsByCharacteristic is the 1-arg seam, but the 2-arg override must also treat it as a
        // pre-readiness failure — otherwise the default onStart wrap would invoke onSubscription before the throw.
        var subscribed = false
        service.observeExceptionsByCharacteristic[char.uuid] = Exception("shared-observe-fail")

        val ex = assertFailsWith<Exception> { service.observe(char) { subscribed = true }.collect() }

        assertTrue(ex.message == "shared-observe-fail", "Expected the shared characteristic exception")
        assertFalse(subscribed, "onSubscription must not run before a shared characteristic failure")
    }

    @Test
    fun observeNeverSubscribeDoesNotInvokeOnSubscription() = runTest {
        var subscribed = false
        service.observeNeverSubscribeCharacteristics += char.uuid

        withTimeoutOrNull(100) { service.observe(char) { subscribed = true }.collect() }

        assertFalse(subscribed, "onSubscription must not run for never-subscribe characteristic")
    }

    @Test
    fun observeNeverSubscribeStillExposesNotifications() = runTest {
        // Even though onSubscription is suppressed, the returned flow is the bare SharedFlow, so emitNotification
        // must still reach active collectors.
        service.observeNeverSubscribeCharacteristics += char.uuid
        var subscribed = false
        var received: ByteArray? = null

        val collector = launch { service.observe(char) { subscribed = true }.collect { received = it } }
        testScheduler.advanceUntilIdle()
        service.emitNotification(char.uuid, byteArrayOf(1, 2, 3))
        testScheduler.advanceUntilIdle()
        collector.cancel()

        assertFalse(subscribed, "onSubscription must not run for never-subscribe characteristic")
        assertNotNull(received, "Notifications must still flow through the bare SharedFlow")
        assertTrue(received!!.contentEquals(byteArrayOf(1, 2, 3)), "Notification payload must be exposed verbatim")
    }
}
