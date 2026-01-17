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
package com.geeksville.mesh.service

import com.geeksville.mesh.concurrent.handledLaunch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
import java.lang.reflect.Modifier
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshConfigHandler
@Inject
constructor(
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeManager: MeshNodeManager,
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _localConfig = MutableStateFlow(LocalConfig())
    val localConfig = _localConfig.asStateFlow()

    private val _localConfigBytes = MutableStateFlow(LocalConfig.ADAPTER.encode(_localConfig.value))
    val localConfigBytes = _localConfigBytes.asStateFlow()

    private val _moduleConfig = MutableStateFlow(LocalModuleConfig())
    val moduleConfig = _moduleConfig.asStateFlow()

    private val _channelSet = MutableStateFlow(ChannelSet())
    val channelSet = _channelSet.asStateFlow()

    private val _channelSetBytes = MutableStateFlow(ChannelSet.ADAPTER.encode(_channelSet.value))
    val channelSetBytes = _channelSetBytes.asStateFlow()

    private val hasLoadedLocalConfig = MutableStateFlow(false)
    private val hasLoadedChannelSet = MutableStateFlow(false)

    val hasLoaded: Boolean
        get() = hasLoadedLocalConfig.value && hasLoadedChannelSet.value

    val loadedFlow = kotlinx.coroutines.flow.combine(hasLoadedLocalConfig, hasLoadedChannelSet) { a, b -> a && b }

    // Dynamically count non-static fields to avoid hardcoded values drifting from Proto definitions
    private val configTotal =
        Config::class.java.declaredFields.count { !Modifier.isStatic(it.modifiers) && it.name != "unknownFields" }

    private val moduleTotal =
        LocalModuleConfig::class.java.declaredFields.count {
            !Modifier.isStatic(it.modifiers) && it.name != "unknownFields"
        }

    init {
        radioConfigRepository.localConfigFlow
            .filterNotNull()
            .onEach {
                _localConfig.value = it
                _localConfigBytes.value = LocalConfig.ADAPTER.encode(it)
                hasLoadedLocalConfig.value = true
            }
            .launchIn(scope)
        radioConfigRepository.moduleConfigFlow.onEach { _moduleConfig.value = it }.launchIn(scope)
        radioConfigRepository.channelSetFlow
            .filterNotNull()
            .onEach {
                _channelSet.value = it
                _channelSetBytes.value = ChannelSet.ADAPTER.encode(it)
                hasLoadedChannelSet.value = true
            }
            .launchIn(scope)
    }

    fun start(newScope: CoroutineScope) {
        this.scope = newScope
        // Re-launch collectors in the new scope if needed, or simply ensure they are active.
        // Since init block already launched them in the initial scope, and that scope might be
        // replaced here, we should ideally re-launch them if the scope changes.
        // However, for this fix, we simply provide the method expected by MeshRouter.
        // Given the architecture, it's safer to re-attach listeners to the provided scope.

        radioConfigRepository.localConfigFlow
            .filterNotNull()
            .onEach {
                _localConfig.value = it
                _localConfigBytes.value = LocalConfig.ADAPTER.encode(it)
                hasLoadedLocalConfig.value = true
            }
            .launchIn(newScope)
        radioConfigRepository.moduleConfigFlow.onEach { _moduleConfig.value = it }.launchIn(newScope)
        radioConfigRepository.channelSetFlow
            .filterNotNull()
            .onEach {
                _channelSet.value = it
                _channelSetBytes.value = ChannelSet.ADAPTER.encode(it)
                hasLoadedChannelSet.value = true
            }
            .launchIn(newScope)
    }

    fun handleDeviceConfig(config: Config) {
        scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
        // Wire doesn't have allFields, count non-null fields
        val configCount = countNonNullFields(_localConfig.value)
        serviceRepository.setStatusMessage("Device config ($configCount / $configTotal)")
    }

    fun handleModuleConfig(config: ModuleConfig) {
        scope.handledLaunch { radioConfigRepository.setLocalModuleConfig(config) }
        val moduleCount = countNonNullFields(_moduleConfig.value)
        serviceRepository.setStatusMessage("Module config ($moduleCount / $moduleTotal)")
    }

    private fun countNonNullFields(obj: Any): Int = obj::class.java.declaredFields.count { field ->
        field.isAccessible = true
        !Modifier.isStatic(field.modifiers) && field.name != "unknownFields" && field.get(obj) != null
    }

    fun handleChannel(ch: Channel) {
        // We always want to save channel settings we receive from the radio
        scope.handledLaunch { radioConfigRepository.updateChannelSettings(ch) }

        // Update status message if we have node info, otherwise use a generic one
        val mi = nodeManager.getMyNodeInfo()
        if (mi != null) {
            serviceRepository.setStatusMessage("Channels (${ch.index + 1} / ${mi.maxChannels})")
        } else {
            serviceRepository.setStatusMessage("Channels (${ch.index + 1})")
        }
    }
}
