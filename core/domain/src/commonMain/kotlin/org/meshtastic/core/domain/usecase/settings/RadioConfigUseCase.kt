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
import org.meshtastic.core.repository.RadioController
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult

/**
 * Use case for interacting with radio configuration components.
 *
 * Each request suspends until the device answers, through the radio session's SDK admin API, and returns that answer.
 */
@Suppress("TooManyFunctions")
@Single
open class RadioConfigUseCase constructor(private val radioController: RadioController) {
    /** Updates the owner information on [destNum]. */
    open suspend fun setOwner(destNum: Int, user: User): AdminResult<Unit> = radioController.setOwner(destNum, user)

    /**
     * Enables amateur-radio (ham) mode on the locally connected node via `set_ham_mode`. `call_sign`, `short_name` and
     * the optional `long_name` are user-supplied; `tx_power`/`frequency` are filled in by the controller.
     */
    open suspend fun setHamMode(destNum: Int, hamParameters: HamParameters): AdminResult<Unit> =
        radioController.setHamMode(destNum, hamParameters)

    /** Reads the owner information from [destNum]. */
    open suspend fun getOwner(destNum: Int): AdminResult<User> = radioController.getOwner(destNum)

    /** Reads the device metadata from [destNum], the remote-admin preflight. */
    open suspend fun getDeviceMetadata(destNum: Int): AdminResult<DeviceMetadata> =
        radioController.getDeviceMetadata(destNum)

    /** Updates a configuration section on [destNum]. */
    open suspend fun setConfig(destNum: Int, config: Config): AdminResult<Unit> =
        radioController.setConfig(destNum, config)

    /** Reads a configuration section; [configType] is an `AdminMessage.ConfigType` value. */
    open suspend fun getConfig(destNum: Int, configType: Int): AdminResult<Config> =
        radioController.getConfig(destNum, configType)

    /** Updates a module configuration section on [destNum]. */
    open suspend fun setModuleConfig(destNum: Int, config: ModuleConfig): AdminResult<Unit> =
        radioController.setModuleConfig(destNum, config)

    /** Reads a module configuration section; [moduleConfigType] is an `AdminMessage.ModuleConfigType` value. */
    open suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int): AdminResult<ModuleConfig> =
        radioController.getModuleConfig(destNum, moduleConfigType)

    /** Reads the channel at zero-based [index]. */
    open suspend fun getChannel(destNum: Int, index: Int): AdminResult<Channel> =
        radioController.getChannel(destNum, index)

    /** Updates a channel on [destNum]. */
    open suspend fun setRemoteChannel(destNum: Int, channel: Channel): AdminResult<Unit> =
        radioController.setRemoteChannel(destNum, channel)

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

    /** Plays the saved ringtone on the radio once. */
    open suspend fun playRingtone(destNum: Int) {
        radioController.playRingtone(destNum)
    }

    /** Reads the ringtone from [destNum]. */
    open suspend fun getRingtone(destNum: Int): AdminResult<String> = radioController.getRingtone(destNum)

    /** Sets the canned messages on the radio. */
    open suspend fun setCannedMessages(destNum: Int, messages: String) {
        radioController.setCannedMessages(destNum, messages)
    }

    /** Reads the canned messages from [destNum]. */
    open suspend fun getCannedMessages(destNum: Int): AdminResult<String> = radioController.getCannedMessages(destNum)

    /** Reads the device connection status from [destNum]. */
    open suspend fun getDeviceConnectionStatus(destNum: Int): AdminResult<DeviceConnectionStatus> =
        radioController.getDeviceConnectionStatus(destNum)
}
