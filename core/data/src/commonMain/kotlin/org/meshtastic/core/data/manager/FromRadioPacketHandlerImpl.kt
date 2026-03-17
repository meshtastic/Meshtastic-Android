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

import org.koin.core.annotation.Single
import org.meshtastic.core.repository.FromRadioPacketHandler
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.client_notification
import org.meshtastic.core.resources.getString
import org.meshtastic.proto.FromRadio

/** Implementation of [FromRadioPacketHandler] that dispatches [FromRadio] variants to specialized handlers. */
@Single
class FromRadioPacketHandlerImpl(
    private val serviceRepository: ServiceRepository,
    private val router: Lazy<MeshRouter>,
    private val mqttManager: MqttManager,
    private val packetHandler: PacketHandler,
    private val notificationManager: NotificationManager,
) : FromRadioPacketHandler {
    @Suppress("CyclomaticComplexMethod")
    override fun handleFromRadio(proto: FromRadio) {
        val myInfo = proto.my_info
        val metadata = proto.metadata
        val nodeInfo = proto.node_info
        val configCompleteId = proto.config_complete_id
        val mqttProxyMessage = proto.mqttClientProxyMessage
        val queueStatus = proto.queueStatus
        val config = proto.config
        val moduleConfig = proto.moduleConfig
        val channel = proto.channel
        val clientNotification = proto.clientNotification

        when {
            myInfo != null -> router.value.configFlowManager.handleMyInfo(myInfo)
            metadata != null -> router.value.configFlowManager.handleLocalMetadata(metadata)
            nodeInfo != null -> {
                router.value.configFlowManager.handleNodeInfo(nodeInfo)
                serviceRepository.setConnectionProgress("Nodes (${router.value.configFlowManager.newNodeCount})")
            }
            configCompleteId != null -> router.value.configFlowManager.handleConfigComplete(configCompleteId)
            mqttProxyMessage != null -> mqttManager.handleMqttProxyMessage(mqttProxyMessage)
            queueStatus != null -> packetHandler.handleQueueStatus(queueStatus)
            config != null -> router.value.configHandler.handleDeviceConfig(config)
            moduleConfig != null -> router.value.configHandler.handleModuleConfig(moduleConfig)
            channel != null -> router.value.configHandler.handleChannel(channel)
            clientNotification != null -> {
                serviceRepository.setClientNotification(clientNotification)
                notificationManager.dispatch(
                    Notification(
                        title = getString(Res.string.client_notification),
                        message = clientNotification.message,
                        category = Notification.Category.Alert,
                    ),
                )
                packetHandler.removeResponse(0, complete = false)
            }
        }
    }
}
