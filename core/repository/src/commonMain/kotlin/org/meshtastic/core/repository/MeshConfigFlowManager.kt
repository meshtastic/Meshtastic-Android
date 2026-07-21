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

import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo

/** Interface for managing the configuration flow, including local node info and metadata. */
interface MeshConfigFlowManager {
    /** Handles local node information admitted by [session]. */
    fun handleMyInfo(myInfo: MyNodeInfo, session: RadioSessionContext)

    /** Handles received local device metadata admitted by [session]. */
    fun handleLocalMetadata(metadata: DeviceMetadata, session: RadioSessionContext): Boolean

    /** Handles received node information admitted by [session]. */
    fun handleNodeInfo(info: NodeInfo, session: RadioSessionContext): Boolean

    /**
     * Handles a [FileInfo] packet received during STATE_SEND_FILEMANIFEST.
     *
     * Each packet describes one file available on the device. Accumulated into [RadioConfigRepository.fileManifestFlow]
     * and cleared at the start of each new handshake.
     */
    fun handleFileInfo(info: FileInfo, session: RadioSessionContext): Boolean

    /** Returns the number of nodes received in the current stage. */
    val newNodeCount: Int

    /** Handles the completion of a configuration stage admitted by [session]. */
    fun handleConfigComplete(configCompleteId: Int, session: RadioSessionContext): Boolean

    /** Triggers a request for the full device configuration when [session] still owns admission. */
    fun triggerWantConfig(session: RadioSessionContext): Boolean
}
