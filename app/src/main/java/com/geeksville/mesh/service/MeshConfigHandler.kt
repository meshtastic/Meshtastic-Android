/*
 * Copyright (c) 2025 Meshtastic LLC
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.ChannelProtos
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.LocalOnlyProtos.LocalConfig
import org.meshtastic.proto.LocalOnlyProtos.LocalModuleConfig
import org.meshtastic.proto.ModuleConfigProtos
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

    private val _localConfig = MutableStateFlow(LocalConfig.getDefaultInstance())
    val localConfig = _localConfig.asStateFlow()

    private val _moduleConfig = MutableStateFlow(LocalModuleConfig.getDefaultInstance())
    val moduleConfig = _moduleConfig.asStateFlow()

    private val configTotal = ConfigProtos.Config.getDescriptor().fields.size
    private val moduleTotal = ModuleConfigProtos.ModuleConfig.getDescriptor().fields.size

    fun start(scope: CoroutineScope) {
        this.scope = scope
        radioConfigRepository.localConfigFlow.onEach { _localConfig.value = it }.launchIn(scope)

        radioConfigRepository.moduleConfigFlow.onEach { _moduleConfig.value = it }.launchIn(scope)
    }

    fun handleDeviceConfig(config: ConfigProtos.Config) {
        scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
        val configCount = _localConfig.value.allFields.size
        serviceRepository.setStatusMessage("Device config ($configCount / $configTotal)")
    }

    fun handleModuleConfig(config: ModuleConfigProtos.ModuleConfig) {
        scope.handledLaunch { radioConfigRepository.setLocalModuleConfig(config) }
        val moduleCount = _moduleConfig.value.allFields.size
        serviceRepository.setStatusMessage("Module config ($moduleCount / $moduleTotal)")
    }

    fun handleChannel(ch: ChannelProtos.Channel) {
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
