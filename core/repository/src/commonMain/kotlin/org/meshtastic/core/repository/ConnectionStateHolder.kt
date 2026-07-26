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

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.meshtastic.core.model.ConnectionEpochs
import org.meshtastic.core.model.ConnectionLifecycle
import org.meshtastic.core.model.ConnectionState

/**
 * Owns canonical connection state and its lifecycle epochs as one lock-free transition.
 *
 * The canonical [ConnectionLifecycle] flow is updated with compare-and-set, so concurrent writers cannot lose or
 * double-count lifecycle edges. One publisher then mirrors its newest value to the legacy state and epoch convenience
 * flows. Lifecycle-sensitive consumers therefore never have to correlate separate publications.
 */
class ConnectionStateHolder(
    initialState: ConnectionState = ConnectionState.Disconnected,
    initialEpochs: ConnectionEpochs = ConnectionEpochs(),
) : ConnectionStateProvider {
    private val publicationInProgress = atomic(false)
    private val publishedVersion = atomic(0L)

    private val mutableConnectionLifecycle =
        MutableStateFlow(ConnectionLifecycle(version = 0, state = initialState, epochs = initialEpochs))
    private val mutableConnectionState = MutableStateFlow(initialState)
    private val mutableConnectionEpochs = MutableStateFlow(initialEpochs)

    override val connectionLifecycle: StateFlow<ConnectionLifecycle> = mutableConnectionLifecycle.asStateFlow()
    override val connectionState: StateFlow<ConnectionState> = mutableConnectionState.asStateFlow()
    override val connectionEpochs: StateFlow<ConnectionEpochs> = mutableConnectionEpochs.asStateFlow()

    /**
     * Applies [newState] and advances epochs exactly once when the state changes.
     *
     * [connectionLifecycle] is authoritative on return. A concurrent publisher may update the compatibility
     * [connectionState] and [connectionEpochs] mirrors immediately afterward, so they are not read-after-write
     * consistent for the caller under contention.
     */
    fun setConnectionState(newState: ConnectionState) {
        while (true) {
            val current = mutableConnectionLifecycle.value
            if (current.state == newState) return

            val next =
                ConnectionLifecycle(
                    version = current.version + 1,
                    state = newState,
                    epochs = current.epochs.advance(current.state, newState),
                )
            if (mutableConnectionLifecycle.compareAndSet(current, next)) {
                publishCompatibilityViews()
                return
            }
        }
    }

    /**
     * Restores a known baseline, primarily for reusable test fakes. The version remains monotonic even when state and
     * epochs return to earlier values, so a reader cannot mistake a reset snapshot for an older publication.
     */
    fun reset(state: ConnectionState = ConnectionState.Disconnected, epochs: ConnectionEpochs = ConnectionEpochs()) {
        while (true) {
            val current = mutableConnectionLifecycle.value
            val next = ConnectionLifecycle(version = current.version + 1, state = state, epochs = epochs)
            if (mutableConnectionLifecycle.compareAndSet(current, next)) {
                publishCompatibilityViews()
                return
            }
        }
    }

    private fun publishCompatibilityViews() {
        while (true) {
            if (!publicationInProgress.compareAndSet(expect = false, update = true)) return
            try {
                do {
                    val snapshot = mutableConnectionLifecycle.value
                    // Epochs first: state collectors that then read connectionEpochs must not observe lagging counters.
                    mutableConnectionEpochs.value = snapshot.epochs
                    mutableConnectionState.value = snapshot.state
                    publishedVersion.value = snapshot.version
                } while (publishedVersion.value != mutableConnectionLifecycle.value.version)
            } finally {
                publicationInProgress.value = false
            }

            // An updater can commit after the final loop check, lose the publisher race, and return before this owner
            // releases the flag. Sequentially consistent atomics make that lifecycle write visible here. The
            // post-release recheck ensures its compatibility projection cannot be stranded.
            if (publishedVersion.value == mutableConnectionLifecycle.value.version) return
        }
    }
}
