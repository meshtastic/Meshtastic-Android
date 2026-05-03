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
package org.meshtastic.core.domain.usecase.settings

import org.koin.core.annotation.Single
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.NodeRepository

/**
 * Use case for performing administrative and destructive actions on mesh nodes.
 *
 * This component provides methods for rebooting, shutting down, or resetting nodes within the mesh. It also handles
 * local database synchronization when these actions are performed on the locally connected device.
 */
@Single
open class AdminActionsUseCase
constructor(
    private val radioController: RadioController,
    private val nodeRepository: NodeRepository,
) {
    /**
     * Reboots the radio.
     *
     * @param destNum The node number to reboot.
     * @return The packet ID of the request.
     */
    open suspend fun reboot(destNum: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.reboot(destNum, packetId)
        return packetId
    }

    /**
     * Shuts down the radio.
     *
     * @param destNum The node number to shut down.
     * @return The packet ID of the request.
     */
    open suspend fun shutdown(destNum: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.shutdown(destNum, packetId)
        return packetId
    }

    /**
     * Factory resets the radio.
     *
     * @param destNum The node number to reset.
     * @param isLocal Whether the reset is being performed on the locally connected node.
     * @return The packet ID of the request.
     */
    open suspend fun factoryReset(destNum: Int, isLocal: Boolean): Int {
        val packetId = radioController.getPacketId()
        radioController.factoryReset(destNum, packetId)

        if (isLocal) {
            // If it's the local node, we should also clear the phone's node database as it will be out of sync.
            nodeRepository.clearNodeDB()
        }

        return packetId
    }

    /**
     * Resets the NodeDB on the radio.
     *
     * @param destNum The node number to reset.
     * @param preserveFavorites Whether to keep favorite nodes in the database.
     * @param isLocal Whether the reset is being performed on the locally connected node.
     * @return The packet ID of the request.
     */
    open suspend fun nodedbReset(destNum: Int, preserveFavorites: Boolean, isLocal: Boolean): Int {
        val packetId = radioController.getPacketId()
        radioController.nodedbReset(destNum, packetId, preserveFavorites)

        if (isLocal) {
            // If it's the local node, we should also clear the phone's node database.
            nodeRepository.clearNodeDB(preserveFavorites)
        }

        return packetId
    }
}
