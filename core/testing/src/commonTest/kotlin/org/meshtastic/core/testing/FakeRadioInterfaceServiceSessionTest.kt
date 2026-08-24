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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeRadioInterfaceServiceSessionTest {

    @Test
    fun `selection does not admit a session and every connected start advances generation`() = runTest {
        val service = FakeRadioInterfaceService(serviceScope = backgroundScope)

        service.setDeviceAddress("ble:same")
        assertNull(service.activeSession.value, "address selection alone must not admit transport callbacks")
        assertEquals(0L, service.sessionGeneration.value)

        service.connect()
        assertEquals(1L, service.sessionGeneration.value)
        assertEquals(1L, service.activeSession.value?.generation)
        assertEquals("ble:same", service.activeSession.value?.address)

        service.connect()
        assertEquals(1L, service.sessionGeneration.value, "repeated connect must preserve the active session")

        service.restartTransport()
        assertEquals(2L, service.sessionGeneration.value)
        assertEquals(2L, service.activeSession.value?.generation)
        assertEquals("ble:same", service.activeSession.value?.address)

        service.disconnect()
        assertNull(service.activeSession.value)

        service.restartTransport()
        assertEquals(2L, service.sessionGeneration.value, "inactive restart must not admit a transport session")
        assertNull(service.activeSession.value)

        service.connect()
        assertEquals(3L, service.sessionGeneration.value)
        assertEquals(3L, service.activeSession.value?.generation)
        assertEquals("ble:same", service.activeSession.value?.address)
    }

    @Test
    fun `trySendToRadio records bytes only while a session is admitted`() = runTest {
        val service = FakeRadioInterfaceService(serviceScope = backgroundScope)
        val bytes = byteArrayOf(1, 2, 3)

        assertFalse(service.trySendToRadio(bytes))
        assertTrue(service.sentToRadio.isEmpty())

        service.setDeviceAddress("ble:test")
        service.connect()
        assertTrue(service.trySendToRadio(bytes))
        assertEquals(1, service.sentToRadio.size)
        assertTrue(bytes.contentEquals(service.sentToRadio.single()))

        service.disconnect()
        assertFalse(service.trySendToRadio(bytes))
        assertEquals(1, service.sentToRadio.size)
    }

    @Test
    fun `trySendToRadio can reject an admitted transport handoff without recording bytes`() = runTest {
        val service = FakeRadioInterfaceService(serviceScope = backgroundScope)
        service.setDeviceAddress("ble:test")
        service.connect()
        service.rejectAdmittedSends = true

        assertFalse(service.trySendToRadio(byteArrayOf(1, 2, 3)))
        assertTrue(service.sentToRadio.isEmpty())
        assertEquals(0, service.sessionLeaseInvariantViolations)
    }

    @Test
    fun `recorded radio writes are isolated from caller mutation`() = runTest {
        val service = FakeRadioInterfaceService(serviceScope = backgroundScope)
        val bytes = byteArrayOf(1, 2, 3)
        service.setDeviceAddress("ble:test")
        service.connect()

        assertTrue(service.trySendToRadio(bytes))
        bytes[0] = 9
        val recorded = service.sentToRadio.single()
        assertContentEquals(byteArrayOf(1, 2, 3), recorded)

        recorded[1] = 8
        assertContentEquals(byteArrayOf(1, 2, 3), service.sentToRadio.single())
    }

    @Test
    fun `disconnect closes admission and waits for admitted fake session work`() = runTest {
        val service = FakeRadioInterfaceService(serviceScope = backgroundScope)
        service.setDeviceAddress("ble:test")
        service.connect()
        val session = requireNotNull(service.activeSession.value)
        val workStarted = CompletableDeferred<Unit>()
        val releaseWork = CompletableDeferred<Unit>()
        val leaseStillCurrent = CompletableDeferred<Boolean>()

        val work = launch {
            assertTrue(
                service.runWithSessionLease(session) { lease ->
                    workStarted.complete(Unit)
                    releaseWork.await()
                    leaseStillCurrent.complete(lease.isCurrent())
                },
            )
        }
        workStarted.await()

        val disconnect = launch { service.disconnect() }
        runCurrent()

        assertFalse(disconnect.isCompleted, "disconnect must drain the admitted fake session lease")
        assertFalse(service.isSessionActive(session), "disconnect must reject late fake-session work immediately")
        assertEquals(session, service.activeSession.value, "lifecycle completion remains published while work drains")

        releaseWork.complete(Unit)
        work.join()
        assertTrue(leaseStillCurrent.await(), "admitted fake work retains its lifecycle lease until return")
        disconnect.join()
        assertNull(service.activeSession.value)
    }
}
