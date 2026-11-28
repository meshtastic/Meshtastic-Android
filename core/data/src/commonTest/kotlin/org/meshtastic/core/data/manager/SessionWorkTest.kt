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
package org.meshtastic.core.data.manager

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.runInSession
import org.meshtastic.core.testing.FakeRadioInterfaceService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionWorkTest {

    @Test
    fun `trusted work without a session executes`() = runTest {
        val service = FakeRadioInterfaceService(backgroundScope)
        var executed = false

        service.launchSessionWork(backgroundScope, session = null) { executed = true }.join()

        assertTrue(executed)
    }

    @Test
    fun `retired session invokes rejection without executing work`() = runTest {
        val service = FakeRadioInterfaceService(backgroundScope)
        val retiredSession = RadioSessionContext(generation = 1L, address = "ble:retired")
        var executed = false
        var rejected = false

        service
            .launchSessionWork(scope = backgroundScope, session = retiredSession, onRejected = { rejected = true }) {
                executed = true
            }
            .join()

        assertFalse(executed)
        assertTrue(rejected)
    }

    @Test
    fun `active session executes work`() = runTest {
        val service = FakeRadioInterfaceService(backgroundScope)
        service.setDeviceAddress("ble:active")
        service.connect()
        val activeSession = requireNotNull(service.activeSession.value)
        var executed = false

        service.launchSessionWork(backgroundScope, activeSession) { executed = true }.join()

        assertTrue(executed)
    }

    @Test
    fun `a session closing mid-persist waits for the write and does not cancel it`() = runTest {
        val service = FakeRadioInterfaceService(backgroundScope)
        service.setDeviceAddress("ble:active")
        service.connect()
        val session = assertNotNull(service.activeSession.value)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var finished = false

        service.launchSessionWork(backgroundScope, session) {
            started.complete(Unit)
            release.await()
            finished = true
        }
        val disconnect = launch { service.disconnect() }
        runCurrent()

        assertTrue(started.isCompleted)
        assertFalse(disconnect.isCompleted, "teardown must wait for the persistence unit")
        assertFalse(finished)

        release.complete(Unit)
        disconnect.join()

        assertTrue(finished, "teardown must join the write, not cancel it")
        assertNull(service.activeSession.value)
    }

    @Test
    fun `work launched after teardown started is rejected`() = runTest {
        val service = FakeRadioInterfaceService(backgroundScope)
        service.setDeviceAddress("ble:active")
        service.connect()
        val session = assertNotNull(service.activeSession.value)
        val release = CompletableDeferred<Unit>()
        service.launchSessionWork(backgroundScope, session) { release.await() }
        val disconnect = launch { service.disconnect() }
        runCurrent()
        assertFalse(disconnect.isCompleted)
        var ran = false
        var rejected = false

        service.launchSessionWork(backgroundScope, session, onRejected = { rejected = true }) { ran = true }.join()

        assertFalse(ran)
        assertTrue(rejected)
        release.complete(Unit)
        disconnect.join()
    }

    @Test
    fun `work for a closed session never runs in the next session`() = runTest {
        val service = FakeRadioInterfaceService(backgroundScope)
        service.setDeviceAddress("ble:same")
        service.connect()
        val first = assertNotNull(service.activeSession.value)
        service.disconnect()
        service.connect()
        assertNotEquals(first, service.activeSession.value, "the next session must have a new generation")
        var ran = false
        var rejected = false

        service.launchSessionWork(backgroundScope, first, onRejected = { rejected = true }) { ran = true }.join()

        assertFalse(ran)
        assertTrue(rejected)
    }

    @Test
    fun `work launched from inside admitted work joins teardown`() = runTest {
        val service = FakeRadioInterfaceService(backgroundScope)
        service.setDeviceAddress("ble:active")
        service.connect()
        val session = assertNotNull(service.activeSession.value)
        val nestedStarted = CompletableDeferred<Unit>()
        val nestedRelease = CompletableDeferred<Unit>()
        val outerRelease = CompletableDeferred<Unit>()
        var nestedDone = false

        val outer =
            launch(start = CoroutineStart.UNDISPATCHED) {
                service.runInSession(session) {
                    service.launchSessionWork(backgroundScope, session) {
                        nestedStarted.complete(Unit)
                        nestedRelease.await()
                        nestedDone = true
                    }
                    outerRelease.await()
                }
            }
        assertTrue(nestedStarted.isCompleted, "a nested launch must reach its body before teardown can cancel it")
        // Teardown cancels the session scope on entry, before the nested launch could get a dispatcher turn.
        val disconnect = launch(start = CoroutineStart.UNDISPATCHED) { service.disconnect() }

        outerRelease.complete(Unit)
        runCurrent()
        assertFalse(disconnect.isCompleted, "teardown must wait for the nested work")

        nestedRelease.complete(Unit)
        disconnect.join()
        outer.join()

        assertTrue(nestedDone)
    }
}
