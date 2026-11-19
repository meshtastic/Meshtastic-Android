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
package org.meshtastic.feature.connections.ui

import org.meshtastic.core.model.TransportFailure
import org.meshtastic.core.model.TransportFailureKind
import org.meshtastic.core.model.TransportPhase
import org.meshtastic.core.model.TransportStatus
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connection_failure_bluetooth_or_location_unavailable
import org.meshtastic.core.resources.connection_failure_bluetooth_unavailable
import org.meshtastic.core.resources.connection_failure_connect_failed
import org.meshtastic.core.resources.connection_failure_connection_rejected
import org.meshtastic.core.resources.connection_failure_out_of_range
import org.meshtastic.core.resources.connection_failure_pairing_required
import org.meshtastic.core.resources.connection_failure_unknown
import org.meshtastic.core.resources.connection_phase_handshake_nodes
import org.meshtastic.core.resources.connection_phase_subscribing
import org.meshtastic.feature.connections.ui.components.RetryCountdown
import org.meshtastic.feature.connections.ui.components.toConnectionStatusDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionStatusDetailTest {

    @Test
    fun `no status and a connected link add nothing`() {
        assertNull(null.toConnectionStatusDetail(nowMillis = 0L))
        assertNull(TransportStatus(TransportPhase.Connected).toConnectionStatusDetail(nowMillis = 0L))
    }

    @Test
    fun `a phase in progress names the phase and the last failure`() {
        val detail =
            TransportStatus(
                phase = TransportPhase.Subscribing,
                lastFailure = TransportFailure(TransportFailureKind.PairingRequired, "GATT Error Unknown."),
            )
                .toConnectionStatusDetail(nowMillis = 0L)

        assertEquals(Res.string.connection_phase_subscribing, detail?.phase)
        assertEquals(Res.string.connection_failure_pairing_required, detail?.failure)
        assertNull(detail?.retry)
    }

    @Test
    fun `waiting to retry counts whole seconds up to the next attempt`() {
        val status =
            TransportStatus(
                phase = TransportPhase.WaitingToRetry,
                lastFailure = TransportFailure(TransportFailureKind.OutOfRange),
                nextAttempt = 3,
                retryAtMillis = 20_000L,
            )

        val detail = status.toConnectionStatusDetail(nowMillis = 7_500L)
        assertNull(detail?.phase)
        assertEquals(RetryCountdown(seconds = 13, attempt = 3), detail?.retry)
        assertEquals(Res.string.connection_failure_out_of_range, detail?.failure)

        assertEquals(RetryCountdown(seconds = 0, attempt = 3), status.toConnectionStatusDetail(30_000L)?.retry)
    }

    @Test
    fun `an unclassified failure reads as connection failed`() {
        val detail =
            TransportStatus(
                phase = TransportPhase.WaitingToRetry,
                lastFailure = TransportFailure(TransportFailureKind.Unknown, "Timed out waiting for FROMNUM"),
                nextAttempt = 2,
                retryAtMillis = 5_000L,
            )
                .toConnectionStatusDetail(nowMillis = 0L)

        assertEquals(Res.string.connection_failure_unknown, detail?.failure)
    }

    @Test
    fun `Bluetooth unavailable names location services only where scanning needs them`() {
        val status =
            TransportStatus(TransportPhase.Connecting, TransportFailure(TransportFailureKind.BluetoothUnavailable))
        assertEquals(
            Res.string.connection_failure_bluetooth_unavailable,
            status.toConnectionStatusDetail(nowMillis = 0L)?.failure,
        )
        assertEquals(
            Res.string.connection_failure_bluetooth_or_location_unavailable,
            status.toConnectionStatusDetail(nowMillis = 0L, bluetoothNeedsLocation = true)?.failure,
        )
    }

    @Test
    fun `a rejection the platform does not explain maps to may need pairing`() {
        val detail =
            TransportStatus(TransportPhase.Subscribing, TransportFailure(TransportFailureKind.ConnectionRejected))
                .toConnectionStatusDetail(nowMillis = 0L)
        assertEquals(Res.string.connection_failure_connection_rejected, detail?.failure)
    }

    @Test
    fun `a handshake phase carries no transport failure`() {
        val detail = TransportStatus(TransportPhase.HandshakeNodes).toConnectionStatusDetail(nowMillis = 0L)
        assertEquals(Res.string.connection_phase_handshake_nodes, detail?.phase)
        assertNull(detail?.failure)
    }

    @Test
    fun `a connect failure maps to could not connect`() {
        val detail =
            TransportStatus(TransportPhase.Connecting, TransportFailure(TransportFailureKind.ConnectFailed))
                .toConnectionStatusDetail(nowMillis = 0L)
        assertEquals(Res.string.connection_failure_connect_failed, detail?.failure)
    }
}
