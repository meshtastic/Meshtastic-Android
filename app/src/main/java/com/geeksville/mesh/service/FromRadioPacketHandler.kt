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
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.MeshProtos
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches non-packet [MeshProtos.FromRadio] variants to their respective handlers. This class is stateless and
 * handles routing for config, metadata, and specialized system messages.
 */
@Singleton
class FromRadioPacketHandler
@Inject
constructor(
    private val serviceRepository: ServiceRepository,
    private val router: MeshRouter,
    private val mqttManager: MeshMqttManager,
    private val packetHandler: PacketHandler,
) {
    @Suppress("CyclomaticComplexMethod")
    fun handleFromRadio(proto: MeshProtos.FromRadio) {
        when (proto.payloadVariantCase) {
            MeshProtos.FromRadio.PayloadVariantCase.MY_INFO -> router.configFlowManager.handleMyInfo(proto.myInfo)
            MeshProtos.FromRadio.PayloadVariantCase.METADATA ->
                router.configFlowManager.handleLocalMetadata(proto.metadata)
            MeshProtos.FromRadio.PayloadVariantCase.NODE_INFO -> {
                router.configFlowManager.handleNodeInfo(proto.nodeInfo)
                serviceRepository.setStatusMessage("Nodes (${router.configFlowManager.newNodeCount})")
            }
            MeshProtos.FromRadio.PayloadVariantCase.CONFIG_COMPLETE_ID ->
                router.configFlowManager.handleConfigComplete(proto.configCompleteId)
            MeshProtos.FromRadio.PayloadVariantCase.MQTTCLIENTPROXYMESSAGE ->
                mqttManager.handleMqttProxyMessage(proto.mqttClientProxyMessage)
            MeshProtos.FromRadio.PayloadVariantCase.QUEUESTATUS -> packetHandler.handleQueueStatus(proto.queueStatus)
            MeshProtos.FromRadio.PayloadVariantCase.CONFIG -> router.configHandler.handleDeviceConfig(proto.config)
            MeshProtos.FromRadio.PayloadVariantCase.MODULECONFIG ->
                router.configHandler.handleModuleConfig(proto.moduleConfig)
            MeshProtos.FromRadio.PayloadVariantCase.CHANNEL -> router.configHandler.handleChannel(proto.channel)
            MeshProtos.FromRadio.PayloadVariantCase.CLIENTNOTIFICATION -> {
                serviceRepository.setClientNotification(proto.clientNotification)
                packetHandler.removeResponse(proto.clientNotification.replyId, complete = false)
            }
            // Logging-only variants are handled by MeshMessageProcessor before dispatching here
            MeshProtos.FromRadio.PayloadVariantCase.PACKET,
            MeshProtos.FromRadio.PayloadVariantCase.LOG_RECORD,
            MeshProtos.FromRadio.PayloadVariantCase.REBOOTED,
            MeshProtos.FromRadio.PayloadVariantCase.XMODEMPACKET,
            MeshProtos.FromRadio.PayloadVariantCase.DEVICEUICONFIG,
            MeshProtos.FromRadio.PayloadVariantCase.FILEINFO,
            -> {
                /* No specialized routing needed here */
            }

            else -> Logger.d { "Dispatcher ignoring ${proto.payloadVariantCase}" }
        }
    }
}
