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
package org.meshtastic.core.repository

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.meshtastic.core.model.ConnectionEpochs
import org.meshtastic.core.model.ConnectionLifecycle
import org.meshtastic.core.model.ConnectionState

/** Owns canonical connection state, epochs, and compatibility projections under one publication lock. */
class ConnectionStateHolder(
    initialState: ConnectionState = ConnectionState.Disconnected,
    initialEpochs: ConnectionEpochs = ConnectionEpochs(),
) : ConnectionStateProvider {
    init {
        require(initialEpochs.isSelfConsistent()) { "initialEpochs must satisfy connection epoch invariants" }
    }

    private val publicationLock = SynchronizedObject()

    private val mutableConnectionLifecycle =
        MutableStateFlow(ConnectionLifecycle(version = 0, state = initialState, epochs = initialEpochs))
    private val mutableConnectionState = MutableStateFlow(initialState)
    private val mutableConnectionEpochs = MutableStateFlow(initialEpochs)

    override val connectionLifecycle: StateFlow<ConnectionLifecycle> = mutableConnectionLifecycle.asStateFlow()
    override val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()
    override val connectionEpochs: StateFlow<ConnectionEpochs> = mutableConnectionEpochs.asStateFlow()

    /** Applies [newState] and advances epochs exactly once when the state changes. */
    fun setConnectionState(newState: ConnectionState) = synchronized(publicationLock) {
        val current = mutableConnectionLifecycle.value
        if (current.state != newState) {
            publish(
                ConnectionLifecycle(
                    version = current.version + 1,
                    state = newState,
                    epochs = current.epochs.advance(current.state, newState),
                ),
            )
        }
    }

    /**
     * Restores a known state, primarily for reusable test fakes. Epoch counters remain monotonic: omitting [epochs]
     * applies the canonical transition into [state], while an explicit value may only advance the counters. The
     * publication version always advances so readers can distinguish the reset from the preceding snapshot.
     */
    fun reset(state: ConnectionState = ConnectionState.Disconnected, epochs: ConnectionEpochs? = null) =
        synchronized(publicationLock) {
            val current = mutableConnectionLifecycle.value
            val minimumEpochs = current.epochs.advance(current.state, state)
            val nextEpochs = epochs ?: minimumEpochs
            val preservesCanonicalDeparture =
                nextEpochs.departures != minimumEpochs.departures ||
                    minimumEpochs.lastDepartureState == null ||
                    nextEpochs.lastDepartureState == minimumEpochs.lastDepartureState
            require(
                nextEpochs.departures >= minimumEpochs.departures &&
                    nextEpochs.completedHandshakes >= minimumEpochs.completedHandshakes &&
                    nextEpochs.handshakesAtLastDeparture >= minimumEpochs.handshakesAtLastDeparture &&
                    nextEpochs.isSelfConsistent() &&
                    preservesCanonicalDeparture,
            ) {
                "reset must not rewind epoch counters or contradict canonical departure evidence"
            }
            publish(ConnectionLifecycle(version = current.version + 1, state = state, epochs = nextEpochs))
        }

    /** Publishes one authoritative snapshot and both legacy mirrors as one non-suspending critical section. */
    private fun publish(next: ConnectionLifecycle) {
        mutableConnectionLifecycle.value = next
        // Epochs before state: state collectors that then read connectionEpochs must not observe lagging counters.
        mutableConnectionEpochs.value = next.epochs
        mutableConnectionState.value = next.state
    }
}

private fun ConnectionEpochs.isSelfConsistent(): Boolean = departures >= 0L &&
    completedHandshakes >= 0L &&
    handshakesAtLastDeparture >= 0L &&
    handshakesAtLastDeparture <= completedHandshakes &&
    (departures > 0L || handshakesAtLastDeparture == 0L) &&
    lastDepartureState !is ConnectionState.Connected &&
    (departures == 0L) == (lastDepartureState == null)
