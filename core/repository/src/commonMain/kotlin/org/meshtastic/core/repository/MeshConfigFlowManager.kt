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

import org.meshtastic.proto.FileInfo

/** Interface for managing the configuration flow, including local node info and metadata. */
interface MeshConfigFlowManager {
    /**
     * Handles a [FileInfo] packet received during STATE_SEND_FILEMANIFEST.
     *
     * Each packet describes one file available on the device. Accumulated into [RadioConfigRepository.fileManifestFlow]
     * and cleared at the start of each new handshake.
     */
    fun handleFileInfo(info: FileInfo, session: RadioSessionContext): Boolean

    /**
     * Re-requests the node database on the live transport after a config change the firmware applied without a reboot,
     * so per-node flags it only reports during the handshake are re-delivered. Leaves config, channels and the
     * connection state untouched. A request during a node database download runs again once it completes. Returns false
     * when the current SDK session cannot admit the refresh.
     */
    fun refreshNodeInfo(): Boolean
}
