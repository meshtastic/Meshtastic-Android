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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single
import org.meshtastic.core.data.datasource.SwitchingChannelSetDataSource
import org.meshtastic.core.datastore.LocalConfigDataSource
import org.meshtastic.core.datastore.ModuleConfigDataSource
import org.meshtastic.core.model.util.getChannelUrl
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
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
import org.meshtastic.sdk.ConfigBundle

/**
 * Class responsible for radio configuration data. Combines access to [nodeDB], [ChannelSet], [LocalConfig] &
 * [LocalModuleConfig].
 */
private const val MAX_FILE_MANIFEST_ENTRIES = 4096

@Single
@Suppress("TooManyFunctions") // All functions mandated by RadioConfigRepository interface; logically grouped by concern
open class RadioConfigRepositoryImpl(
    private val nodeDB: NodeRepository,
    private val channelSetDataSource: SwitchingChannelSetDataSource,
    private val localConfigDataSource: LocalConfigDataSource,
    private val moduleConfigDataSource: ModuleConfigDataSource,
    private val radioInterfaceService: RadioInterfaceService,
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

    override suspend fun updateChannelSet(settingsList: List<ChannelSettings>?, loraConfig: Config.LoRaConfig?) {
        channelSetDataSource.updateChannelSet(settingsList, loraConfig)
    }

    override suspend fun reconcileConversations() {
        channelSetDataSource.reconcileConversations()
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

    // The SDK client's config bundle follows the active session; with no session or no bundle yet both flows are null.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sessionConfigBundle: Flow<ConfigBundle?> =
        radioInterfaceService.activeSession.flatMapLatest { session ->
            session?.let(radioInterfaceService::clientForSession)?.configBundle ?: flowOf(null)
        }

    override val deviceUIConfigFlow: Flow<DeviceUIConfig?> =
        sessionConfigBundle.map { it?.deviceUIConfig }.distinctUntilChanged()

    override val loraRegionPresetMapFlow: Flow<LoRaRegionPresetMap?> =
        sessionConfigBundle.map { it?.regionPresets }.distinctUntilChanged()

    // FileInfo manifest is session-scoped: accumulated during STATE_SEND_FILEMANIFEST, cleared on each new handshake.
    private val _fileManifestFlow = MutableStateFlow<List<FileInfo>>(emptyList())
    override val fileManifestFlow: Flow<List<FileInfo>> = _fileManifestFlow.asStateFlow()

    override suspend fun addFileInfo(info: FileInfo) {
        // A real device's manifest is bounded by its flash storage (realistically a handful to a few
        // hundred entries). A rogue/adversarial peer, however, can emit FileInfo packets in a tight loop
        // for the lifetime of a session -- clearFileManifest only runs on the *next* handshake, so nothing
        // else bounds this accumulator in between. Cap it defensively.
        //
        // handleFileInfo dispatches each packet on its own scope.handledLaunch, so addFileInfo calls can
        // run concurrently -- a plain read-then-write would race (lost updates, or two callers both seeing
        // size-1 and bypassing the cap). update{} makes the read/cap/append atomic (CAS retry loop).
        var capped = false
        _fileManifestFlow.update { current ->
            if (current.size >= MAX_FILE_MANIFEST_ENTRIES) {
                capped = true
                current
            } else {
                capped = false
                current + info
            }
        }
        if (capped) {
            Logger.w { "File manifest capped at $MAX_FILE_MANIFEST_ENTRIES entries, dropping further FileInfo" }
        }
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
            DeviceProfile.Builder()
                .also { wb ->
                    wb.long_name = node?.user?.long_name
                    wb.short_name = node?.user?.short_name
                    wb.channel_url = channels.getChannelUrl().toString()
                    wb.config = localConfig
                    wb.module_config = localModuleConfig
                    wb.is_unmessagable = node?.user?.is_unmessagable
                    wb.is_licensed = node?.user?.is_licensed
                    wb.fixed_position =
                        if (node != null && localConfig.position?.fixed_position == true) {
                            node.position
                        } else {
                            null
                        }
                }
                .build()
        }
}
