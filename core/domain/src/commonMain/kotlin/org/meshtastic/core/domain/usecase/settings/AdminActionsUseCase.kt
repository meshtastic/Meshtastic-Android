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
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.onSuccess

/**
 * Use case for performing administrative and destructive actions on mesh nodes.
 *
 * This component provides methods for rebooting, shutting down, or resetting nodes within the mesh. Each suspends until
 * the device answers and returns that answer. A reset of the locally connected node also clears the phone's node
 * database once the device reports it applied.
 */
@Single
open class AdminActionsUseCase
constructor(
    private val radioController: RadioController,
    private val nodeRepository: NodeRepository,
) {
    /** Reboots [destNum]. */
    open suspend fun reboot(destNum: Int): AdminResult<Unit> = radioController.reboot(destNum)

    /** Reboots an nRF52 [destNum] into its DFU bootloader; firmware answers nothing, so success means sent. */
    open suspend fun rebootToDfu(destNum: Int): AdminResult<Unit> = radioController.rebootToDfu(destNum)

    /** Shuts down [destNum]. */
    open suspend fun shutdown(destNum: Int): AdminResult<Unit> = radioController.shutdown(destNum)

    /** Factory resets [destNum]; [isLocal] also clears the phone's node database, which would be out of sync. */
    open suspend fun factoryReset(destNum: Int, isLocal: Boolean): AdminResult<Unit> =
        radioController.factoryReset(destNum).onSuccess { if (isLocal) nodeRepository.clearNodeDB() }

    /**
     * Syncs the node's real-time clock to the phone's current time, so a remote node whose RTC has drifted can be
     * corrected without being on-site or using the CLI.
     */
    open suspend fun setTime(destNum: Int): AdminResult<Unit> = radioController.setTime(destNum)

    /** Resets the NodeDB on [destNum]; [isLocal] also clears the phone's node database the same way. */
    open suspend fun nodedbReset(destNum: Int, preserveFavorites: Boolean, isLocal: Boolean): AdminResult<Unit> =
        radioController.nodedbReset(destNum, preserveFavorites).onSuccess {
            if (isLocal) nodeRepository.clearNodeDB(preserveFavorites)
        }
}
