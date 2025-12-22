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
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.fromRadio
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FromRadioPacketHandler
@Inject
constructor(
    private val serviceRepository: ServiceRepository,
    private val router: MeshRouter,
    private val mqttManager: MeshMqttManager,
    private val packetHandler: PacketHandler,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val messageProcessor: Lazy<MeshMessageProcessor>,
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Suppress("CyclomaticComplexMethod")
    fun handleFromRadio(proto: MeshProtos.FromRadio, myNodeNum: Int?) {
        when (proto.payloadVariantCase) {
            MeshProtos.FromRadio.PayloadVariantCase.PACKET ->
                messageProcessor.get().handleReceivedMeshPacket(proto.packet, myNodeNum)
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
            MeshProtos.FromRadio.PayloadVariantCase.LOG_RECORD -> handleLogRecord(proto.logRecord)
            MeshProtos.FromRadio.PayloadVariantCase.REBOOTED -> handleRebooted(proto.rebooted)
            MeshProtos.FromRadio.PayloadVariantCase.XMODEMPACKET -> handleXmodemPacket(proto.xmodemPacket)
            MeshProtos.FromRadio.PayloadVariantCase.DEVICEUICONFIG -> handleDeviceUiConfig(proto.deviceuiConfig)
            MeshProtos.FromRadio.PayloadVariantCase.FILEINFO -> handleFileInfo(proto.fileInfo)
            else -> Timber.d("Processor handling ${proto.payloadVariantCase}")
        }
    }

    private fun handleLogRecord(logRecord: MeshProtos.LogRecord) {
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "LogRecord",
                received_date = System.currentTimeMillis(),
                raw_message = logRecord.toString(),
                fromRadio = fromRadio { this.logRecord = logRecord },
            ),
        )
    }

    private fun handleRebooted(rebooted: Boolean) {
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "Rebooted",
                received_date = System.currentTimeMillis(),
                raw_message = rebooted.toString(),
                fromRadio = fromRadio { this.rebooted = rebooted },
            ),
        )
    }

    private fun handleXmodemPacket(xmodemPacket: org.meshtastic.proto.XmodemProtos.XModem) {
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "XmodemPacket",
                received_date = System.currentTimeMillis(),
                raw_message = xmodemPacket.toString(),
                fromRadio = fromRadio { this.xmodemPacket = xmodemPacket },
            ),
        )
    }

    private fun handleDeviceUiConfig(deviceUiConfig: org.meshtastic.proto.DeviceUIProtos.DeviceUIConfig) {
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "DeviceUIConfig",
                received_date = System.currentTimeMillis(),
                raw_message = deviceUiConfig.toString(),
                fromRadio = fromRadio { this.deviceuiConfig = deviceUiConfig },
            ),
        )
    }

    private fun handleFileInfo(fileInfo: MeshProtos.FileInfo) {
        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "FileInfo",
                received_date = System.currentTimeMillis(),
                raw_message = fileInfo.toString(),
                fromRadio = fromRadio { this.fileInfo = fileInfo },
            ),
        )
    }

    private fun insertMeshLog(log: MeshLog): Job = scope.handledLaunch { meshLogRepository.get().insert(log) }
}
