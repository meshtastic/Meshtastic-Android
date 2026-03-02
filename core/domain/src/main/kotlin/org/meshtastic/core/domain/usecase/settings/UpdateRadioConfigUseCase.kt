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

import org.meshtastic.core.model.Position
import org.meshtastic.core.model.RadioController
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import javax.inject.Inject

/**
 * Use case for updating radio configuration components.
 */
class UpdateRadioConfigUseCase @Inject constructor(
    private val radioController: RadioController,
) {
    /**
     * Updates the owner information on the radio.
     */
    suspend fun setOwner(destNum: Int, user: User): Int {
        val packetId = radioController.getPacketId()
        radioController.setOwner(destNum, user, packetId)
        return packetId
    }

    /**
     * Updates a configuration section on the radio.
     */
    suspend fun setConfig(destNum: Int, config: Config): Int {
        val packetId = radioController.getPacketId()
        radioController.setConfig(destNum, config, packetId)
        return packetId
    }

    /**
     * Updates a module configuration section on the radio.
     */
    suspend fun setModuleConfig(destNum: Int, config: ModuleConfig): Int {
        val packetId = radioController.getPacketId()
        radioController.setModuleConfig(destNum, config, packetId)
        return packetId
    }

    /**
     * Updates the fixed position on the radio.
     */
    suspend fun setFixedPosition(destNum: Int, position: Position) {
        radioController.setFixedPosition(destNum, position)
    }

    /**
     * Removes the fixed position on the radio.
     */
    suspend fun removeFixedPosition(destNum: Int) {
        radioController.setFixedPosition(destNum, Position(0.0, 0.0, 0))
    }

    /**
     * Sets the ringtone on the radio.
     */
    suspend fun setRingtone(destNum: Int, ringtone: String) {
        radioController.setRingtone(destNum, ringtone)
    }

    /**
     * Sets the canned messages on the radio.
     */
    suspend fun setCannedMessages(destNum: Int, messages: String) {
        radioController.setCannedMessages(destNum, messages)
    }
}
