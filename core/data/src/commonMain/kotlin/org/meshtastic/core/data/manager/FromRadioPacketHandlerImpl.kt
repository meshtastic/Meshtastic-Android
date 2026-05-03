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
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.repository.FromRadioPacketHandler
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.client_notification
import org.meshtastic.core.resources.duplicated_public_key_title
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.key_verification_final_title
import org.meshtastic.core.resources.key_verification_request_title
import org.meshtastic.core.resources.key_verification_title
import org.meshtastic.core.resources.low_entropy_key_title
import org.meshtastic.proto.ClientNotification
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

    // Application-scoped coroutine context for suspend work (e.g. getStringSuspend).
    // This @Single lives for the entire app lifetime, so the SupervisorJob is never cancelled.
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

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
        val deviceUIConfig = proto.deviceuiConfig
        val fileInfo = proto.fileInfo
        val xmodemPacket = proto.xmodemPacket

        when {
            myInfo != null -> router.value.configFlowManager.handleMyInfo(myInfo)

            // deviceuiConfig arrives immediately after my_info (STATE_SEND_UIDATA). It carries
            // the device's display, theme, node-filter, and other UI preferences.
            deviceUIConfig != null -> router.value.configHandler.handleDeviceUIConfig(deviceUIConfig)

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

            fileInfo != null -> router.value.configFlowManager.handleFileInfo(fileInfo)

            xmodemPacket != null -> router.value.xmodemManager.handleIncomingXModem(xmodemPacket)

            clientNotification != null -> handleClientNotification(clientNotification)

            // Firmware rebooted without a transport-level disconnect (common on serial/TCP).
            // Re-handshake immediately rather than waiting for the 30s stall guard.
            proto.rebooted != null -> {
                Logger.w { "Firmware rebooted (rebooted=${proto.rebooted}), re-initiating handshake" }
                router.value.configFlowManager.triggerWantConfig()
            }
        }
    }

    private fun handleClientNotification(cn: ClientNotification) {
        serviceRepository.setClientNotification(cn)

        scope.handledLaunch {
            val inform = cn.key_verification_number_inform
            val request = cn.key_verification_number_request
            val verificationFinal = cn.key_verification_final
            val (title, type) =
                when {
                    inform != null -> {
                        Logger.i { "Key verification inform from ${inform.remote_longname}" }
                        Pair(getStringSuspend(Res.string.key_verification_title), Notification.Type.Info)
                    }

                    request != null -> {
                        Logger.i { "Key verification request from ${request.remote_longname}" }
                        Pair(getStringSuspend(Res.string.key_verification_request_title), Notification.Type.Info)
                    }

                    verificationFinal != null -> {
                        Logger.i { "Key verification final from ${verificationFinal.remote_longname}" }
                        Pair(getStringSuspend(Res.string.key_verification_final_title), Notification.Type.Info)
                    }

                    cn.duplicated_public_key != null -> {
                        Logger.w { "Duplicated public key notification received" }
                        Pair(getStringSuspend(Res.string.duplicated_public_key_title), Notification.Type.Warning)
                    }

                    cn.low_entropy_key != null -> {
                        Logger.w { "Low entropy key notification received" }
                        Pair(getStringSuspend(Res.string.low_entropy_key_title), Notification.Type.Warning)
                    }

                    else -> Pair(getStringSuspend(Res.string.client_notification), Notification.Type.Info)
                }

            notificationManager.dispatch(
                Notification(title = title, type = type, message = cn.message, category = Notification.Category.Alert),
            )
        }
    }
}
