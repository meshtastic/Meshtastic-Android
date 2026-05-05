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
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/** Focused interface for remote node administration. */
interface RemoteAdmin {
    suspend fun setOwner(destNum: Int, user: User, packetId: Int)
    suspend fun setConfig(destNum: Int, config: Config, packetId: Int)
    suspend fun setModuleConfig(destNum: Int, config: ModuleConfig, packetId: Int)
    suspend fun setRemoteChannel(destNum: Int, channel: Channel, packetId: Int)
    suspend fun setFixedPosition(destNum: Int, position: Position)
    suspend fun setRingtone(destNum: Int, ringtone: String)
    suspend fun setCannedMessages(destNum: Int, messages: String)
    suspend fun getOwner(destNum: Int, packetId: Int)
    suspend fun getConfig(destNum: Int, configType: Int, packetId: Int)
    suspend fun getModuleConfig(destNum: Int, moduleConfigType: Int, packetId: Int)
    suspend fun getChannel(destNum: Int, index: Int, packetId: Int)
    suspend fun getRingtone(destNum: Int, packetId: Int)
    suspend fun getCannedMessages(destNum: Int, packetId: Int)
    suspend fun getDeviceConnectionStatus(destNum: Int, packetId: Int)
}
