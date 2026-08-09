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
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.model.util.isOtaStatusNotification
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.FromRadioPacketHandler
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.repository.XModemManager
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
@Suppress("LongParameterList")
@Single
class FromRadioPacketHandlerImpl(
    private val serviceStateWriter: ServiceStateWriter,
    private val configFlowManager: Lazy<MeshConfigFlowManager>,
    private val configHandler: Lazy<MeshConfigHandler>,
    private val xmodemManager: Lazy<XModemManager>,
    private val mqttManager: MqttManager,
    private val packetHandler: PacketHandler,
    private val notificationManager: NotificationManager,
    private val lockdownCoordinator: LockdownCoordinator,
    private val firmwareUpdateStatusRepository: FirmwareUpdateStatusRepository,
    private val radioInterfaceService: RadioInterfaceService,
) : FromRadioPacketHandler {

    // Application-scoped coroutine context for suspend work (e.g. getStringSuspend).
    // This @Single lives for the entire app lifetime, so the SupervisorJob is never cancelled.
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    @Suppress("CyclomaticComplexMethod")
    override fun handleFromRadio(proto: FromRadio, session: RadioSessionContext) {
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
        val regionPresets = proto.region_presets
        val xmodemPacket = proto.xmodemPacket
        val lockdownStatus = proto.lockdown_status

        when {
            myInfo != null -> configFlowManager.value.handleMyInfo(myInfo, session)

            // deviceuiConfig arrives immediately after my_info (STATE_SEND_UIDATA). It carries
            // the device's display, theme, node-filter, and other UI preferences.
            deviceUIConfig != null -> configHandler.value.handleDeviceUIConfig(deviceUIConfig, session)

            metadata != null -> configFlowManager.value.handleLocalMetadata(metadata, session)

            nodeInfo != null -> {
                if (configFlowManager.value.handleNodeInfo(nodeInfo, session)) {
                    runIfSessionActive(session, "node-list progress") {
                        serviceStateWriter.setConnectionProgress("Nodes (${configFlowManager.value.newNodeCount})")
                    }
                }
            }

            configCompleteId != null -> {
                if (configFlowManager.value.handleConfigComplete(configCompleteId, session)) {
                    runIfSessionActive(session, "lockdown config completion") { lockdownCoordinator.onConfigComplete() }
                }
            }

            mqttProxyMessage != null ->
                runIfSessionActive(session, "MQTT proxy message") {
                    mqttManager.handleMqttProxyMessage(mqttProxyMessage)
                }

            queueStatus != null ->
                runIfSessionActive(session, "queue status") { packetHandler.handleQueueStatus(queueStatus) }

            config != null -> configHandler.value.handleDeviceConfig(config, session)

            moduleConfig != null -> configHandler.value.handleModuleConfig(moduleConfig, session)

            channel != null -> configHandler.value.handleChannel(channel, session)

            fileInfo != null -> configFlowManager.value.handleFileInfo(fileInfo, session)

            // region_presets arrives during the handshake (after metadata, before channels). It tells the client
            // which modem presets are legal per LoRa region. Absent on firmware < 2.8.
            regionPresets != null -> configHandler.value.handleRegionPresets(regionPresets, session)

            xmodemPacket != null ->
                runIfSessionActive(session, "XModem packet") { xmodemManager.value.handleIncomingXModem(xmodemPacket) }

            lockdownStatus != null ->
                runIfSessionActive(session, "lockdown status") {
                    lockdownCoordinator.handleLockdownStatus(lockdownStatus)
                }

            clientNotification != null -> handleClientNotification(clientNotification, session)

            // Firmware rebooted without a transport-level disconnect (common on serial/TCP).
            // Re-handshake immediately rather than waiting for the 30s stall guard.
            proto.rebooted != null -> {
                Logger.w { "Firmware rebooted (rebooted=${proto.rebooted}), re-initiating handshake" }
                configFlowManager.value.triggerWantConfig(session)
            }
        }
    }

    private fun runIfSessionActive(session: RadioSessionContext, operation: String, block: () -> Unit) {
        if (!radioInterfaceService.runIfSessionActive(session, block)) {
            Logger.d { "Discarding $operation from stale transport session" }
        }
    }

    private fun handleClientNotification(cn: ClientNotification, session: RadioSessionContext) {
        val admitted =
            radioInterfaceService.runIfSessionActive(session) { serviceStateWriter.setClientNotification(cn) }
        if (!admitted) {
            Logger.d { "Discarding client notification from stale transport session" }
            return
        }
        radioInterfaceService.launchSessionWork(
            scope = scope,
            session = session,
            onRejected = { Logger.d { "Skipping client alert from stale transport session" } },
        ) {
            dispatchClientNotification(cn)
        }
    }

    private suspend fun dispatchClientNotification(cn: ClientNotification) {
        if (cn.isOtaStatusNotification() && firmwareUpdateStatusRepository.status.value.isOtaUpdateActive) {
            Logger.i { "OTA status ClientNotification received; skipping duplicate generic alert" }
            return
        }

        val inform = cn.key_verification_number_inform
        val request = cn.key_verification_number_request
        val verificationFinal = cn.key_verification_final
        val (title, type) =
            when {
                inform != null -> {
                    Logger.i { "Key verification inform received" }
                    Pair(getStringSuspend(Res.string.key_verification_title), Notification.Type.Info)
                }

                request != null -> {
                    Logger.i { "Key verification request received" }
                    Pair(getStringSuspend(Res.string.key_verification_request_title), Notification.Type.Info)
                }

                verificationFinal != null -> {
                    Logger.i { "Key verification final received" }
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
