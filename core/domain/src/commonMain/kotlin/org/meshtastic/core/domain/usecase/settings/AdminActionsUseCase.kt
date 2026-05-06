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
 * Methods suspend until the device acknowledges. On failure, they propagate
 * [org.meshtastic.core.model.AdminException].
 */
@Single
open class AdminActionsUseCase
constructor(
    private val radioController: RadioController,
    private val nodeRepository: NodeRepository,
) {
    /** Reboot the target node. */
    open suspend fun reboot(destNum: Int) {
        radioController.reboot(destNum)
    }

    /** Shut down the target node. */
    open suspend fun shutdown(destNum: Int) {
        radioController.shutdown(destNum)
    }

    /**
     * Factory reset the target node.
     *
     * @param destNum The node number to reset.
     * @param isLocal Whether the reset is being performed on the locally connected node.
     */
    open suspend fun factoryReset(destNum: Int, isLocal: Boolean) {
        radioController.factoryReset(destNum)
        if (isLocal) {
            nodeRepository.clearNodeDB()
        }
    }

    /**
     * Reset the NodeDB on the target node.
     *
     * @param destNum The node number to reset.
     * @param preserveFavorites Whether to keep favorite nodes in the database.
     * @param isLocal Whether the reset is being performed on the locally connected node.
     */
    open suspend fun nodedbReset(destNum: Int, preserveFavorites: Boolean, isLocal: Boolean) {
        radioController.nodedbReset(destNum, preserveFavorites)
        if (isLocal) {
            nodeRepository.clearNodeDB(preserveFavorites)
        }
    }
}
