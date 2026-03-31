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
package org.meshtastic.core.data.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig

@Single
class MeshConfigHandlerImpl(
    private val radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeManager: NodeManager,
) : MeshConfigHandler {
    private var scope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val _localConfig = MutableStateFlow(LocalConfig())
    override val localConfig = _localConfig.asStateFlow()

    private val _moduleConfig = MutableStateFlow(LocalModuleConfig())
    override val moduleConfig = _moduleConfig.asStateFlow()

    override fun start(scope: CoroutineScope) {
        this.scope = scope
        radioConfigRepository.localConfigFlow.onEach { _localConfig.value = it }.launchIn(scope)
        radioConfigRepository.moduleConfigFlow.onEach { _moduleConfig.value = it }.launchIn(scope)
    }

    override fun handleDeviceConfig(config: Config) {
        scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
        serviceRepository.setConnectionProgress("Device config received")
    }

    override fun handleModuleConfig(config: ModuleConfig) {
        scope.handledLaunch { radioConfigRepository.setLocalModuleConfig(config) }
        serviceRepository.setConnectionProgress("Module config received")

        config.statusmessage?.let { sm ->
            nodeManager.myNodeNum?.let { num -> nodeManager.updateNodeStatus(num, sm.node_status) }
        }
    }

    override fun handleChannel(channel: Channel) {
        // We always want to save channel settings we receive from the radio
        scope.handledLaunch { radioConfigRepository.updateChannelSettings(channel) }

        // Update status message if we have node info, otherwise use a generic one
        val mi = nodeManager.getMyNodeInfo()
        val index = channel.index
        if (mi != null) {
            serviceRepository.setConnectionProgress("Channels (${index + 1} / ${mi.maxChannels})")
        } else {
            serviceRepository.setConnectionProgress("Channels (${index + 1})")
        }
    }

    override fun handleDeviceUIConfig(config: DeviceUIConfig) {
        scope.handledLaunch { radioConfigRepository.setDeviceUIConfig(config) }
    }
}
