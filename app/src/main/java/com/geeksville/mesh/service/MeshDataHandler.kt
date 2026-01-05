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

import android.util.Log
import co.touchlab.kermit.Logger
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.repository.radio.InterfaceId
import com.meshtastic.core.strings.getString
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.critical_alert
import org.meshtastic.core.strings.error_duty_cycle
import org.meshtastic.core.strings.unknown_username
import org.meshtastic.core.strings.waypoint_received
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.MeshPacket
import org.meshtastic.proto.PaxcountProtos
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.StoreAndForwardProtos
import org.meshtastic.proto.TelemetryProtos
import org.meshtastic.proto.copy
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Suppress("LongParameterList", "TooManyFunctions")
@Singleton
class MeshDataHandler
@Inject
constructor(
    private val nodeManager: MeshNodeManager,
    private val packetHandler: PacketHandler,
    private val serviceRepository: ServiceRepository,
    private val packetRepository: Lazy<PacketRepository>,
    private val serviceBroadcasts: MeshServiceBroadcasts,
    private val serviceNotifications: MeshServiceNotifications,
    private val analytics: PlatformAnalytics,
    private val dataMapper: MeshDataMapper,
    private val configHandler: MeshConfigHandler,
    private val configFlowManager: MeshConfigFlowManager,
    private val commandSender: MeshCommandSender,
    private val historyManager: MeshHistoryManager,
    private val meshPrefs: MeshPrefs,
    private val connectionManager: MeshConnectionManager,
    private val tracerouteHandler: MeshTracerouteHandler,
    private val neighborInfoHandler: MeshNeighborInfoHandler,
    private val radioConfigRepository: RadioConfigRepository,
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    private val rememberDataType =
        setOf(
            Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
            Portnums.PortNum.ALERT_APP_VALUE,
            Portnums.PortNum.WAYPOINT_APP_VALUE,
        )

    fun handleReceivedData(packet: MeshPacket, myNodeNum: Int, logUuid: String? = null, logInsertJob: Job? = null) {
        val dataPacket = dataMapper.toDataPacket(packet) ?: return
        val fromUs = myNodeNum == packet.from
        dataPacket.status = MessageStatus.RECEIVED

        val shouldBroadcast = handleDataPacket(packet, dataPacket, myNodeNum, fromUs, logUuid, logInsertJob)

        if (shouldBroadcast) {
            serviceBroadcasts.broadcastReceivedData(dataPacket)
        }
        analytics.track("num_data_receive", DataPair("num_data_receive", 1))
    }

    private fun handleDataPacket(
        packet: MeshPacket,
        dataPacket: DataPacket,
        myNodeNum: Int,
        fromUs: Boolean,
        logUuid: String?,
        logInsertJob: Job?,
    ): Boolean {
        var shouldBroadcast = !fromUs
        when (packet.decoded.portnumValue) {
            Portnums.PortNum.TEXT_MESSAGE_APP_VALUE -> handleTextMessage(packet, dataPacket, myNodeNum)
            Portnums.PortNum.ALERT_APP_VALUE -> rememberDataPacket(dataPacket, myNodeNum)
            Portnums.PortNum.WAYPOINT_APP_VALUE -> handleWaypoint(packet, dataPacket, myNodeNum)
            Portnums.PortNum.POSITION_APP_VALUE -> handlePosition(packet, dataPacket, myNodeNum)
            Portnums.PortNum.NODEINFO_APP_VALUE -> if (!fromUs) handleNodeInfo(packet)
            Portnums.PortNum.TELEMETRY_APP_VALUE -> handleTelemetry(packet, dataPacket, myNodeNum)
            else -> shouldBroadcast = handleSpecializedDataPacket(packet, dataPacket, myNodeNum, logUuid, logInsertJob)
        }
        return shouldBroadcast
    }

    private fun handleSpecializedDataPacket(
        packet: MeshPacket,
        dataPacket: DataPacket,
        myNodeNum: Int,
        logUuid: String?,
        logInsertJob: Job?,
    ): Boolean {
        var shouldBroadcast = false
        when (packet.decoded.portnumValue) {
            Portnums.PortNum.TRACEROUTE_APP_VALUE -> {
                tracerouteHandler.handleTraceroute(packet, logUuid, logInsertJob)
                shouldBroadcast = false
            }
            Portnums.PortNum.ROUTING_APP_VALUE -> {
                handleRouting(packet, dataPacket)
                shouldBroadcast = true
            }

            Portnums.PortNum.PAXCOUNTER_APP_VALUE -> {
                handlePaxCounter(packet)
                shouldBroadcast = false
            }

            Portnums.PortNum.STORE_FORWARD_APP_VALUE -> {
                handleStoreAndForward(packet, dataPacket, myNodeNum)
                shouldBroadcast = false
            }

            Portnums.PortNum.STORE_FORWARD_PLUSPLUS_APP_VALUE -> {
                handleStoreForwardPlusPlus(packet)
                shouldBroadcast = false
            }

            Portnums.PortNum.ADMIN_APP_VALUE -> {
                handleAdminMessage(packet, myNodeNum)
                shouldBroadcast = false
            }

            Portnums.PortNum.NEIGHBORINFO_APP_VALUE -> {
                neighborInfoHandler.handleNeighborInfo(packet)
                shouldBroadcast = true
            }

            Portnums.PortNum.RANGE_TEST_APP_VALUE,
            Portnums.PortNum.DETECTION_SENSOR_APP_VALUE,
            -> {
                handleRangeTest(dataPacket, myNodeNum)
                shouldBroadcast = false
            }
        }
        return shouldBroadcast
    }

    private fun handleRangeTest(dataPacket: DataPacket, myNodeNum: Int) {
        val u = dataPacket.copy(dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE)
        rememberDataPacket(u, myNodeNum)
    }

    private fun handleStoreAndForward(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val u = StoreAndForwardProtos.StoreAndForward.parseFrom(packet.decoded.payload)
        handleReceivedStoreAndForward(dataPacket, u, myNodeNum)
    }

    private fun handleStoreForwardPlusPlus(packet: MeshPacket) {
        val sfpp = MeshProtos.StoreForwardPlusPlus.parseFrom(packet.decoded.payload)
        Logger.d { "Received StoreForwardPlusPlus packet: $sfpp" }
    }

    private fun handlePaxCounter(packet: MeshPacket) {
        val p = PaxcountProtos.Paxcount.parseFrom(packet.decoded.payload)
        nodeManager.handleReceivedPaxcounter(packet.from, p)
    }

    private fun handlePosition(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val p = MeshProtos.Position.parseFrom(packet.decoded.payload)
        nodeManager.handleReceivedPosition(packet.from, myNodeNum, p, dataPacket.time)
    }

    private fun handleWaypoint(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val u = MeshProtos.Waypoint.parseFrom(packet.decoded.payload)
        if (u.lockedTo != 0 && u.lockedTo != packet.from) return
        val currentSecond = (System.currentTimeMillis() / MILLISECONDS_IN_SECOND).toInt()
        rememberDataPacket(dataPacket, myNodeNum, updateNotification = u.expire > currentSecond)
    }

    private fun handleAdminMessage(packet: MeshPacket, myNodeNum: Int) {
        val u = AdminProtos.AdminMessage.parseFrom(packet.decoded.payload)
        commandSender.setSessionPasskey(u.sessionPasskey)

        if (packet.from == myNodeNum) {
            when (u.payloadVariantCase) {
                AdminProtos.AdminMessage.PayloadVariantCase.GET_CONFIG_RESPONSE ->
                    configHandler.handleDeviceConfig(u.getConfigResponse)

                AdminProtos.AdminMessage.PayloadVariantCase.GET_CHANNEL_RESPONSE ->
                    configHandler.handleChannel(u.getChannelResponse)

                else -> {}
            }
        }

        if (u.payloadVariantCase == AdminProtos.AdminMessage.PayloadVariantCase.GET_DEVICE_METADATA_RESPONSE) {
            if (packet.from == myNodeNum) {
                configFlowManager.handleLocalMetadata(u.getDeviceMetadataResponse)
            } else {
                nodeManager.insertMetadata(packet.from, u.getDeviceMetadataResponse)
            }
        }
    }

    private fun handleTextMessage(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        if (packet.decoded.replyId != 0 && packet.decoded.emoji != 0) {
            rememberReaction(packet)
        } else {
            rememberDataPacket(dataPacket, myNodeNum)
        }
    }

    private fun handleNodeInfo(packet: MeshPacket) {
        val u =
            MeshProtos.User.parseFrom(packet.decoded.payload).copy {
                if (isLicensed) clearPublicKey()
                if (packet.viaMqtt) longName = "$longName (MQTT)"
            }
        nodeManager.handleReceivedUser(packet.from, u, packet.channel)
    }

    private fun handleTelemetry(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val t =
            TelemetryProtos.Telemetry.parseFrom(packet.decoded.payload).copy {
                if (time == 0) time = (dataPacket.time.milliseconds.inWholeSeconds).toInt()
            }
        val fromNum = packet.from
        val isRemote = (fromNum != myNodeNum)
        if (!isRemote) {
            connectionManager.updateTelemetry(t)
        }

        nodeManager.updateNodeInfo(fromNum) { nodeEntity ->
            when {
                t.hasDeviceMetrics() -> {
                    nodeEntity.deviceTelemetry = t
                    if (fromNum == myNodeNum || (isRemote && nodeEntity.isFavorite)) {
                        if (
                            t.deviceMetrics.voltage > BATTERY_PERCENT_UNSUPPORTED &&
                            t.deviceMetrics.batteryLevel <= BATTERY_PERCENT_LOW_THRESHOLD
                        ) {
                            if (shouldBatteryNotificationShow(fromNum, t, myNodeNum)) {
                                serviceNotifications.showOrUpdateLowBatteryNotification(nodeEntity, isRemote)
                            }
                        } else {
                            if (batteryPercentCooldowns.containsKey(fromNum)) {
                                batteryPercentCooldowns.remove(fromNum)
                            }
                            serviceNotifications.cancelLowBatteryNotification(nodeEntity)
                        }
                    }
                }

                t.hasEnvironmentMetrics() -> nodeEntity.environmentTelemetry = t
                t.hasPowerMetrics() -> nodeEntity.powerTelemetry = t
            }
        }
    }

    private fun shouldBatteryNotificationShow(fromNum: Int, t: TelemetryProtos.Telemetry, myNodeNum: Int): Boolean {
        val isRemote = (fromNum != myNodeNum)
        var shouldDisplay = false
        var forceDisplay = false
        when {
            t.deviceMetrics.batteryLevel <= BATTERY_PERCENT_CRITICAL_THRESHOLD -> {
                shouldDisplay = true
                forceDisplay = true
            }

            t.deviceMetrics.batteryLevel == BATTERY_PERCENT_LOW_THRESHOLD -> shouldDisplay = true
            t.deviceMetrics.batteryLevel.mod(BATTERY_PERCENT_LOW_DIVISOR) == 0 && !isRemote -> shouldDisplay = true

            isRemote -> shouldDisplay = true
        }
        if (shouldDisplay) {
            val now = System.currentTimeMillis() / MILLISECONDS_IN_SECOND
            if (!batteryPercentCooldowns.containsKey(fromNum)) batteryPercentCooldowns[fromNum] = 0
            if ((now - batteryPercentCooldowns[fromNum]!!) >= BATTERY_PERCENT_COOLDOWN_SECONDS || forceDisplay) {
                batteryPercentCooldowns[fromNum] = now
                return true
            }
        }
        return false
    }

    private fun handleRouting(packet: MeshPacket, dataPacket: DataPacket) {
        val r = MeshProtos.Routing.parseFrom(packet.decoded.payload)
        if (r.errorReason == MeshProtos.Routing.Error.DUTY_CYCLE_LIMIT) {
            serviceRepository.setErrorMessage(getString(Res.string.error_duty_cycle))
        }
        handleAckNak(
            packet.decoded.requestId,
            dataMapper.toNodeID(packet.from),
            r.errorReasonValue,
            dataPacket.relayNode,
        )
        packetHandler.removeResponse(packet.decoded.requestId, complete = true)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun handleAckNak(requestId: Int, fromId: String, routingError: Int, relayNode: Int?) {
        scope.handledLaunch {
            val isAck = routingError == MeshProtos.Routing.Error.NONE_VALUE
            val p = packetRepository.get().getPacketById(requestId)
            val reaction = packetRepository.get().getReactionByPacketId(requestId)

            val isMaxRetransmit = routingError == MeshProtos.Routing.Error.MAX_RETRANSMIT_VALUE
            val shouldRetry =
                isMaxRetransmit &&
                    p != null &&
                    p.port_num == Portnums.PortNum.TEXT_MESSAGE_APP_VALUE &&
                    p.data.from == DataPacket.ID_LOCAL &&
                    p.data.retryCount < MAX_RETRY_ATTEMPTS

            val shouldRetryReaction =
                isMaxRetransmit &&
                    reaction != null &&
                    reaction.userId == DataPacket.ID_LOCAL &&
                    reaction.retryCount < MAX_RETRY_ATTEMPTS &&
                    reaction.to != null

            Logger.d {
                val retryInfo =
                    "packetId=${p?.packetId ?: reaction?.packetId} dataId=${p?.data?.id} retry=${p?.data?.retryCount ?: reaction?.retryCount}"
                val statusInfo = "status=${p?.data?.status ?: reaction?.status}"
                "[ackNak] req=$requestId routeErr=$routingError isAck=$isAck " +
                    "maxRetransmit=$isMaxRetransmit shouldRetry=$shouldRetry reaction=$shouldRetryReaction $retryInfo $statusInfo"
            }

            if (shouldRetry && p != null) {
                val newRetryCount = p.data.retryCount + 1
                val newId = commandSender.generatePacketId()
                val updatedData =
                    p.data.copy(id = newId, status = MessageStatus.QUEUED, retryCount = newRetryCount, relayNode = null)
                val updatedPacket =
                    p.copy(packetId = newId, data = updatedData, routingError = MeshProtos.Routing.Error.NONE_VALUE)
                packetRepository.get().update(updatedPacket)

                Logger.w { "[ackNak] retrying req=$requestId newId=$newId retry=$newRetryCount" }

                delay(RETRY_DELAY_MS)
                commandSender.sendData(updatedData)
                return@handledLaunch
            }

            if (shouldRetryReaction && reaction != null) {
                val newRetryCount = reaction.retryCount + 1
                val newId = commandSender.generatePacketId()

                val reactionPacket = DataPacket(
                    to = reaction.to,
                    channel = reaction.channel,
                    bytes = reaction.emoji.toByteArray(Charsets.UTF_8),
                    dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                    replyId = reaction.replyId,
                    wantAck = true,
                    emoji = reaction.emoji.codePointAt(0),
                    id = newId,
                    retryCount = newRetryCount,
                )

                val updatedReaction = reaction.copy(
                    packetId = newId,
                    status = MessageStatus.QUEUED,
                    retryCount = newRetryCount,
                    relayNode = null,
                    routingError = MeshProtos.Routing.Error.NONE_VALUE,
                )
                packetRepository.get().updateReaction(updatedReaction)

                Logger.w { "[ackNak] retrying reaction req=$requestId newId=$newId retry=$newRetryCount" }

                delay(RETRY_DELAY_MS)
                commandSender.sendData(reactionPacket)
                return@handledLaunch
            }

            val m =
                when {
                    isAck && (fromId == p?.data?.to || fromId == reaction?.to) -> MessageStatus.RECEIVED
                    isAck -> MessageStatus.DELIVERED
                    else -> MessageStatus.ERROR
                }
            if (p != null && p.data.status != MessageStatus.RECEIVED) {
                p.data.status = m
                p.routingError = routingError
                if (isAck) {
                    p.data.relays += 1
                }
                p.data.relayNode = relayNode
                packetRepository.get().update(p)
            }

            reaction?.let { r ->
                if (r.status != MessageStatus.RECEIVED) {
                    var updated = r.copy(status = m, routingError = routingError, relayNode = relayNode)
                    if (isAck) {
                        updated = updated.copy(relays = updated.relays + 1)
                    }
                    packetRepository.get().updateReaction(updated)
                }
            }

            serviceBroadcasts.broadcastMessageStatus(requestId, m)
        }
    }

    private fun handleReceivedStoreAndForward(
        dataPacket: DataPacket,
        s: StoreAndForwardProtos.StoreAndForward,
        myNodeNum: Int,
    ) {
        Logger.d { "StoreAndForward: ${s.variantCase} ${s.rr} from ${dataPacket.from}" }
        val transport = currentTransport()
        val lastRequest =
            if (s.variantCase == StoreAndForwardProtos.StoreAndForward.VariantCase.HISTORY) {
                s.history.lastRequest
            } else {
                0
            }
        val baseContext = "transport=$transport from=${dataPacket.from}"
        historyLog { "rxStoreForward $baseContext variant=${s.variantCase} rr=${s.rr} lastRequest=$lastRequest" }
        when (s.variantCase) {
            StoreAndForwardProtos.StoreAndForward.VariantCase.STATS -> {
                val u =
                    dataPacket.copy(
                        bytes = s.stats.toString().encodeToByteArray(),
                        dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                    )
                rememberDataPacket(u, myNodeNum)
            }

            StoreAndForwardProtos.StoreAndForward.VariantCase.HISTORY -> {
                val history = s.history
                val historySummary =
                    "routerHistory $baseContext messages=${history.historyMessages} " +
                        "window=${history.window} lastRequest=${history.lastRequest}"
                historyLog(Log.DEBUG) { historySummary }
                val text =
                    """
                    Total messages: ${s.history.historyMessages}
                    History window: ${s.history.window.milliseconds.inWholeMinutes} min
                    Last request: ${s.history.lastRequest}
                """
                        .trimIndent()
                val u =
                    dataPacket.copy(
                        bytes = text.encodeToByteArray(),
                        dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
                    )
                rememberDataPacket(u, myNodeNum)
                historyManager.updateStoreForwardLastRequest("router_history", s.history.lastRequest, transport)
            }

            StoreAndForwardProtos.StoreAndForward.VariantCase.TEXT -> {
                if (s.rr == StoreAndForwardProtos.StoreAndForward.RequestResponse.ROUTER_TEXT_BROADCAST) {
                    dataPacket.to = DataPacket.ID_BROADCAST
                }
                val textLog =
                    "rxText $baseContext id=${dataPacket.id} ts=${dataPacket.time} " +
                        "to=${dataPacket.to} decision=remember"
                historyLog(Log.DEBUG) { textLog }
                val u =
                    dataPacket.copy(bytes = s.text.toByteArray(), dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE)
                rememberDataPacket(u, myNodeNum)
            }

            else -> {}
        }
    }

    fun rememberDataPacket(dataPacket: DataPacket, myNodeNum: Int, updateNotification: Boolean = true) {
        if (dataPacket.dataType !in rememberDataType) return
        val fromLocal = dataPacket.from == DataPacket.ID_LOCAL
        val toBroadcast = dataPacket.to == DataPacket.ID_BROADCAST
        val contactId = if (fromLocal || toBroadcast) dataPacket.to else dataPacket.from

        // contactKey: unique contact key filter (channel)+(nodeId)
        val contactKey = "${dataPacket.channel}$contactId"

        val packetToSave =
            Packet(
                uuid = 0L,
                myNodeNum = myNodeNum,
                packetId = dataPacket.id,
                port_num = dataPacket.dataType,
                contact_key = contactKey,
                received_time = System.currentTimeMillis(),
                read = fromLocal,
                data = dataPacket,
                snr = dataPacket.snr,
                rssi = dataPacket.rssi,
                hopsAway = dataPacket.hopsAway,
                replyId = dataPacket.replyId ?: 0,
            )
        scope.handledLaunch {
            packetRepository.get().apply {
                insert(packetToSave)
                val isMuted = getContactSettings(contactKey).isMuted
                if (!isMuted) {
                    if (packetToSave.port_num == Portnums.PortNum.ALERT_APP_VALUE) {
                        serviceNotifications.showAlertNotification(
                            contactKey,
                            getSenderName(dataPacket),
                            dataPacket.alert ?: getString(Res.string.critical_alert),
                        )
                    } else if (updateNotification) {
                        scope.handledLaunch { updateNotification(contactKey, dataPacket) }
                    }
                }
            }
        }
    }

    private fun getSenderName(packet: DataPacket): String {
        if (packet.from == DataPacket.ID_LOCAL) {
            val myId = nodeManager.getMyId()
            return nodeManager.nodeDBbyID[myId]?.user?.longName ?: getString(Res.string.unknown_username)
        }
        return nodeManager.nodeDBbyID[packet.from]?.user?.longName ?: getString(Res.string.unknown_username)
    }

    private suspend fun updateNotification(contactKey: String, dataPacket: DataPacket) {
        when (dataPacket.dataType) {
            Portnums.PortNum.TEXT_MESSAGE_APP_VALUE -> {
                val message = dataPacket.text!!
                val channelName =
                    if (dataPacket.to == DataPacket.ID_BROADCAST) {
                        radioConfigRepository.channelSetFlow.first().settingsList.getOrNull(dataPacket.channel)?.name
                    } else {
                        null
                    }
                serviceNotifications.updateMessageNotification(
                    contactKey,
                    getSenderName(dataPacket),
                    message,
                    dataPacket.to == DataPacket.ID_BROADCAST,
                    channelName,
                )
            }

            Portnums.PortNum.WAYPOINT_APP_VALUE -> {
                val message = getString(Res.string.waypoint_received, dataPacket.waypoint!!.name)
                serviceNotifications.updateWaypointNotification(
                    contactKey,
                    getSenderName(dataPacket),
                    message,
                    dataPacket.waypoint!!.id,
                )
            }

            else -> return
        }
    }

    private fun rememberReaction(packet: MeshPacket) = scope.handledLaunch {
        val emoji = packet.decoded.payload.toByteArray().decodeToString()
        val reaction =
            ReactionEntity(
                replyId = packet.decoded.replyId,
                userId = dataMapper.toNodeID(packet.from),
                emoji = emoji,
                timestamp = System.currentTimeMillis(),
                snr = packet.rxSnr,
                rssi = packet.rxRssi,
                hopsAway =
                if (packet.hopStart == 0 || packet.hopLimit > packet.hopStart) {
                    HOPS_AWAY_UNAVAILABLE
                } else {
                    packet.hopStart - packet.hopLimit
                },
            )
        packetRepository.get().insertReaction(reaction)

        // Find the original packet to get the contactKey
        packetRepository.get().getPacketByPacketId(packet.decoded.replyId)?.let { original ->
            val contactKey = original.packet.contact_key
            val isMuted = packetRepository.get().getContactSettings(contactKey).isMuted
            if (!isMuted) {
                val channelName =
                    if (original.packet.data.to == DataPacket.ID_BROADCAST) {
                        radioConfigRepository.channelSetFlow
                            .first()
                            .settingsList
                            .getOrNull(original.packet.data.channel)
                            ?.name
                    } else {
                        null
                    }
                serviceNotifications.updateReactionNotification(
                    contactKey,
                    getSenderName(dataMapper.toDataPacket(packet)!!),
                    emoji,
                    original.packet.data.to == DataPacket.ID_BROADCAST,
                    channelName,
                )
            }
        }
    }

    private fun currentTransport(address: String? = meshPrefs.deviceAddress): String = when (address?.firstOrNull()) {
        InterfaceId.BLUETOOTH.id -> "BLE"
        InterfaceId.TCP.id -> "TCP"
        InterfaceId.SERIAL.id -> "Serial"
        InterfaceId.MOCK.id -> "Mock"
        InterfaceId.NOP.id -> "NOP"
        else -> "Unknown"
    }

    private inline fun historyLog(
        priority: Int = Log.INFO,
        throwable: Throwable? = null,
        crossinline message: () -> String,
    ) {
        if (!BuildConfig.DEBUG) return
        val logger = Logger.withTag("HistoryReplay")
        val msg = message()
        when (priority) {
            Log.VERBOSE -> logger.v(throwable) { msg }
            Log.DEBUG -> logger.d(throwable) { msg }
            Log.INFO -> logger.i(throwable) { msg }
            Log.WARN -> logger.w(throwable) { msg }
            Log.ERROR -> logger.e(throwable) { msg }
            else -> logger.i(throwable) { msg }
        }
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val RETRY_DELAY_MS = 5_000L
        private const val MILLISECONDS_IN_SECOND = 1000L
        private const val HOPS_AWAY_UNAVAILABLE = -1

        private const val BATTERY_PERCENT_UNSUPPORTED = 0.0
        private const val BATTERY_PERCENT_LOW_THRESHOLD = 20
        private const val BATTERY_PERCENT_LOW_DIVISOR = 5
        private const val BATTERY_PERCENT_CRITICAL_THRESHOLD = 5
        private const val BATTERY_PERCENT_COOLDOWN_SECONDS = 1500
        private val batteryPercentCooldowns = ConcurrentHashMap<Int, Long>()
    }
}
