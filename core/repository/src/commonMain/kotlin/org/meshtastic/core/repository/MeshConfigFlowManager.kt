/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import kotlinx.coroutines.CoroutineScope
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo

/** Interface for managing the configuration flow, including local node info and metadata. */
interface MeshConfigFlowManager {
    /** Starts the manager with the given coroutine scope. */
    fun start(scope: CoroutineScope)

    /** Handles received local node information. */
    fun handleMyInfo(myInfo: MyNodeInfo)

    /** Handles received local device metadata. */
    fun handleLocalMetadata(metadata: DeviceMetadata)

    /** Handles received node information. */
    fun handleNodeInfo(info: NodeInfo)

    /**
     * Handles a batch of node information records delivered in a single [NodeInfoBatch] message.
     *
     * The default implementation simply delegates to [handleNodeInfo] for each item. Implementations should override
     * this with a bulk `addAll` to avoid per-item overhead on large meshes.
     */
    fun handleNodeInfoBatch(items: List<NodeInfo>) {
        items.forEach { handleNodeInfo(it) }
    }

    /**
     * Handles a [FileInfo] packet received during STATE_SEND_FILEMANIFEST.
     *
     * Each packet describes one file available on the device. Accumulated into [RadioConfigRepository.fileManifestFlow]
     * and cleared at the start of each new handshake.
     */
    fun handleFileInfo(info: FileInfo)

    /** Returns the number of nodes received in the current stage. */
    val newNodeCount: Int

    /** Handles the completion of a configuration stage. */
    fun handleConfigComplete(configCompleteId: Int)

    /** Triggers a request for the full device configuration. */
    fun triggerWantConfig()
}
