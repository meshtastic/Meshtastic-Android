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
package org.meshtastic.feature.connections.ui.components

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.ble.classifyBleFailure
import org.meshtastic.core.model.TransportFailureKind
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connected_sleeping
import org.meshtastic.core.resources.connection_failed
import org.meshtastic.core.resources.connection_failure_bluetooth_or_location_unavailable
import org.meshtastic.core.resources.connection_failure_bluetooth_unavailable
import org.meshtastic.core.resources.connection_failure_connect_failed
import org.meshtastic.core.resources.connection_failure_connection_rejected
import org.meshtastic.core.resources.connection_failure_link_lost
import org.meshtastic.core.resources.connection_failure_out_of_range
import org.meshtastic.core.resources.connection_failure_pairing_failed
import org.meshtastic.core.resources.connection_failure_pairing_required
import org.meshtastic.core.resources.connection_failure_unknown
import org.meshtastic.core.resources.connection_phase_connecting
import org.meshtastic.core.resources.connection_phase_handshake_config
import org.meshtastic.core.resources.connection_phase_handshake_nodes
import org.meshtastic.core.resources.disconnecting
import org.meshtastic.core.resources.node_restarting
import org.meshtastic.core.resources.not_connected
import org.meshtastic.core.resources.reconnecting
import org.meshtastic.sdk.ConfigPhase
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.MeshtasticException
import kotlin.time.Instant

/**
 * What the connecting card says under the device for one [ConnectionState].
 *
 * @property status what the connection is doing, shown unless [retry] stands in for it.
 * @property retry the countdown to the next attempt while the client waits out a backoff.
 * @property failure why the last attempt failed, in plain words. The platform's own message stays in the log.
 * @property canConnect whether the client stopped trying, so only the user connecting again brings it back.
 */
data class ConnectionStatusDetail(
    val status: StringResource,
    val retry: RetryCountdown?,
    val failure: StringResource?,
    val canConnect: Boolean = false,
)

/** [seconds] until attempt number [attempt] starts. */
data class RetryCountdown(val seconds: Int, val attempt: Int)

private const val MILLIS_PER_SECOND = 1_000L

/**
 * Resolves this state at [now].
 *
 * @param restartExpected whether the node is applying a change that reboots it, so a drop reads as a restart
 * @param isBluetooth whether the link is Bluetooth, whose failures name a cause the user can act on
 * @param bluetoothNeedsLocation whether a Bluetooth scan here also needs location services, so an unavailable adapter
 *   names both
 */
fun ConnectionState.toConnectionStatusDetail(
    now: Instant,
    restartExpected: Boolean = false,
    isBluetooth: Boolean = false,
    bluetoothNeedsLocation: Boolean = false,
): ConnectionStatusDetail = when (this) {
    ConnectionState.Connected -> ConnectionStatusDetail(Res.string.connected, retry = null, failure = null)

    is ConnectionState.Connecting ->
        ConnectionStatusDetail(
            if (restartExpected) Res.string.node_restarting else Res.string.connection_phase_connecting,
            retry = null,
            failure = null,
        )

    is ConnectionState.Configuring ->
        ConnectionStatusDetail(
            when (phase) {
                ConfigPhase.Stage1,
                ConfigPhase.Settling,
                -> Res.string.connection_phase_handshake_config

                ConfigPhase.Stage2 -> Res.string.connection_phase_handshake_nodes
            },
            retry = null,
            failure = null,
        )

    is ConnectionState.Reconnecting ->
        ConnectionStatusDetail(
            if (restartExpected) Res.string.node_restarting else Res.string.reconnecting,
            retry =
            retryAt?.let { at ->
                val remaining = (at - now).inWholeMilliseconds.coerceAtLeast(0L)
                RetryCountdown(((remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt(), attempt)
            },
            failure = if (restartExpected) null else cause.failureLabel(isBluetooth, bluetoothNeedsLocation),
        )

    is ConnectionState.DeviceSleeping ->
        ConnectionStatusDetail(Res.string.connected_sleeping, retry = null, failure = null)

    ConnectionState.Disconnecting -> ConnectionStatusDetail(Res.string.disconnecting, retry = null, failure = null)

    is ConnectionState.Failed ->
        ConnectionStatusDetail(
            Res.string.connection_failed,
            retry = null,
            failure = cause.failureLabel(isBluetooth, bluetoothNeedsLocation),
            canConnect = true,
        )

    ConnectionState.Disconnected ->
        ConnectionStatusDetail(
            if (restartExpected) Res.string.node_restarting else Res.string.not_connected,
            retry = null,
            failure = null,
        )
}

/** A Bluetooth failure names its cause; other links say only that the connect failed. */
private fun MeshtasticException.failureLabel(isBluetooth: Boolean, bluetoothNeedsLocation: Boolean): StringResource =
    when {
        this is MeshtasticException.HandshakeTimeout -> Res.string.connection_failure_unknown
        isBluetooth -> classifyBleFailure().kind.label(bluetoothNeedsLocation)
        else -> Res.string.connection_failure_connect_failed
    }

private fun TransportFailureKind.label(bluetoothNeedsLocation: Boolean): StringResource = when (this) {
    TransportFailureKind.OutOfRange -> Res.string.connection_failure_out_of_range
    TransportFailureKind.ConnectFailed -> Res.string.connection_failure_connect_failed
    TransportFailureKind.PairingRequired -> Res.string.connection_failure_pairing_required
    TransportFailureKind.ConnectionRejected -> Res.string.connection_failure_connection_rejected
    TransportFailureKind.PairingFailed -> Res.string.connection_failure_pairing_failed
    TransportFailureKind.LinkLost -> Res.string.connection_failure_link_lost
    TransportFailureKind.BluetoothUnavailable -> bluetoothUnavailableLabel(bluetoothNeedsLocation)
    TransportFailureKind.Unknown -> Res.string.connection_failure_unknown
}

private fun bluetoothUnavailableLabel(bluetoothNeedsLocation: Boolean): StringResource = if (bluetoothNeedsLocation) {
    Res.string.connection_failure_bluetooth_or_location_unavailable
} else {
    Res.string.connection_failure_bluetooth_unavailable
}
