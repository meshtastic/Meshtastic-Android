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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
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
    @Named("ServiceScope") private val scope: CoroutineScope,
) : MeshConfigHandler {

    private val _localConfig = MutableStateFlow(LocalConfig())
    override val localConfig = _localConfig.asStateFlow()

    private val _moduleConfig = MutableStateFlow(LocalModuleConfig())
    override val moduleConfig = _moduleConfig.asStateFlow()

    init {
        radioConfigRepository.localConfigFlow.onEach { _localConfig.value = it }.launchIn(scope)
        radioConfigRepository.moduleConfigFlow.onEach { _moduleConfig.value = it }.launchIn(scope)
    }

    override fun handleDeviceConfig(config: Config) {
        Logger.d { "Device config received: ${config.summarize()}" }
        scope.handledLaunch { radioConfigRepository.setLocalConfig(config) }
        serviceRepository.setConnectionProgress("Device config received")
    }

    override fun handleModuleConfig(config: ModuleConfig) {
        Logger.d { "Module config received: ${config.summarize()}" }
        scope.handledLaunch { radioConfigRepository.setLocalModuleConfig(config) }
        serviceRepository.setConnectionProgress("Module config received")

        config.statusmessage?.let { sm ->
            nodeManager.myNodeNum.value?.let { num -> nodeManager.updateNodeStatus(num, sm.node_status) }
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
        Logger.d { "DeviceUI config received" }
        scope.handledLaunch { radioConfigRepository.setDeviceUIConfig(config) }
    }
}

/** Returns a short summary of which Config variant is set. */
private fun Config.summarize(): String = when {
    device != null -> "device"
    position != null -> "position"
    power != null -> "power"
    network != null -> "network"
    display != null -> "display"
    lora != null -> "lora"
    bluetooth != null -> "bluetooth"
    security != null -> "security"
    else -> "unknown"
}

/** Returns a short summary of which ModuleConfig variant is set. */
@Suppress("CyclomaticComplexMethod")
private fun ModuleConfig.summarize(): String = when {
    mqtt != null -> "mqtt"
    serial != null -> "serial"
    external_notification != null -> "external_notification"
    store_forward != null -> "store_forward"
    range_test != null -> "range_test"
    telemetry != null -> "telemetry"
    canned_message != null -> "canned_message"
    audio != null -> "audio"
    remote_hardware != null -> "remote_hardware"
    neighbor_info != null -> "neighbor_info"
    ambient_lighting != null -> "ambient_lighting"
    detection_sensor != null -> "detection_sensor"
    paxcounter != null -> "paxcounter"
    statusmessage != null -> "statusmessage"
    else -> "unknown"
}
