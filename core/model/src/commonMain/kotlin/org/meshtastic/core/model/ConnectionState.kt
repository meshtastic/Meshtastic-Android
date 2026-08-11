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
package org.meshtastic.core.model

sealed interface ConnectionState {
    /** We are disconnected from the device, and we should be trying to reconnect. */
    data object Disconnected : ConnectionState

    /** We are currently attempting to connect to the device. */
    data object Connecting : ConnectionState

    /** We are connected to the device and communicating normally. */
    data object Connected : ConnectionState

    /** The device is in a light sleep state, and we are waiting for it to wake up and reconnect to us. */
    data object DeviceSleep : ConnectionState
}

/**
 * One atomically published view of canonical connection state and its lifecycle evidence.
 *
 * Consumers that need to correlate a state with departure or handshake counters must observe this snapshot instead of
 * reading [ConnectionState] and [ConnectionEpochs] flows independently.
 */
data class ConnectionLifecycle(
    /**
     * Monotonically increasing identity of this snapshot. Consumers capture it before a long operation and reject the
     * result if the current value differs.
     */
    val version: Long = 0,
    val state: ConnectionState = ConnectionState.Disconnected,
    val epochs: ConnectionEpochs = ConnectionEpochs(),
)

/**
 * Monotonic counters for connection lifecycle events that cannot be inferred reliably from transient [ConnectionState]
 * values. A fast disconnect/reconnect may be observed only as the final state, while these counters retain both
 * lifecycle boundaries.
 *
 * @property departures Number of transitions away from [ConnectionState.Connected].
 * @property completedHandshakes Number of transitions into [ConnectionState.Connected].
 * @property handshakesAtLastDeparture Completed-handshake count captured at the most recent departure. This preserves
 *   event ordering when a fast departure/reconnect pair is conflated into one observed state-flow value.
 * @property lastDepartureState State entered by the most recent departure. Consumers that react after a fast reconnect
 *   can use this event evidence instead of rereading the already-advanced live connection state.
 */
data class ConnectionEpochs(
    val departures: Long = 0,
    val completedHandshakes: Long = 0,
    val handshakesAtLastDeparture: Long = 0,
    val lastDepartureState: ConnectionState? = null,
) {
    /** Returns the counters after applying one canonical connection-state transition. */
    fun advance(previous: ConnectionState, current: ConnectionState): ConnectionEpochs = when {
        previous is ConnectionState.Connected && current !is ConnectionState.Connected ->
            copy(
                departures = departures + 1,
                handshakesAtLastDeparture = completedHandshakes,
                lastDepartureState = current,
            )

        previous !is ConnectionState.Connected && current is ConnectionState.Connected ->
            copy(completedHandshakes = completedHandshakes + 1)

        else -> this
    }
}
