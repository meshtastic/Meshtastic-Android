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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.Flow
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig

@Suppress("TooManyFunctions")
interface RadioConfigRepository {
    /** Flow representing the [ChannelSet] data store. */
    val channelSetFlow: Flow<ChannelSet>

    /** Clears the [ChannelSet] data in the data store. */
    suspend fun clearChannelSet()

    /** Replaces the [ChannelSettings] list with a new [settingsList]. */
    suspend fun replaceAllSettings(settingsList: List<ChannelSettings>)

    /** Updates the [ChannelSettings] list with the provided channel. */
    suspend fun updateChannelSettings(channel: Channel)

    /** Flow representing the [LocalConfig] data store. */
    val localConfigFlow: Flow<LocalConfig>

    /** Clears the [LocalConfig] data in the data store. */
    suspend fun clearLocalConfig()

    /** Updates [LocalConfig] from each [Config] oneOf. */
    suspend fun setLocalConfig(config: Config)

    /** Flow representing the [LocalModuleConfig] data store. */
    val moduleConfigFlow: Flow<LocalModuleConfig>

    /** Clears the [LocalModuleConfig] data in the data store. */
    suspend fun clearLocalModuleConfig()

    /** Updates [LocalModuleConfig] from each [ModuleConfig] oneOf. */
    suspend fun setLocalModuleConfig(config: ModuleConfig)

    /** Flow representing the combined [DeviceProfile] protobuf. */
    val deviceProfileFlow: Flow<DeviceProfile>

    /**
     * Flow of the device's UI configuration, populated from [DeviceUIConfig] during the config handshake
     * (STATE_SEND_UIDATA — 2nd packet in every handshake). Null until the first handshake completes or after
     * [clearDeviceUIConfig] is called.
     */
    val deviceUIConfigFlow: Flow<DeviceUIConfig?>

    /** Stores the [DeviceUIConfig] received from the device. */
    suspend fun setDeviceUIConfig(config: DeviceUIConfig)

    /** Clears the stored [DeviceUIConfig]; called at the start of each new handshake. */
    suspend fun clearDeviceUIConfig()

    /**
     * Flow of [FileInfo] packets accumulated during STATE_SEND_FILEMANIFEST.
     *
     * Cleared at the start of each new handshake via [clearFileManifest].
     */
    val fileManifestFlow: Flow<List<FileInfo>>

    /** Appends a single [FileInfo] entry to [fileManifestFlow]. */
    suspend fun addFileInfo(info: FileInfo)

    /** Clears the accumulated file manifest; called at the start of each new handshake. */
    suspend fun clearFileManifest()
}
