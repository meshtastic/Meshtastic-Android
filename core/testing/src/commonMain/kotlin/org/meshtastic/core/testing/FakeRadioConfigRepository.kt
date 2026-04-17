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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.Flow
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
 * A test double for [RadioConfigRepository] backed by in-memory [kotlinx.coroutines.flow.MutableStateFlow]s.
 *
 * All mutator methods update the underlying state flows synchronously so tests can observe changes immediately.
 * [deviceProfileFlow] is derived from [localConfigFlow], [moduleConfigFlow], and the current channel set.
 */
@Suppress("TooManyFunctions")
class FakeRadioConfigRepository :
    BaseFake(),
    RadioConfigRepository {

    private val channelSetBacking = mutableStateFlow(ChannelSet())
    override val channelSetFlow: Flow<ChannelSet> = channelSetBacking

    private val localConfigBacking = mutableStateFlow(LocalConfig())
    override val localConfigFlow: Flow<LocalConfig> = localConfigBacking

    private val moduleConfigBacking = mutableStateFlow(LocalModuleConfig())
    override val moduleConfigFlow: Flow<LocalModuleConfig> = moduleConfigBacking

    private val deviceProfileBacking = mutableStateFlow(DeviceProfile())
    override val deviceProfileFlow: Flow<DeviceProfile> = deviceProfileBacking
    val currentDeviceProfile: DeviceProfile
        get() = deviceProfileBacking.value

    private val deviceUIConfigBacking = mutableStateFlow<DeviceUIConfig?>(null)
    override val deviceUIConfigFlow: Flow<DeviceUIConfig?> = deviceUIConfigBacking

    private val fileManifestBacking = mutableStateFlow<List<FileInfo>>(emptyList())
    override val fileManifestFlow: Flow<List<FileInfo>> = fileManifestBacking

    val currentChannelSet: ChannelSet
        get() = channelSetBacking.value

    val currentLocalConfig: LocalConfig
        get() = localConfigBacking.value

    val currentModuleConfig: LocalModuleConfig
        get() = moduleConfigBacking.value

    val currentDeviceUIConfig: DeviceUIConfig?
        get() = deviceUIConfigBacking.value

    val currentFileManifest: List<FileInfo>
        get() = fileManifestBacking.value

    /**
     * Last [Config] passed to [setLocalConfig] (null until called). Tests should use [setLocalConfigDirect] to drive
     * state.
     */
    var lastSetLocalConfig: Config? = null
        private set

    /** Last [ModuleConfig] passed to [setLocalModuleConfig] (null until called). */
    var lastSetModuleConfig: ModuleConfig? = null
        private set

    init {
        registerResetAction {
            lastSetLocalConfig = null
            lastSetModuleConfig = null
        }
    }

    override suspend fun clearChannelSet() {
        channelSetBacking.value = ChannelSet()
    }

    override suspend fun replaceAllSettings(settingsList: List<ChannelSettings>) {
        channelSetBacking.value = channelSetBacking.value.copy(settings = settingsList)
    }

    override suspend fun updateChannelSettings(channel: Channel) {
        val current = channelSetBacking.value.settings.toMutableList()
        while (current.size <= channel.index) current.add(ChannelSettings())
        current[channel.index] = channel.settings ?: ChannelSettings()
        channelSetBacking.value = channelSetBacking.value.copy(settings = current)
    }

    override suspend fun clearLocalConfig() {
        localConfigBacking.value = LocalConfig()
    }

    override suspend fun setLocalConfig(config: Config) {
        lastSetLocalConfig = config
    }

    override suspend fun clearLocalModuleConfig() {
        moduleConfigBacking.value = LocalModuleConfig()
    }

    override suspend fun setLocalModuleConfig(config: ModuleConfig) {
        lastSetModuleConfig = config
    }

    override suspend fun setDeviceUIConfig(config: DeviceUIConfig) {
        deviceUIConfigBacking.value = config
    }

    override suspend fun clearDeviceUIConfig() {
        deviceUIConfigBacking.value = null
    }

    override suspend fun addFileInfo(info: FileInfo) {
        fileManifestBacking.value = fileManifestBacking.value + info
    }

    override suspend fun clearFileManifest() {
        fileManifestBacking.value = emptyList()
    }

    /** Directly sets the [LocalConfig] without merging (preferred for test setup). */
    fun setLocalConfigDirect(config: LocalConfig) {
        localConfigBacking.value = config
    }

    /** Directly sets the [LocalModuleConfig] without merging (preferred for test setup). */
    fun setLocalModuleConfigDirect(config: LocalModuleConfig) {
        moduleConfigBacking.value = config
    }

    /** Directly sets the combined [DeviceProfile] emitted by [deviceProfileFlow]. */
    fun setDeviceProfile(profile: DeviceProfile) {
        deviceProfileBacking.value = profile
    }

    /** Directly sets the [ChannelSet] (bypasses [updateChannelSettings]/[replaceAllSettings]). */
    fun setChannelSet(channelSet: ChannelSet) {
        channelSetBacking.value = channelSet
    }
}
