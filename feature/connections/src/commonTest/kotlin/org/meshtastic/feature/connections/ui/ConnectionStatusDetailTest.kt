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

import com.juul.kable.GattStatusException
import org.meshtastic.core.ble.BluetoothUnsupportedException
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connection_failed
import org.meshtastic.core.resources.connection_failure_bluetooth_or_location_unavailable
import org.meshtastic.core.resources.connection_failure_bluetooth_unavailable
import org.meshtastic.core.resources.connection_failure_connect_failed
import org.meshtastic.core.resources.connection_failure_out_of_range
import org.meshtastic.core.resources.connection_phase_handshake_config
import org.meshtastic.core.resources.connection_phase_handshake_nodes
import org.meshtastic.core.resources.node_restarting
import org.meshtastic.core.resources.reconnecting
import org.meshtastic.feature.connections.ui.components.RetryCountdown
import org.meshtastic.feature.connections.ui.components.toConnectionStatusDetail
import org.meshtastic.sdk.ConfigPhase
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.MeshtasticException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ConnectionStatusDetailTest {

    private val now = Instant.fromEpochMilliseconds(7_500L)

    /** A Bluetooth link the radio dropped on a supervision timeout, GATT status 0x08. */
    private val outOfRange = MeshtasticException.Transport("link", GattStatusException(message = "link", status = 8))

    @Test
    fun `a reconnect waiting out its backoff counts whole seconds up to the attempt and names the failure`() {
        val state =
            ConnectionState.Reconnecting(outOfRange, attempt = 3, retryAt = Instant.fromEpochMilliseconds(20_000))

        val detail = state.toConnectionStatusDetail(now, isBluetooth = true)

        assertEquals(Res.string.reconnecting, detail.status)
        assertEquals(RetryCountdown(seconds = 13, attempt = 3), detail.retry)
        assertEquals(Res.string.connection_failure_out_of_range, detail.failure)
        assertFalse(detail.canConnect)
        val late = state.toConnectionStatusDetail(Instant.fromEpochMilliseconds(30_000), isBluetooth = true)
        assertEquals(RetryCountdown(seconds = 0, attempt = 3), late.retry)
    }

    @Test
    fun `an attempt in flight shows no countdown`() {
        val state = ConnectionState.Reconnecting(outOfRange, attempt = 2, retryAt = null)

        assertNull(state.toConnectionStatusDetail(now, isBluetooth = true).retry)
    }

    @Test
    fun `only a Bluetooth failure names its cause`() {
        val state = ConnectionState.Reconnecting(outOfRange, attempt = 1)

        assertEquals(
            Res.string.connection_failure_connect_failed,
            state.toConnectionStatusDetail(now, isBluetooth = false).failure,
        )
    }

    @Test
    fun `Bluetooth unavailable names location services only where scanning needs them`() {
        val off =
            ConnectionState.Reconnecting(
                MeshtasticException.Transport("off", BluetoothUnsupportedException(IllegalStateException("off"))),
                attempt = 1,
            )

        assertEquals(
            Res.string.connection_failure_bluetooth_unavailable,
            off.toConnectionStatusDetail(now, isBluetooth = true).failure,
        )
        assertEquals(
            Res.string.connection_failure_bluetooth_or_location_unavailable,
            off.toConnectionStatusDetail(now, isBluetooth = true, bluetoothNeedsLocation = true).failure,
        )
    }

    @Test
    fun `a drop during an expected restart reads as the restart with no failure`() {
        val state = ConnectionState.Reconnecting(outOfRange, attempt = 1)

        val detail = state.toConnectionStatusDetail(now, restartExpected = true, isBluetooth = true)

        assertEquals(Res.string.node_restarting, detail.status)
        assertNull(detail.failure)
    }

    @Test
    fun `a client that stopped trying names the cause and offers to connect`() {
        val detail = ConnectionState.Failed(outOfRange).toConnectionStatusDetail(now, isBluetooth = true)

        assertEquals(Res.string.connection_failed, detail.status)
        assertEquals(Res.string.connection_failure_out_of_range, detail.failure)
        assertTrue(detail.canConnect)
    }

    @Test
    fun `the handshake names its stage`() {
        assertEquals(
            Res.string.connection_phase_handshake_config,
            ConnectionState.Configuring(ConfigPhase.Stage1, 0.2f).toConnectionStatusDetail(now).status,
        )
        assertEquals(
            Res.string.connection_phase_handshake_nodes,
            ConnectionState.Configuring(ConfigPhase.Stage2, 0.6f).toConnectionStatusDetail(now).status,
        )
    }
}
