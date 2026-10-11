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
package org.meshtastic.core.network.radio

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.node.BytesStore
import org.meshtastic.node.PositionSource
import org.meshtastic.node.host.BearerFactory
import org.meshtastic.node.host.HostState

/**
 * What a platform supplies for the in-process node: its bearer factory, its lifecycle signals and where the node's
 * files live. The Context never crosses into commonMain; Android and desktop each bind one of these.
 */
interface NodeBearers {
    /** Foreground, screen and power state the node's scan policy follows. */
    val hostSignals: StateFlow<HostState>

    /** The bearers this platform can build. Called once per transport start. */
    fun factory(signals: StateFlow<HostState>): BearerFactory

    /** The directory holding the node's identity, settings and node database, as an absolute path. */
    fun storesDir(): String

    /** Where the node remembers which host bearers (Wi-Fi Aware, audio, video) the user switched on. */
    fun hostBearerStore(): BytesStore

    /** The device's position for the node's beacon, or null when the platform offers none. */
    val positionSource: PositionSource?
        get() = null
}
