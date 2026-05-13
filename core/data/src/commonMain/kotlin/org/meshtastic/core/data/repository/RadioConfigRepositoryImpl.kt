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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.ChannelSetDataSource
import org.meshtastic.core.datastore.LocalConfigDataSource
import org.meshtastic.core.datastore.ModuleConfigDataSource
import org.meshtastic.core.model.util.getChannelUrl
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
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

/**
 * Class responsible for radio configuration data. Combines access to [nodeDB], [ChannelSet], [LocalConfig] &
 * [LocalModuleConfig].
 */
@Single
open class RadioConfigRepositoryImpl(
    private val nodeDB: NodeRepository,
    private val channelSetDataSource: ChannelSetDataSource,
    private val localConfigDataSource: LocalConfigDataSource,
    private val moduleConfigDataSource: ModuleConfigDataSource,
) : RadioConfigRepository {

    /** Flow representing the [ChannelSet] data store. */
    override val channelSetFlow: Flow<ChannelSet> = channelSetDataSource.channelSetFlow

    /** Clears the [ChannelSet] data in the data store. */
    override suspend fun clearChannelSet() {
        channelSetDataSource.clearChannelSet()
    }

    /** Replaces the [ChannelSettings] list with a new [settingsList]. */
    override suspend fun replaceAllSettings(settingsList: List<ChannelSettings>) {
        channelSetDataSource.replaceAllSettings(settingsList)
    }

    /**
     * Updates the [ChannelSettings] list with the provided channel and returns the index of the admin channel after the
     * update (if not found, returns 0).
     *
     * @param channel The [Channel] provided.
     * @return the index of the admin channel after the update (if not found, returns 0).
     */
    override suspend fun updateChannelSettings(channel: Channel) = channelSetDataSource.updateChannelSettings(channel)

    /** Flow representing the [LocalConfig] data store. */
    override val localConfigFlow: Flow<LocalConfig> = localConfigDataSource.localConfigFlow

    /** Clears the [LocalConfig] data in the data store. */
    override suspend fun clearLocalConfig() {
        localConfigDataSource.clearLocalConfig()
    }

    /**
     * Updates [LocalConfig] from each [Config] oneOf.
     *
     * @param config The [Config] to be set.
     */
    override suspend fun setLocalConfig(config: Config) {
        localConfigDataSource.setLocalConfig(config)
        config.lora?.let { channelSetDataSource.setLoraConfig(it) }
    }

    /** Flow representing the [LocalModuleConfig] data store. */
    override val moduleConfigFlow: Flow<LocalModuleConfig> = moduleConfigDataSource.moduleConfigFlow

    /** Clears the [LocalModuleConfig] data in the data store. */
    override suspend fun clearLocalModuleConfig() {
        moduleConfigDataSource.clearLocalModuleConfig()
    }

    /**
     * Updates [LocalModuleConfig] from each [ModuleConfig] oneOf.
     *
     * @param config The [ModuleConfig] to be set.
     */
    override suspend fun setLocalModuleConfig(config: ModuleConfig) {
        moduleConfigDataSource.setLocalModuleConfig(config)
    }

    // DeviceUIConfig is session-scoped data received fresh in every handshake — no persistence needed.
    private val _deviceUIConfigFlow = MutableStateFlow<DeviceUIConfig?>(null)
    override val deviceUIConfigFlow: Flow<DeviceUIConfig?> = _deviceUIConfigFlow.asStateFlow()

    override suspend fun setDeviceUIConfig(config: DeviceUIConfig) {
        _deviceUIConfigFlow.value = config
    }

    override suspend fun clearDeviceUIConfig() {
        _deviceUIConfigFlow.value = null
    }

    // FileInfo manifest is session-scoped: accumulated during STATE_SEND_FILEMANIFEST, cleared on each new handshake.
    private val _fileManifestFlow = MutableStateFlow<List<FileInfo>>(emptyList())
    override val fileManifestFlow: Flow<List<FileInfo>> = _fileManifestFlow.asStateFlow()

    override suspend fun addFileInfo(info: FileInfo) {
        _fileManifestFlow.value += info
    }

    override suspend fun clearFileManifest() {
        _fileManifestFlow.value = emptyList()
    }

    /** Flow representing the combined [DeviceProfile] protobuf. */
    override val deviceProfileFlow: Flow<DeviceProfile> =
        combine(nodeDB.ourNodeInfo, channelSetFlow, localConfigFlow, moduleConfigFlow) {
                node,
                channels,
                localConfig,
                localModuleConfig,
            ->
            DeviceProfile(
                long_name = node?.user?.long_name,
                short_name = node?.user?.short_name,
                channel_url = channels.getChannelUrl().toString(),
                config = localConfig,
                module_config = localModuleConfig,
                fixed_position =
                if (node != null && localConfig.position?.fixed_position == true) {
                    node.position
                } else {
                    null
                },
            )
        }
}
