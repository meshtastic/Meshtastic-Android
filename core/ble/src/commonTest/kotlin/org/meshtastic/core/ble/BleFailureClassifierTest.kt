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
import org.meshtastic.core.model.TransportFailureKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [classifyBleFailure] against the failures seen on the bench. Web Bluetooth rejections reach common code as
 * `JsException`, which `asException` rethrows as an [IllegalStateException] carrying the browser's message, so the web
 * cases use that shape and pass the Web Bluetooth message table the wasmJs hook uses.
 */
class BleFailureClassifierTest {

    private fun webRejection(message: String) = IllegalStateException(message)

    private val webMessages: (Throwable) -> TransportFailureKind? = {
        it.message?.let(::webBluetoothMessageFailureKind)
    }

    private fun Throwable.classifyOnWeb(setupHint: Throwable? = null) = classifyBleFailureWith(setupHint, webMessages)

    // Kable wraps a rejected startNotifications() in an IOException named after the step; any wrapper type works here.
    private fun refusedSubscription() =
        RuntimeException("Failed to start notification", webRejection("GATT Error Unknown."))

    @Test
    fun `a web connect rejected as no longer in range reads as out of range`() {
        val failure =
            BleConnectFailedException(
                "Failed to connect",
                webRejection("Bluetooth Device is no longer in range."),
            )
        assertEquals(TransportFailureKind.OutOfRange, failure.classifyOnWeb().kind)
    }

    @Test
    fun `a web connection attempt failure reads as could not connect`() {
        val failure =
            BleConnectFailedException(
                "Failed to connect",
                webRejection("Connection Error: Connection attempt failed."),
            )
        val classified = failure.classifyOnWeb()
        assertEquals(TransportFailureKind.ConnectFailed, classified.kind)
        assertEquals("Connection Error: Connection attempt failed.", classified.detail)
    }

    @Test
    fun `a refused subscription reads as rejected rather than definitely needing pairing`() {
        assertEquals(TransportFailureKind.ConnectionRejected, refusedSubscription().classifyOnWeb().kind)
    }

    @Test
    fun `a web not paired error reads as pairing needed`() {
        assertEquals(
            TransportFailureKind.PairingRequired,
            webRejection("GATT Error: Not paired.").classifyOnWeb().kind,
        )
    }

    @Test
    fun `a link lost during setup after a refused subscription reads as rejected`() {
        val lost = NotConnectedException("Connection lost during BLE profile execution")
        assertEquals(
            TransportFailureKind.ConnectionRejected,
            lost.classifyOnWeb(setupHint = refusedSubscription()).kind,
        )
    }

    @Test
    fun `a subscription timeout after a refused subscription reads as rejected`() {
        val timeout = RuntimeException("Timed out waiting for FROMNUM subscription readiness")
        assertEquals(
            TransportFailureKind.ConnectionRejected,
            timeout.classifyOnWeb(setupHint = refusedSubscription()).kind,
        )
    }

    @Test
    fun `a link lost during setup with no hint reads as link lost`() {
        val lost = NotConnectedException("Connection lost during BLE profile execution")
        assertEquals(TransportFailureKind.LinkLost, lost.classifyBleFailure().kind)
    }

    @Test
    fun `a hint that is not about pairing does not override a lost link`() {
        val lost = NotConnectedException("Connection lost during BLE profile execution")
        val hint = webRejection("GATT Server is disconnected. Cannot perform GATT operations.")
        assertEquals(TransportFailureKind.LinkLost, lost.classifyOnWeb(setupHint = hint).kind)
    }

    @Test
    fun `a hint never overrides a failure that already names its cause`() {
        val notFound = BleDeviceNotFoundException("Device not found at address ...")
        assertEquals(
            TransportFailureKind.OutOfRange,
            notFound.classifyOnWeb(setupHint = refusedSubscription()).kind,
        )
    }

    @Test
    fun `a failed bond reads as pairing failed`() {
        val failure = BlePairingFailedException(Exception("Bond failed or rejected"))
        assertEquals(TransportFailureKind.PairingFailed, failure.classifyBleFailure().kind)
    }

    @Test
    fun `a web authentication failure reads as pairing failed`() {
        val failure = BleConnectFailedException("Failed", webRejection("Connection Error: Authentication failed."))
        assertEquals(TransportFailureKind.PairingFailed, failure.classifyOnWeb().kind)
    }

    @Test
    fun `android insufficient authentication and encryption read as pairing needed`() {
        listOf(5, 15).forEach { status ->
            assertEquals(
                TransportFailureKind.PairingRequired,
                GattStatusException(message = "observe", status = status).classifyBleFailure().kind,
                "status $status",
            )
        }
    }

    @Test
    fun `android GATT 133 at connect reads as could not connect`() {
        val failure = BleConnectFailedException("Failed", GattStatusException(message = "connect", status = 133))
        assertEquals(TransportFailureKind.ConnectFailed, failure.classifyBleFailure().kind)
    }

    @Test
    fun `android supervision timeout reads as out of range`() {
        val failure = GattStatusException(message = "link", status = 8)
        assertEquals(TransportFailureKind.OutOfRange, failure.classifyBleFailure().kind)
    }

    @Test
    fun `android link terminations read as link lost`() {
        listOf(19, 22, 34).forEach { status ->
            assertEquals(
                TransportFailureKind.LinkLost,
                GattStatusException(message = "link", status = status).classifyBleFailure().kind,
                "status $status",
            )
        }
    }

    @Test
    fun `an unrecognised failure is unknown and keeps its message as detail`() {
        val classified = RuntimeException("boom").classifyBleFailure()
        assertEquals(TransportFailureKind.Unknown, classified.kind)
        assertEquals("boom", classified.detail)
    }
}
