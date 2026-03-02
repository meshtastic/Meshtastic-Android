/*
 * Copyright (c) 2025 Meshtastic LLC
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

import org.meshtastic.core.model.RadioController
import javax.inject.Inject

/**
 * Use case for performing administrative actions on the radio.
 */
class AdminActionsUseCase @Inject constructor(
    private val radioController: RadioController,
) {
    /**
     * Reboots the radio.
     */
    suspend fun reboot(destNum: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.reboot(destNum, packetId)
        return packetId
    }

    /**
     * Shuts down the radio.
     */
    suspend fun shutdown(destNum: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.shutdown(destNum, packetId)
        return packetId
    }

    /**
     * Factory resets the radio.
     */
    suspend fun factoryReset(destNum: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.factoryReset(destNum, packetId)
        return packetId
    }

    /**
     * Resets the NodeDB on the radio.
     */
    suspend fun nodedbReset(destNum: Int, preserveFavorites: Boolean): Int {
        val packetId = radioController.getPacketId()
        radioController.nodedbReset(destNum, packetId, preserveFavorites)
        return packetId
    }
}
