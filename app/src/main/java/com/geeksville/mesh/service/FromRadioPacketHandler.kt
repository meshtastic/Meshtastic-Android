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

import co.touchlab.kermit.Logger
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.FromRadio
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches non-packet [FromRadio] variants to their respective handlers. This class is stateless and handles routing
 * for config, metadata, and specialized system messages.
 */
@Singleton
class FromRadioPacketHandler
@Inject
constructor(
    private val serviceRepository: ServiceRepository,
    private val router: MeshRouter,
    private val mqttManager: MeshMqttManager,
    private val packetHandler: PacketHandler,
    private val serviceNotifications: MeshServiceNotifications,
) {
    @Suppress("CyclomaticComplexMethod")
    fun handleFromRadio(proto: FromRadio) {
        when {
            proto.my_info != null -> proto.my_info?.let { router.configFlowManager.handleMyInfo(it) }
            proto.metadata != null -> proto.metadata?.let { router.configFlowManager.handleLocalMetadata(it) }
            proto.node_info != null ->
                proto.node_info?.let {
                    router.configFlowManager.handleNodeInfo(it)
                    serviceRepository.setStatusMessage("Nodes (${router.configFlowManager.newNodeCount})")
                }
            proto.config_complete_id != null ->
                proto.config_complete_id?.let { router.configFlowManager.handleConfigComplete(it) }
            proto.mqttClientProxyMessage != null ->
                proto.mqttClientProxyMessage?.let { mqttManager.handleMqttProxyMessage(it) }
            proto.queueStatus != null -> proto.queueStatus?.let { packetHandler.handleQueueStatus(it) }
            proto.config != null -> proto.config?.let { router.configHandler.handleDeviceConfig(it) }
            proto.moduleConfig != null -> proto.moduleConfig?.let { router.configHandler.handleModuleConfig(it) }
            proto.channel != null -> proto.channel?.let { router.configHandler.handleChannel(it) }
            proto.clientNotification != null ->
                proto.clientNotification?.let { notification ->
                    serviceRepository.setClientNotification(notification)
                    serviceNotifications.showClientNotification(notification)
                    packetHandler.removeResponse(notification.reply_id ?: 0, complete = false)
                }
            // Logging-only variants are handled by MeshMessageProcessor before dispatching here
            proto.packet != null ||
                proto.log_record != null ||
                proto.rebooted != null ||
                proto.xmodemPacket != null ||
                proto.deviceuiConfig != null ||
                proto.fileInfo != null -> {
                /* No specialized routing needed here */
            }

            else -> Logger.d { "Dispatcher ignoring FromRadio variant" }
        }
    }
}
