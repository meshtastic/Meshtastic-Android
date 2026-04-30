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
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.RadioController
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/** Use case for interacting with radio configuration components. */
@Suppress("TooManyFunctions")
@Single
open class RadioConfigUseCase constructor(private val radioController: RadioController) {
    /**
     * Updates the owner information on the radio.
     *
     * @param destNum The node number to update.
     * @param user The new user configuration.
     * @return The packet ID of the request.
     */
    open suspend fun setOwner(destNum: Int, user: User): Int {
        val packetId = radioController.getPacketId()
        radioController.setOwner(destNum, user, packetId)
        return packetId
    }

    /**
     * Requests the owner information from the radio.
     *
     * @param destNum The node number to query.
     * @return The packet ID of the request.
     */
    open suspend fun getOwner(destNum: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.getOwner(destNum, packetId)
        return packetId
    }

    /**
     * Updates a configuration section on the radio.
     *
     * @param destNum The node number to update.
     * @param config The new configuration.
     * @return The packet ID of the request.
     */
    open suspend fun setConfig(destNum: Int, config: Config): Int {
        val packetId = radioController.getPacketId()
        radioController.setConfig(destNum, config, packetId)
        return packetId
    }

    /**
     * Requests a configuration section from the radio.
     *
     * @param destNum The node number to query.
     * @param configType The type of configuration to request (from [org.meshtastic.proto.AdminMessage.ConfigType]).
     * @return The packet ID of the request.
     */
    open suspend fun getConfig(destNum: Int, configType: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.getConfig(destNum, configType, packetId)
        return packetId
    }

    /**
     * Updates a module configuration section on the radio.
     *
     * @param destNum The node number to update.
     * @param config The new module configuration.
     * @return The packet ID of the request.
     */
    open suspend fun setModuleConfig(destNum: Int, config: ModuleConfig): Int {
        val packetId = radioController.getPacketId()
        radioController.setModuleConfig(destNum, config, packetId)
        return packetId
    }

    /**
     * Requests a module configuration section from the radio.
     *
     * @param destNum The node number to query.
     * @param moduleConfigType The type of module configuration to request.
     * @return The packet ID of the request.
     */
    open suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.getModuleConfig(destNum, moduleConfigType, packetId)
        return packetId
    }

    /**
     * Requests a channel from the radio.
     *
     * @param destNum The node number to query.
     * @param index The index of the channel to request.
     * @return The packet ID of the request.
     */
    open suspend fun getChannel(destNum: Int, index: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.getChannel(destNum, index, packetId)
        return packetId
    }

    /**
     * Updates a channel on the radio.
     *
     * @param destNum The node number to update.
     * @param channel The new channel configuration.
     * @return The packet ID of the request.
     */
    open suspend fun setRemoteChannel(destNum: Int, channel: org.meshtastic.proto.Channel): Int {
        val packetId = radioController.getPacketId()
        radioController.setRemoteChannel(destNum, channel, packetId)
        return packetId
    }

    /** Updates the fixed position on the radio. */
    open suspend fun setFixedPosition(destNum: Int, position: Position) {
        radioController.setFixedPosition(destNum, position)
    }

    /** Removes the fixed position on the radio. */
    open suspend fun removeFixedPosition(destNum: Int) {
        radioController.setFixedPosition(destNum, Position(0.0, 0.0, 0))
    }

    /** Sets the ringtone on the radio. */
    open suspend fun setRingtone(destNum: Int, ringtone: String) {
        radioController.setRingtone(destNum, ringtone)
    }

    /**
     * Requests the ringtone from the radio.
     *
     * @param destNum The node number to query.
     * @return The packet ID of the request.
     */
    open suspend fun getRingtone(destNum: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.getRingtone(destNum, packetId)
        return packetId
    }

    /** Sets the canned messages on the radio. */
    open suspend fun setCannedMessages(destNum: Int, messages: String) {
        radioController.setCannedMessages(destNum, messages)
    }

    /**
     * Requests the canned messages from the radio.
     *
     * @param destNum The node number to query.
     * @return The packet ID of the request.
     */
    open suspend fun getCannedMessages(destNum: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.getCannedMessages(destNum, packetId)
        return packetId
    }

    /**
     * Requests the device connection status from the radio.
     *
     * @param destNum The node number to query.
     * @return The packet ID of the request.
     */
    open suspend fun getDeviceConnectionStatus(destNum: Int): Int {
        val packetId = radioController.getPacketId()
        radioController.getDeviceConnectionStatus(destNum, packetId)
        return packetId
    }
}
