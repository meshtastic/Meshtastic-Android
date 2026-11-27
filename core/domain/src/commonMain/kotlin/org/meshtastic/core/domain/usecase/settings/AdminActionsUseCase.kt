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

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.routingErrorOrNull
import org.meshtastic.sdk.AdminResult

/**
 * Use case for performing administrative and destructive actions on mesh nodes.
 *
 * This component provides methods for rebooting, shutting down, or resetting nodes within the mesh. Each suspends until
 * the device answers and returns that answer. A reset of the locally connected node also clears the phone's node
 * database, unless the device refuses the reset.
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
        resetting(isLocal, clearLocal = { nodeRepository.clearNodeDB() }) { radioController.factoryReset(destNum) }

    /**
     * Runs a reset and, for the connected node, clears the phone's copy unless the radio refused it. The radio reboots
     * after a reset, so its answer can be lost, and the caller can go away before it arrives.
     */
    private suspend fun resetting(
        isLocal: Boolean,
        clearLocal: suspend () -> Unit,
        send: suspend () -> AdminResult<Unit>,
    ): AdminResult<Unit> {
        var result: AdminResult<Unit>? = null
        try {
            val answer = send()
            result = answer
            return answer
        } finally {
            val answer = result
            val abandoned = answer == null && !currentCoroutineContext().isActive
            val notRefused = answer != null && answer.routingErrorOrNull() == null
            if (isLocal && (abandoned || notRefused)) withContext(NonCancellable) { clearLocal() }
        }
    }

    /**
     * Syncs the node's real-time clock to the phone's current time, so a remote node whose RTC has drifted can be
     * corrected without being on-site or using the CLI.
     */
    open suspend fun setTime(destNum: Int): AdminResult<Unit> = radioController.setTime(destNum)

    /** Resets the NodeDB on [destNum]; [isLocal] also clears the phone's node database the same way. */
    open suspend fun nodedbReset(destNum: Int, preserveFavorites: Boolean, isLocal: Boolean): AdminResult<Unit> =
        resetting(isLocal, clearLocal = { nodeRepository.clearNodeDB(preserveFavorites) }) {
            radioController.nodedbReset(destNum, preserveFavorites)
        }
}
