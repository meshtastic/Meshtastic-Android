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
package org.meshtastic.core.data.radio

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.sdk.MeshtasticException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UsbPermissionWaitTest {
    private var requests = 0
    private var reattachWaits = 0

    private suspend fun UsbPermissionWait.run(granted: Boolean = false, answer: Boolean? = false) = await(
        hasPermission = { granted },
        request = {
            requests++
            answer
        },
        awaitReattachOrGrant = { reattachWaits++ },
    )

    // Catches the regression where a deny was ignored and the wait polled forever.
    @Test
    fun `a denied prompt throws the typed exception instead of waiting`() = runTest {
        assertFailsWith<UsbPermissionDeniedException> { UsbPermissionWait().run(answer = false) }
        assertEquals(1, requests)
    }

    @Test
    fun `a prompt that never answers counts as denied`() = runTest {
        assertFailsWith<UsbPermissionDeniedException> { UsbPermissionWait().run(answer = null) }
    }

    // Catches a prompt on every SDK backoff retry after a deny.
    @Test
    fun `after a deny the next call waits for re-attach before it asks again`() = runTest {
        val wait = UsbPermissionWait()
        assertFailsWith<UsbPermissionDeniedException> { wait.run(answer = false) }
        assertEquals(0, reattachWaits)
        assertFailsWith<UsbPermissionDeniedException> { wait.run(answer = false) }
        assertEquals(1, reattachWaits)
        assertEquals(2, requests)
    }

    @Test
    fun `a grant after a deny is accepted without another prompt`() = runTest {
        val wait = UsbPermissionWait()
        assertFailsWith<UsbPermissionDeniedException> { wait.run(answer = false) }
        wait.run(granted = true)
        assertEquals(1, requests)
        wait.run(granted = true)
        assertEquals(1, reattachWaits)
    }

    @Test
    fun `an already held permission never prompts`() = runTest {
        UsbPermissionWait().run(granted = true)
        assertEquals(0, requests)
    }

    // Catches the failure reason being dropped so the screen shows no message.
    @Test
    fun `a transport failure caused by the typed exception maps to UsbPermissionDenied`() {
        val cause = MeshtasticException.Transport("open failed", UsbPermissionDeniedException())
        assertEquals(TransportDisconnectReason.UsbPermissionDenied, usbFailureReason(cause))
    }

    @Test
    fun `a transport failure with another cause maps to no reason`() {
        assertNull(usbFailureReason(MeshtasticException.Transport("io", RuntimeException())))
    }
}
