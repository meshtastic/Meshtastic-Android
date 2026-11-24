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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.sdk.MeshtasticException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UsbPermissionWaitTest {
    private var requests = 0
    private val events = mutableListOf<String>()

    private suspend fun UsbPermissionWait.run(
        device: String = "usb-1",
        granted: Boolean = false,
        answer: Boolean? = false,
        awaitChange: suspend (String) -> Unit = { events += "wait" },
    ) = await(
        deviceName = { device },
        hasPermission = { granted },
        request = {
            requests++
            events += "request"
            answer
        },
        awaitChange = awaitChange,
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
    fun `after a deny the next call suspends before it asks again`() = runTest {
        val wait = UsbPermissionWait()
        assertFailsWith<UsbPermissionDeniedException> { wait.run() }
        val release = CompletableDeferred<Unit>()
        val second = async {
            runCatching { wait.run(awaitChange = { release.await() }) }
        }
        runCurrent()
        assertEquals(1, requests)
        release.complete(Unit)
        second.await()
        assertEquals(2, requests)
    }

    // Catches the stuck state where a device re-enumerated between attempts waited for yet another replug.
    @Test
    fun `a device replaced between attempts asks again without waiting`() = runTest {
        val wait = UsbPermissionWait()
        assertFailsWith<UsbPermissionDeniedException> { wait.run(device = "usb-1") }
        events.clear()
        assertFailsWith<UsbPermissionDeniedException> { wait.run(device = "usb-2") }
        assertEquals(listOf("request"), events)
    }

    // Catches a denial on one radio silencing the prompt for a different radio.
    @Test
    fun `a deny on one device does not suppress the prompt for another`() = runTest {
        val wait = UsbPermissionWait()
        assertFailsWith<UsbPermissionDeniedException> { wait.run(device = "usb-1") }
        wait.run(device = "usb-2", answer = true)
        assertEquals(2, requests)
        assertEquals(listOf("request", "request"), events)
    }

    @Test
    fun `a grant after a deny is accepted without another prompt`() = runTest {
        val wait = UsbPermissionWait()
        assertFailsWith<UsbPermissionDeniedException> { wait.run() }
        wait.run(granted = true)
        assertEquals(1, requests)
        wait.run(granted = true)
        assertEquals(listOf("request", "wait"), events)
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
