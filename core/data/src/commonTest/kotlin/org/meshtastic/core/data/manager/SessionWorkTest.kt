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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeRadioInterfaceService
import kotlin.test.Test
import kotlin.test.assertFalse
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
    fun `active session executes leased work`() = runTest {
        val service = FakeRadioInterfaceService(backgroundScope)
        service.setDeviceAddress("ble:active")
        service.connect()
        val activeSession = requireNotNull(service.activeSession.value)
        var executed = false

        service.launchSessionWork(backgroundScope, activeSession) { executed = true }.join()

        assertTrue(executed)
    }
}
