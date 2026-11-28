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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.meshtastic.core.repository.runInSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

        service.disconnect()
        assertNull(service.activeSession.value)

        service.connect()
        assertEquals(2L, service.sessionGeneration.value)
        assertEquals(2L, service.activeSession.value?.generation)
        assertEquals("ble:same", service.activeSession.value?.address)
    }

    @Test
    fun `disconnect closes admission and waits for admitted fake session work`() = runTest {
        val service = FakeRadioInterfaceService(serviceScope = backgroundScope)
        service.setDeviceAddress("ble:test")
        service.connect()
        val session = requireNotNull(service.activeSession.value)
        val workStarted = CompletableDeferred<Unit>()
        val releaseWork = CompletableDeferred<Unit>()
        val sessionStillCurrent = CompletableDeferred<Boolean>()
        var workDone = false

        val work =
            assertNotNull(service.sessionScope(session)).launch(start = CoroutineStart.UNDISPATCHED) {
                withContext(NonCancellable) {
                    workStarted.complete(Unit)
                    releaseWork.await()
                    sessionStillCurrent.complete(service.activeSession.value == session)
                    workDone = true
                }
            }
        workStarted.await()

        val disconnect = launch { service.disconnect() }
        runCurrent()

        assertFalse(disconnect.isCompleted, "disconnect must drain the admitted fake session work")
        assertFalse(service.isSessionActive(session), "disconnect must reject late fake-session work immediately")
        assertEquals(session, service.activeSession.value, "lifecycle completion remains published while work drains")

        releaseWork.complete(Unit)
        work.join()
        assertTrue(workDone, "teardown joins admitted fake work instead of cancelling it")
        assertTrue(sessionStillCurrent.await(), "admitted fake work keeps its session published until it returns")
        disconnect.join()
        assertNull(service.activeSession.value)
    }

    @Test
    fun `runInSession is rejected for a closed session and never runs`() = runTest {
        val service = FakeRadioInterfaceService(serviceScope = backgroundScope)
        service.setDeviceAddress("ble:test")
        service.connect()
        val session = requireNotNull(service.activeSession.value)
        service.disconnect()
        var ran = false

        val result = service.runInSession(session) { ran = true }

        assertFalse(result)
        assertFalse(ran)
    }

    @Test
    fun `a caller cancelled mid-block cancels the block`() = runTest {
        val service = FakeRadioInterfaceService(serviceScope = backgroundScope)
        service.setDeviceAddress("ble:test")
        service.connect()
        val session = requireNotNull(service.activeSession.value)
        val blockStarted = CompletableDeferred<Unit>()
        var cancelled = false

        val caller = launch {
            service.runInSession(session) {
                try {
                    blockStarted.complete(Unit)
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
        runCurrent()
        assertTrue(blockStarted.isCompleted)

        caller.cancel()
        runCurrent()

        assertTrue(cancelled, "a cancelled caller must not leave its block running in the session scope")
    }

    @Test
    fun `a session closing mid-block returns false to a caller that is still active`() = runTest {
        val service = FakeRadioInterfaceService(serviceScope = backgroundScope)
        service.setDeviceAddress("ble:test")
        service.connect()
        val session = requireNotNull(service.activeSession.value)
        val blockStarted = CompletableDeferred<Unit>()
        var result: Boolean? = null

        val caller = launch {
            result =
                service.runInSession(session) {
                    blockStarted.complete(Unit)
                    awaitCancellation()
                }
        }
        runCurrent()
        assertTrue(blockStarted.isCompleted)

        service.disconnect()
        runCurrent()

        assertEquals(false, result)
        assertFalse(caller.isCancelled, "a session closing under the block must not cancel its caller")
        assertTrue(caller.isCompleted)
    }

    @Test
    fun `a failure in the block reaches the caller`() = runTest {
        val service = FakeRadioInterfaceService(serviceScope = backgroundScope)
        service.setDeviceAddress("ble:test")
        service.connect()
        val session = requireNotNull(service.activeSession.value)

        val failure = assertFailsWith<IllegalStateException> { service.runInSession(session) { error("block failed") } }

        assertEquals("block failed", failure.message)
        assertTrue(service.isSessionActive(session), "a block failure must not close the session")
    }
}
