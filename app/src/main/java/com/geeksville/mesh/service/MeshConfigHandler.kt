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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import org.meshtastic.proto.ModuleConfig
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

    private val _moduleConfig = MutableStateFlow(LocalModuleConfig())
    val moduleConfig = _moduleConfig.asStateFlow()

    fun start(scope: CoroutineScope) {
        this.scope = scope
        radioConfigRepository.localConfigFlow.onEach { _localConfig.value = it }.launchIn(scope)

        radioConfigRepository.moduleConfigFlow.onEach { _moduleConfig.value = it }.launchIn(scope)
    }

    fun handleDeviceConfig(config: Config) {
        scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
        serviceRepository.setConnectionProgress("Device config received")
    }

    fun handleModuleConfig(config: ModuleConfig) {
        scope.handledLaunch { radioConfigRepository.setLocalModuleConfig(config) }
        serviceRepository.setConnectionProgress("Module config received")

        config.statusmessage?.node_status?.let { status ->
            nodeManager.myNodeNum?.let { num -> nodeManager.updateNodeStatus(num, status) }
        }
    }

    fun handleChannel(ch: Channel) {
        // We always want to save channel settings we receive from the radio
        scope.handledLaunch { radioConfigRepository.updateChannelSettings(ch) }

        // Update status message if we have node info, otherwise use a generic one
        val mi = nodeManager.getMyNodeInfo()
        val index = ch.index ?: 0
        if (mi != null) {
            serviceRepository.setConnectionProgress("Channels (${index + 1} / ${mi.maxChannels})")
        } else {
            serviceRepository.setConnectionProgress("Channels (${index + 1})")
        }
    }
}
