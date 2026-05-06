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
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/**
 * Use case for interacting with radio configuration components.
 *
 * Methods suspend until the device responds and return typed results directly.
 * On failure, they propagate [org.meshtastic.core.model.AdminException].
 */
@Suppress("TooManyFunctions")
@Single
open class RadioConfigUseCase constructor(private val radioController: RadioController) {

    /** Write the owner on the target node. */
    open suspend fun setOwner(destNum: Int, user: User) {
        radioController.setOwner(destNum, user)
    }

    /** Read the owner from the target node. */
    open suspend fun getOwner(destNum: Int): User =
        radioController.getOwner(destNum)

    /** Write a config section on the target node. */
    open suspend fun setConfig(destNum: Int, config: Config) {
        radioController.setConfig(destNum, config)
    }

    /** Read a config section from the target node. */
    open suspend fun getConfig(destNum: Int, configType: Int): Config =
        radioController.getConfig(destNum, configType)

    /** Write a module config section on the target node. */
    open suspend fun setModuleConfig(destNum: Int, config: ModuleConfig) {
        radioController.setModuleConfig(destNum, config)
    }

    /** Read a module config section from the target node. */
    open suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int): ModuleConfig =
        radioController.getModuleConfig(destNum, moduleConfigType)

    /** Read a channel by index from the target node. */
    open suspend fun getChannel(destNum: Int, index: Int): Channel =
        radioController.getChannel(destNum, index)

    /** Read all channels from the target node. */
    open suspend fun listChannels(destNum: Int): List<Channel> =
        radioController.listChannels(destNum)

    /** Write a channel on the target node. */
    open suspend fun setRemoteChannel(destNum: Int, channel: Channel) {
        radioController.setRemoteChannel(destNum, channel)
    }

    /** Set a fixed position on the target node. */
    open suspend fun setFixedPosition(destNum: Int, position: Position) {
        radioController.setFixedPosition(destNum, position)
    }

    /** Remove the fixed position (zero coordinates). */
    open suspend fun removeFixedPosition(destNum: Int) {
        radioController.setFixedPosition(destNum, Position(0.0, 0.0, 0))
    }

    /** Write the ringtone on the target node. */
    open suspend fun setRingtone(destNum: Int, ringtone: String) {
        radioController.setRingtone(destNum, ringtone)
    }

    /** Read the ringtone from the target node. */
    open suspend fun getRingtone(destNum: Int): String =
        radioController.getRingtone(destNum)

    /** Write canned messages on the target node. */
    open suspend fun setCannedMessages(destNum: Int, messages: String) {
        radioController.setCannedMessages(destNum, messages)
    }

    /** Read canned messages from the target node. */
    open suspend fun getCannedMessages(destNum: Int): String =
        radioController.getCannedMessages(destNum)

    /** Read device connection status from the target node. */
    open suspend fun getDeviceConnectionStatus(destNum: Int): DeviceConnectionStatus =
        radioController.getDeviceConnectionStatus(destNum)
}
