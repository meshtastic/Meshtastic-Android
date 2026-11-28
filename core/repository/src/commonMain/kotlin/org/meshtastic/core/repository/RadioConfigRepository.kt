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
import org.meshtastic.proto.LoRaRegionPresetMap
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

    /**
     * Atomically updates the cached channel set. A null argument preserves that field's current value.
     *
     * This is used after a committed radio transaction when channel settings and LoRa configuration must become visible
     * together.
     */
    suspend fun updateChannelSet(settingsList: List<ChannelSettings>?, loraConfig: Config.LoRaConfig?)

    /**
     * Re-keys stored conversations onto the current channel set.
     *
     * Conversations are keyed by channel index, so a slot that changed occupant shows the previous channel's history
     * under the new channel until this runs. The app's own whole-set writes reconcile themselves; this exists for the
     * handshake, which streams channels in one slot at a time and so must reconcile once at the end.
     */
    suspend fun reconcileConversations()

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
     * The connected device's UI configuration, from the active session's SDK config bundle. Null between sessions,
     * including while reconnecting, before the handshake has delivered one, or when the firmware sends none.
     */
    val deviceUIConfigFlow: Flow<DeviceUIConfig?>

    /** The connected device's file manifest from its handshake, in the order sent; empty with no session. */
    val fileManifestFlow: Flow<List<FileInfo>>

    /**
     * The firmware's region to modem-preset compatibility map, from the active session's SDK config bundle. Null
     * between sessions, including while reconnecting, or when the connected firmware predates this message (< 2.8). The
     * local LoRa config UI uses it to constrain the preset picker to the presets legal in the selected region.
     */
    val loraRegionPresetMapFlow: Flow<LoRaRegionPresetMap?>
}
