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

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.ConnectionEpochs
import org.meshtastic.core.model.ConnectionLifecycle
import org.meshtastic.core.model.ConnectionState

/**
 * Read-only provider of the canonical app-level connection state.
 *
 * Inject this interface in ViewModels and feature modules that only need to **observe** connection state — never write
 * it. This enforces the single-writer contract (only [MeshConnectionManager] may mutate state via
 * [ServiceRepository.setConnectionState]).
 *
 * @see ServiceRepository for the full read/write interface
 */
interface ConnectionStateProvider {
    /**
     * Atomically correlated canonical state and lifecycle evidence.
     *
     * Lifecycle-sensitive code must observe this flow rather than combining independent reads from [connectionState]
     * and [connectionEpochs].
     */
    val connectionLifecycle: StateFlow<ConnectionLifecycle>

    /**
     * Canonical app-level connection state.
     *
     * This is the **single source of truth** for connection status across the entire application.
     *
     * @see ServiceRepository.connectionState
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Convenience view of monotonic departures and completed handshakes. Use [connectionLifecycle] when the counters
     * must be correlated with a specific state, because independent StateFlow collections can observe different
     * versions.
     */
    val connectionEpochs: StateFlow<ConnectionEpochs>
}
