/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.meshtastic.core.datastore.ChannelSetDataSource
import org.meshtastic.core.datastore.LocalConfigDataSource
import org.meshtastic.core.datastore.ModuleConfigDataSource
import org.meshtastic.core.model.util.getChannelUrl
import org.meshtastic.proto.AppOnlyProtos.ChannelSet
import org.meshtastic.proto.ChannelProtos.Channel
import org.meshtastic.proto.ChannelProtos.ChannelSettings
import org.meshtastic.proto.ClientOnlyProtos.DeviceProfile
import org.meshtastic.proto.ConfigProtos.Config
import org.meshtastic.proto.LocalOnlyProtos.LocalConfig
import org.meshtastic.proto.LocalOnlyProtos.LocalModuleConfig
import org.meshtastic.proto.ModuleConfigProtos.ModuleConfig
import org.meshtastic.proto.deviceProfile
import javax.inject.Inject

/**
 * Class responsible for radio configuration data. Combines access to [nodeDB], [ChannelSet], [LocalConfig] &
 * [LocalModuleConfig].
 */
class RadioConfigRepository
@Inject
constructor(
    private val nodeDB: NodeRepository,
    private val channelSetDataSource: ChannelSetDataSource,
    private val localConfigDataSource: LocalConfigDataSource,
    private val moduleConfigDataSource: ModuleConfigDataSource,
) {

    /** Flow representing the [ChannelSet] data store. */
    val channelSetFlow: Flow<ChannelSet> = channelSetDataSource.channelSetFlow

    /** Clears the [ChannelSet] data in the data store. */
    suspend fun clearChannelSet() {
        channelSetDataSource.clearChannelSet()
    }

    /** Replaces the [ChannelSettings] list with a new [settingsList]. */
    suspend fun replaceAllSettings(settingsList: List<ChannelSettings>) {
        channelSetDataSource.replaceAllSettings(settingsList)
    }

    /**
     * Updates the [ChannelSettings] list with the provided channel and returns the index of the admin channel after the
     * update (if not found, returns 0).
     *
     * @param channel The [Channel] provided.
     * @return the index of the admin channel after the update (if not found, returns 0).
     */
    suspend fun updateChannelSettings(channel: Channel) = channelSetDataSource.updateChannelSettings(channel)

    /** Flow representing the [LocalConfig] data store. */
    val localConfigFlow: Flow<LocalConfig> = localConfigDataSource.localConfigFlow

    /** Clears the [LocalConfig] data in the data store. */
    suspend fun clearLocalConfig() {
        localConfigDataSource.clearLocalConfig()
    }

    /**
     * Updates [LocalConfig] from each [Config] oneOf.
     *
     * @param config The [Config] to be set.
     */
    suspend fun setLocalConfig(config: Config) {
        localConfigDataSource.setLocalConfig(config)
        if (config.hasLora()) channelSetDataSource.setLoraConfig(config.lora)
    }

    /** Flow representing the [LocalModuleConfig] data store. */
    val moduleConfigFlow: Flow<LocalModuleConfig> = moduleConfigDataSource.moduleConfigFlow

    /** Clears the [LocalModuleConfig] data in the data store. */
    suspend fun clearLocalModuleConfig() {
        moduleConfigDataSource.clearLocalModuleConfig()
    }

    /**
     * Updates [LocalModuleConfig] from each [ModuleConfig] oneOf.
     *
     * @param config The [ModuleConfig] to be set.
     */
    suspend fun setLocalModuleConfig(config: ModuleConfig) {
        moduleConfigDataSource.setLocalModuleConfig(config)
    }

    /** Flow representing the combined [DeviceProfile] protobuf. */
    val deviceProfileFlow: Flow<DeviceProfile> =
        combine(nodeDB.ourNodeInfo, channelSetFlow, localConfigFlow, moduleConfigFlow) {
                node,
                channels,
                localConfig,
                localModuleConfig,
            ->
            deviceProfile {
                node?.user?.let {
                    longName = it.longName
                    shortName = it.shortName
                }
                channelUrl = channels.getChannelUrl().toString()
                config = localConfig
                moduleConfig = localModuleConfig
                if (node != null && localConfig.position.fixedPosition) {
                    fixedPosition = node.position
                }
            }
        }
}
