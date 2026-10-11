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
package org.meshtastic.desktop.radio

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.database.desktopDataDir
import org.meshtastic.core.network.radio.NodeBearers
import org.meshtastic.node.BytesStore
import org.meshtastic.node.host.BearerFactory
import org.meshtastic.node.host.HostState
import org.meshtastic.node.host.JvmBearerFactory
import org.meshtastic.node.store.file.AtomicFileStore

/**
 * The desktop half of the in-process node. Each data directory is its own node, so two app instances on separate
 * `MESHTASTIC_DATA_DIR`s are two nodes. The window is always considered visible and the host always in front.
 *
 * Registered in [org.meshtastic.desktop.di.DesktopRuntimeModule]; no `@Single` here, to avoid double registration
 * with the desktop component scan.
 */
class DesktopNodeBearers : NodeBearers {
    private val nodeDir: File
        get() = File(desktopDataDir(), NODE_DIR)

    override val hostSignals: StateFlow<HostState> = MutableStateFlow(HostState())

    override fun factory(signals: StateFlow<HostState>): BearerFactory = JvmBearerFactory(signals)

    override fun storesDir(): String = nodeDir.path

    override fun hostBearerStore(): BytesStore = AtomicFileStore(File(nodeDir, HOST_BEARERS_FILE).path)

    private companion object {
        const val NODE_DIR = "node"
        const val HOST_BEARERS_FILE = "host-bearers.bin"
    }
}
