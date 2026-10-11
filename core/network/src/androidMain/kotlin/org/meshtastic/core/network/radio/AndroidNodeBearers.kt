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

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single
import org.meshtastic.node.BytesStore
import org.meshtastic.node.android.AndroidBearerFactory
import org.meshtastic.node.android.androidHostState
import org.meshtastic.node.host.BearerFactory
import org.meshtastic.node.host.HostState
import org.meshtastic.node.store.file.AtomicFileStore

/** The Android half of the in-process node: Context-bound bearers, and the node's files under no-backup storage. */
@Single(binds = [NodeBearers::class])
class AndroidNodeBearers(context: Context) : NodeBearers {
    private val app: Context = context.applicationContext
    private val nodeDir: File
        get() = File(app.noBackupFilesDir, NODE_DIR)

    override val hostSignals: StateFlow<HostState> = androidHostState(app)

    override fun factory(signals: StateFlow<HostState>): BearerFactory = AndroidBearerFactory(app, signals)

    override fun storesDir(): String = nodeDir.path

    override fun hostBearerStore(): BytesStore = AtomicFileStore(File(nodeDir, HOST_BEARERS_FILE).path)

    private companion object {
        const val NODE_DIR = "node"
        const val HOST_BEARERS_FILE = "host-bearers.bin"
    }
}
