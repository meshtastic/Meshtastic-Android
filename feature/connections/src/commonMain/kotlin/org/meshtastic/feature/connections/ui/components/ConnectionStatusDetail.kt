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
import org.meshtastic.core.model.TransportFailureKind
import org.meshtastic.core.model.TransportPhase
import org.meshtastic.core.model.TransportStatus
import org.meshtastic.core.resources.Res
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
import org.meshtastic.core.resources.connection_phase_discovering_services
import org.meshtastic.core.resources.connection_phase_finding_device
import org.meshtastic.core.resources.connection_phase_handshake_config
import org.meshtastic.core.resources.connection_phase_handshake_nodes
import org.meshtastic.core.resources.connection_phase_pairing
import org.meshtastic.core.resources.connection_phase_subscribing

/**
 * What the connecting card says under the device for one [TransportStatus].
 *
 * @property phase the step in progress, or null while [retry] stands in for it.
 * @property retry the countdown to the next attempt while waiting to retry.
 * @property failure why the last attempt failed, in plain words. The platform's own message stays in the log.
 */
data class ConnectionStatusDetail(val phase: StringResource?, val retry: RetryCountdown?, val failure: StringResource?)

/** [seconds] until attempt number [attempt] starts. */
data class RetryCountdown(val seconds: Int, val attempt: Int)

private const val MILLIS_PER_SECOND = 1_000L

/**
 * Resolves this status at [nowMillis]; null when there is nothing to add, including once the link is up.
 *
 * @param bluetoothNeedsLocation whether a Bluetooth scan here also needs location services, so an unavailable adapter
 *   names both
 */
fun TransportStatus?.toConnectionStatusDetail(
    nowMillis: Long,
    bluetoothNeedsLocation: Boolean = false,
): ConnectionStatusDetail? {
    if (this == null || phase == TransportPhase.Connected) return null
    val retry =
        retryAtMillis
            ?.takeIf { phase == TransportPhase.WaitingToRetry }
            ?.let { retryAt ->
                val remaining = (retryAt - nowMillis).coerceAtLeast(0L)
                RetryCountdown(
                    seconds = ((remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt(),
                    attempt = nextAttempt,
                )
            }
    return ConnectionStatusDetail(
        phase = phase.label(),
        retry = retry,
        failure = lastFailure?.kind?.label(bluetoothNeedsLocation),
    )
}

private fun TransportPhase.label(): StringResource? = when (this) {
    TransportPhase.FindingDevice -> Res.string.connection_phase_finding_device

    TransportPhase.Pairing -> Res.string.connection_phase_pairing

    TransportPhase.Connecting -> Res.string.connection_phase_connecting

    TransportPhase.DiscoveringServices -> Res.string.connection_phase_discovering_services

    TransportPhase.Subscribing -> Res.string.connection_phase_subscribing

    TransportPhase.HandshakeConfig -> Res.string.connection_phase_handshake_config

    TransportPhase.HandshakeNodes -> Res.string.connection_phase_handshake_nodes

    TransportPhase.Connected,
    TransportPhase.WaitingToRetry,
    -> null
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
