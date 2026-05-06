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
package org.meshtastic.core.model

import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceConnectionStatus
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/**
 * Focused interface for remote node administration.
 *
 * Methods suspend until the device responds. On failure, they throw [AdminException].
 */
interface RemoteAdmin {
    /** Write the owner [user] on the target node. */
    suspend fun setOwner(destNum: Int, user: User)

    /** Write a [config] section on the target node. */
    suspend fun setConfig(destNum: Int, config: Config)

    /** Write a [config] module section on the target node. */
    suspend fun setModuleConfig(destNum: Int, config: ModuleConfig)

    /** Write a [channel] on the target node. */
    suspend fun setRemoteChannel(destNum: Int, channel: Channel)

    /** Set a fixed position on the target node. */
    suspend fun setFixedPosition(destNum: Int, position: Position)

    /** Set the ringtone (RTTTL) on the target node. */
    suspend fun setRingtone(destNum: Int, ringtone: String)

    /** Set canned messages on the target node. */
    suspend fun setCannedMessages(destNum: Int, messages: String)

    /** Read the owner from the target node. */
    suspend fun getOwner(destNum: Int): User

    /** Read a config section from the target node. */
    suspend fun getConfig(destNum: Int, configType: Int): Config

    /** Read a module config section from the target node. */
    suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int): ModuleConfig

    /** Read a channel by [index] from the target node. */
    suspend fun getChannel(destNum: Int, index: Int): Channel

    /** Read all channels from the target node (stops at first disabled slot). */
    suspend fun listChannels(destNum: Int): List<Channel>

    /** Read the ringtone from the target node. */
    suspend fun getRingtone(destNum: Int): String

    /** Read canned messages from the target node. */
    suspend fun getCannedMessages(destNum: Int): String

    /** Read device connection status from the target node. */
    suspend fun getDeviceConnectionStatus(destNum: Int): DeviceConnectionStatus
}
