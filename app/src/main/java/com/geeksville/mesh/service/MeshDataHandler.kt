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
import kotlinx.coroutines.flow.first
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.util.SfppHasher
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.RetryEvent
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.critical_alert
import org.meshtastic.core.strings.error_duty_cycle
import org.meshtastic.core.strings.unknown_username
import org.meshtastic.core.strings.waypoint_received
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.StoreAndForward
import org.meshtastic.proto.StoreForwardPlusPlus
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import org.meshtastic.proto.Waypoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
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
        setOf(PortNum.TEXT_MESSAGE_APP.value, PortNum.ALERT_APP.value, PortNum.WAYPOINT_APP.value)

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
        val portNum = packet.decoded?.portnum ?: PortNum.UNKNOWN_APP
        when (portNum) {
            PortNum.TEXT_MESSAGE_APP -> handleTextMessage(packet, dataPacket, myNodeNum)
            PortNum.ALERT_APP -> rememberDataPacket(dataPacket, myNodeNum)
            PortNum.WAYPOINT_APP -> handleWaypoint(packet, dataPacket, myNodeNum)
            PortNum.POSITION_APP -> handlePosition(packet, dataPacket, myNodeNum)
            PortNum.NODEINFO_APP -> if (!fromUs) handleNodeInfo(packet)
            PortNum.TELEMETRY_APP -> handleTelemetry(packet, dataPacket, myNodeNum)
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
        val portNum = packet.decoded?.portnum ?: PortNum.UNKNOWN_APP
        when (portNum) {
            PortNum.TRACEROUTE_APP -> {
                tracerouteHandler.handleTraceroute(packet, logUuid, logInsertJob)
                shouldBroadcast = false
            }
            PortNum.ROUTING_APP -> {
                handleRouting(packet, dataPacket)
                shouldBroadcast = true
            }

            PortNum.PAXCOUNTER_APP -> {
                handlePaxCounter(packet)
                shouldBroadcast = false
            }

            PortNum.STORE_FORWARD_APP -> {
                handleStoreAndForward(packet, dataPacket, myNodeNum)
                shouldBroadcast = false
            }

            PortNum.STORE_FORWARD_PLUSPLUS_APP -> {
                handleStoreForwardPlusPlus(packet)
                shouldBroadcast = false
            }

            PortNum.ADMIN_APP -> {
                handleAdminMessage(packet, myNodeNum)
                shouldBroadcast = false
            }

            PortNum.NEIGHBORINFO_APP -> {
                neighborInfoHandler.handleNeighborInfo(packet)
                shouldBroadcast = true
            }

            PortNum.RANGE_TEST_APP,
            PortNum.DETECTION_SENSOR_APP,
            -> {
                handleRangeTest(dataPacket, myNodeNum)
                shouldBroadcast = false
            }
            else -> {}
        }
        return shouldBroadcast
    }

    private fun handleRangeTest(dataPacket: DataPacket, myNodeNum: Int) {
        val u = dataPacket.copy(dataType = PortNum.TEXT_MESSAGE_APP.value)
        rememberDataPacket(u, myNodeNum)
    }

    private fun handleStoreAndForward(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: okio.ByteString.EMPTY
        val u = StoreAndForward.ADAPTER.decode(payload)
        handleReceivedStoreAndForward(dataPacket, u, myNodeNum)
    }

    @Suppress("LongMethod")
    private fun handleStoreForwardPlusPlus(packet: MeshPacket) {
        val payload = packet.decoded?.payload ?: okio.ByteString.EMPTY
        val sfpp = StoreForwardPlusPlus.ADAPTER.decode(payload)
        Logger.d { "Received StoreForwardPlusPlus packet: $sfpp" }

        val msgType = sfpp.sfpp_message_type
        if (
            msgType == StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE ||
            msgType == StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE_FIRSTHALF ||
            msgType == StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE_SECONDHALF
        ) {
            val isFragment = msgType != StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE

            // If it has a commit hash, it's already on the chain (Confirmed)
            // Otherwise it's still being routed via SF++ (Routing)
            val status = if (sfpp.commit_hash.size == 0) MessageStatus.SFPP_ROUTING else MessageStatus.SFPP_CONFIRMED

            // Prefer a full 16-byte hash calculated from the message bytes if available
            // But only if it's NOT a fragment, otherwise the calculated hash would be wrong
            val hash =
                when {
                    sfpp.message_hash.size != 0 -> sfpp.message_hash.toByteArray()
                    !isFragment && sfpp.message.size != 0 -> {
                        SfppHasher.computeMessageHash(
                            encryptedPayload = sfpp.message.toByteArray(),
                            // Map 0 back to NODENUM_BROADCAST to match firmware hash calculation
                            to = if (sfpp.encapsulated_to == 0) DataPacket.NODENUM_BROADCAST else sfpp.encapsulated_to,
                            from = sfpp.encapsulated_from,
                            id = sfpp.encapsulated_id,
                        )
                    }
                    else -> null
                } ?: return

            Logger.d {
                "SFPP updateStatus: packetId=${sfpp.encapsulated_id} from=${sfpp.encapsulated_from} " +
                    "to=${sfpp.encapsulated_to} myNodeNum=${nodeManager.myNodeNum} status=$status"
            }
            scope.handledLaunch {
                packetRepository
                    .get()
                    .updateSFPPStatus(
                        packetId = sfpp.encapsulated_id,
                        from = sfpp.encapsulated_from,
                        to = sfpp.encapsulated_to,
                        hash = hash,
                        status = status,
                        rxTime = sfpp.encapsulated_rxtime.toLong() and 0xFFFFFFFFL,
                        myNodeNum = nodeManager.myNodeNum,
                    )
                serviceBroadcasts.broadcastMessageStatus(sfpp.encapsulated_id, status)
            }
        } else if (msgType == StoreForwardPlusPlus.SFPP_message_type.CANON_ANNOUNCE) {
            scope.handledLaunch {
                packetRepository
                    .get()
                    .updateSFPPStatusByHash(
                        hash = sfpp.message_hash.toByteArray(),
                        status = MessageStatus.SFPP_CONFIRMED,
                        rxTime = sfpp.encapsulated_rxtime.toLong() and 0xFFFFFFFFL,
                    )
            }
        } else if (msgType == StoreForwardPlusPlus.SFPP_message_type.CHAIN_QUERY) {
            Logger.i { "SF++: Node ${packet.from} is querying chain status" }
        } else if (msgType == StoreForwardPlusPlus.SFPP_message_type.LINK_REQUEST) {
            Logger.i { "SF++: Node ${packet.from} is requesting links" }
        }
    }

    private fun handlePaxCounter(packet: MeshPacket) {
        val payload = packet.decoded?.payload ?: okio.ByteString.EMPTY
        val p = Paxcount.ADAPTER.decode(payload)
        nodeManager.handleReceivedPaxcounter(packet.from, p)
    }

    private fun handlePosition(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: okio.ByteString.EMPTY
        val p = org.meshtastic.proto.Position.ADAPTER.decode(payload)
        nodeManager.handleReceivedPosition(packet.from, myNodeNum, p, dataPacket.time)
    }

    private fun handleWaypoint(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: okio.ByteString.EMPTY
        val u = Waypoint.ADAPTER.decode(payload)
        if (u.locked_to != 0 && u.locked_to != packet.from) return
        val currentSecond = (System.currentTimeMillis() / MILLISECONDS_IN_SECOND).toInt()
        rememberDataPacket(dataPacket, myNodeNum, updateNotification = u.expire > currentSecond)
    }

    private fun handleAdminMessage(packet: MeshPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: okio.ByteString.EMPTY
        val u = AdminMessage.ADAPTER.decode(payload)
        commandSender.setSessionPasskey(u.session_passkey)

        if (packet.from == myNodeNum) {
            u.get_config_response?.let { configHandler.handleDeviceConfig(it) }
            u.get_channel_response?.let { configHandler.handleChannel(it) }
        }

        u.get_device_metadata_response?.let { metadata ->
            if (packet.from == myNodeNum) {
                configFlowManager.handleLocalMetadata(metadata)
            } else {
                nodeManager.insertMetadata(packet.from, metadata)
            }
        }
    }

    private fun handleTextMessage(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        if ((packet.decoded?.reply_id ?: 0) != 0 && (packet.decoded?.emoji ?: 0) != 0) {
            rememberReaction(packet)
        } else {
            rememberDataPacket(dataPacket, myNodeNum)
        }
    }

    private fun handleNodeInfo(packet: MeshPacket) {
        val payload = packet.decoded?.payload ?: okio.ByteString.EMPTY
        val u =
            User.ADAPTER.decode(payload).let { user ->
                user.copy(
                    public_key = if (user.is_licensed) okio.ByteString.EMPTY else user.public_key,
                    long_name = if (packet.via_mqtt) "${user.long_name} (MQTT)" else user.long_name,
                )
            }
        nodeManager.handleReceivedUser(packet.from, u, packet.channel)
    }

    private fun handleTelemetry(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: okio.ByteString.EMPTY
        val t =
            Telemetry.ADAPTER.decode(payload).let { tel ->
                if (tel.time == 0) tel.copy(time = (dataPacket.time.milliseconds.inWholeSeconds).toInt()) else tel
            }
        val fromNum = packet.from
        val isRemote = (fromNum != myNodeNum)
        if (!isRemote) {
            connectionManager.updateTelemetry(t)
        }

        nodeManager.updateNodeInfo(fromNum) { nodeEntity ->
            when {
                t.device_metrics != null -> {
                    nodeEntity.deviceTelemetry = t
                    if (fromNum == myNodeNum || (isRemote && nodeEntity.isFavorite)) {
                        val dm = t.device_metrics
                        if (
                            dm != null &&
                            (dm.voltage ?: 0f) > BATTERY_PERCENT_UNSUPPORTED &&
                            (dm.battery_level ?: 0) <= BATTERY_PERCENT_LOW_THRESHOLD
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

                t.environment_metrics != null -> nodeEntity.environmentTelemetry = t
                t.power_metrics != null -> nodeEntity.powerTelemetry = t
            }
        }
    }

    private fun shouldBatteryNotificationShow(fromNum: Int, t: Telemetry, myNodeNum: Int): Boolean {
        val isRemote = (fromNum != myNodeNum)
        var shouldDisplay = false
        var forceDisplay = false
        val dm = t.device_metrics ?: return false
        val batteryLevel = dm.battery_level ?: 0
        when {
            batteryLevel <= BATTERY_PERCENT_CRITICAL_THRESHOLD -> {
                shouldDisplay = true
                forceDisplay = true
            }

            batteryLevel == BATTERY_PERCENT_LOW_THRESHOLD -> shouldDisplay = true
            batteryLevel.mod(BATTERY_PERCENT_LOW_DIVISOR) == 0 && !isRemote -> shouldDisplay = true

            isRemote -> shouldDisplay = true
        }
        if (shouldDisplay) {
            val now = System.currentTimeMillis() / MILLISECONDS_IN_SECOND
            val lastCooldown = batteryPercentCooldowns[fromNum] ?: 0L
            if ((now - lastCooldown) >= BATTERY_PERCENT_COOLDOWN_SECONDS || forceDisplay) {
                batteryPercentCooldowns[fromNum] = now
                return true
            }
        }
        return false
    }

    private fun handleRouting(packet: MeshPacket, dataPacket: DataPacket) {
        val payload = packet.decoded?.payload ?: okio.ByteString.EMPTY
        val r = org.meshtastic.proto.Routing.ADAPTER.decode(payload)
        if (r.error_reason == org.meshtastic.proto.Routing.Error.DUTY_CYCLE_LIMIT) {
            serviceRepository.setErrorMessage(getString(Res.string.error_duty_cycle))
        }
        val requestId = packet.decoded?.request_id ?: 0
        handleAckNak(requestId, dataMapper.toNodeID(packet.from), r.error_reason?.value ?: 0, dataPacket.relayNode)
        packetHandler.removeResponse(requestId, complete = true)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun handleAckNak(requestId: Int, fromId: String, routingError: Int, relayNode: Int?) {
        scope.handledLaunch {
            val isAck = routingError == org.meshtastic.proto.Routing.Error.NONE.value
            val p = packetRepository.get().getPacketById(requestId)
            val reaction = packetRepository.get().getReactionByPacketId(requestId)

            val isMaxRetransmit = routingError == org.meshtastic.proto.Routing.Error.MAX_RETRANSMIT.value
            val shouldRetry =
                isMaxRetransmit &&
                    p != null &&
                    p.port_num == PortNum.TEXT_MESSAGE_APP.value &&
                    (p.data.from == DataPacket.ID_LOCAL || p.data.from == nodeManager.getMyId()) &&
                    p.data.retryCount < MAX_RETRY_ATTEMPTS

            val shouldRetryReaction =
                isMaxRetransmit &&
                    reaction != null &&
                    (reaction.userId == DataPacket.ID_LOCAL || reaction.userId == nodeManager.getMyId()) &&
                    reaction.retryCount < MAX_RETRY_ATTEMPTS &&
                    reaction.to != null
            @Suppress("MaxLineLength")
            Logger.d {
                val retryInfo =
                    "packetId=${p?.packetId ?: reaction?.packetId} dataId=${p?.data?.id} retry=${p?.data?.retryCount ?: reaction?.retryCount}"
                val statusInfo = "status=${p?.data?.status ?: reaction?.status}"
                "[ackNak] req=$requestId routeErr=$routingError isAck=$isAck " +
                    "maxRetransmit=$isMaxRetransmit shouldRetry=$shouldRetry reaction=$shouldRetryReaction $retryInfo $statusInfo"
            }

            if (shouldRetry) {
                val newRetryCount = p.data.retryCount + 1

                // Emit retry event to UI and wait for user response
                val retryEvent =
                    RetryEvent.MessageRetry(
                        packetId = requestId,
                        text = p.data.text ?: "",
                        attemptNumber = newRetryCount,
                        maxAttempts = MAX_RETRY_ATTEMPTS + 1, // +1 for initial attempt
                    )

                Logger.w { "[ackNak] requesting retry for req=$requestId retry=$newRetryCount" }
                Log.d("MeshDataHandler", "[ackNak] Emitting retry event for req=$requestId retry=$newRetryCount")

                val shouldProceed = serviceRepository.requestRetry(retryEvent, RETRY_DELAY_MS)
                Log.d("MeshDataHandler", "[ackNak] Retry response for req=$requestId: shouldProceed=$shouldProceed")

                if (shouldProceed) {
                    val newId = commandSender.generatePacketId()
                    val updatedData =
                        p.data.copy(
                            id = newId,
                            status = MessageStatus.QUEUED,
                            retryCount = newRetryCount,
                            relayNode = null,
                        )
                    val updatedPacket =
                        p.copy(
                            packetId = newId,
                            data = updatedData,
                            routingError = org.meshtastic.proto.Routing.Error.NONE.value,
                        )
                    packetRepository.get().update(updatedPacket)

                    Logger.w { "[ackNak] retrying req=$requestId newId=$newId retry=$newRetryCount" }
                    commandSender.sendData(updatedData)
                } else {
                    // User cancelled retry - mark as ERROR
                    Logger.w { "[ackNak] retry cancelled by user for req=$requestId" }
                    p.data.status = MessageStatus.ERROR
                    packetRepository.get().update(p)
                }
                return@handledLaunch
            }

            if (shouldRetryReaction) {
                val newRetryCount = reaction.retryCount + 1

                // Emit retry event to UI and wait for user response
                val retryEvent =
                    RetryEvent.ReactionRetry(
                        packetId = requestId,
                        emoji = reaction.emoji,
                        attemptNumber = newRetryCount,
                        maxAttempts = MAX_RETRY_ATTEMPTS + 1, // +1 for initial attempt
                    )

                Logger.w { "[ackNak] requesting retry for reaction req=$requestId retry=$newRetryCount" }

                val shouldProceed = serviceRepository.requestRetry(retryEvent, RETRY_DELAY_MS)

                if (shouldProceed) {
                    val newId = commandSender.generatePacketId()

                    val reactionPacket =
                        DataPacket(
                            to = reaction.to,
                            channel = reaction.channel,
                            bytes = reaction.emoji.toByteArray(Charsets.UTF_8),
                            dataType = PortNum.TEXT_MESSAGE_APP.value,
                            replyId = reaction.replyId,
                            wantAck = true,
                            emoji = reaction.emoji.codePointAt(0),
                            id = newId,
                            retryCount = newRetryCount,
                        )

                    val updatedReaction =
                        reaction.copy(
                            packetId = newId,
                            status = MessageStatus.QUEUED,
                            retryCount = newRetryCount,
                            relayNode = null,
                            routingError = org.meshtastic.proto.Routing.Error.NONE.value,
                        )
                    packetRepository.get().updateReaction(updatedReaction)

                    Logger.w { "[ackNak] retrying reaction req=$requestId newId=$newId retry=$newRetryCount" }
                    commandSender.sendData(reactionPacket)
                } else {
                    // User cancelled retry - mark as ERROR
                    Logger.w { "[ackNak] retry cancelled by user for reaction req=$requestId" }
                    val errorReaction = reaction.copy(status = MessageStatus.ERROR, routingError = routingError)
                    packetRepository.get().updateReaction(errorReaction)
                }
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

    private fun handleReceivedStoreAndForward(dataPacket: DataPacket, s: StoreAndForward, myNodeNum: Int) {
        Logger.d { "StoreAndForward: ${s.rr} from ${dataPacket.from}" }
        val transport = currentTransport()
        val h = s.history
        val lastRequest = h?.last_request ?: 0
        val baseContext = "transport=$transport from=${dataPacket.from}"
        historyLog { "rxStoreForward $baseContext rr=${s.rr} lastRequest=$lastRequest" }

        if (s.stats != null) {
            val text = s.stats.toString()
            val u = dataPacket.copy(bytes = text.encodeToByteArray(), dataType = PortNum.TEXT_MESSAGE_APP.value)
            rememberDataPacket(u, myNodeNum)
        } else if (h != null) {
            @Suppress("MaxLineLength")
            historyLog(Log.DEBUG) {
                "routerHistory $baseContext messages=${h.history_messages} window=${h.window} lastReq=${h.last_request}"
            }
            val text =
                "Total messages: ${h.history_messages}\n" +
                    "History window: ${h.window.toLong().milliseconds.inWholeMinutes} min\n" +
                    "Last request: ${h.last_request}"
            val u = dataPacket.copy(bytes = text.encodeToByteArray(), dataType = PortNum.TEXT_MESSAGE_APP.value)
            rememberDataPacket(u, myNodeNum)
            historyManager.updateStoreForwardLastRequest("router_history", h.last_request, transport)
        } else if (s.heartbeat != null) {
            val hb = s.heartbeat
            if (hb != null) {
                historyLog { "rxHeartbeat $baseContext period=${hb.period} secondary=${hb.secondary}" }
            }
        } else if (s.text != null) {
            if (s.rr == StoreAndForward.RequestResponse.ROUTER_TEXT_BROADCAST) {
                dataPacket.to = DataPacket.ID_BROADCAST
            }
            @Suppress("MaxLineLength")
            historyLog(Log.DEBUG) {
                "rxText $baseContext id=${dataPacket.id} ts=${dataPacket.time} to=${dataPacket.to} decision=remember"
            }
            val bytes = s.text?.toByteArray() ?: ByteArray(0)
            val u = dataPacket.copy(bytes = bytes, dataType = PortNum.TEXT_MESSAGE_APP.value)
            rememberDataPacket(u, myNodeNum)
        }
    }

    fun rememberDataPacket(dataPacket: DataPacket, myNodeNum: Int, updateNotification: Boolean = true) {
        if (dataPacket.dataType !in rememberDataType) return
        val fromLocal =
            dataPacket.from == DataPacket.ID_LOCAL || dataPacket.from == DataPacket.nodeNumToDefaultId(myNodeNum)
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
            )
        scope.handledLaunch {
            packetRepository.get().apply {
                // Check for duplicates before inserting
                val existingPackets = findPacketsWithId(dataPacket.id)
                if (existingPackets.isNotEmpty()) {
                    Logger.d {
                        "Skipping duplicate packet: packetId=${dataPacket.id} from=${dataPacket.from} " +
                            "to=${dataPacket.to} contactKey=$contactKey" +
                            " (already have ${existingPackets.size} packet(s))"
                    }
                    return@handledLaunch
                }

                insert(packetToSave)
                val conversationMuted = getContactSettings(contactKey).isMuted
                val nodeMuted = nodeManager.nodeDBbyID[dataPacket.from]?.isMuted == true
                val isSilent = conversationMuted || nodeMuted
                if (packetToSave.port_num == PortNum.ALERT_APP.value && !isSilent) {
                    serviceNotifications.showAlertNotification(
                        contactKey,
                        getSenderName(dataPacket),
                        dataPacket.alert ?: getString(Res.string.critical_alert),
                    )
                } else if (updateNotification) {
                    scope.handledLaunch { updateNotification(contactKey, dataPacket, isSilent) }
                }
            }
        }
    }

    private fun getSenderName(packet: DataPacket): String {
        if (packet.from == DataPacket.ID_LOCAL) {
            val myId = nodeManager.getMyId()
            return nodeManager.nodeDBbyID[myId]?.user?.long_name ?: getString(Res.string.unknown_username)
        }
        return nodeManager.nodeDBbyID[packet.from]?.user?.long_name ?: getString(Res.string.unknown_username)
    }

    private suspend fun updateNotification(contactKey: String, dataPacket: DataPacket, isSilent: Boolean) {
        val portNum = PortNum.fromValue(dataPacket.dataType)
        when (portNum) {
            PortNum.TEXT_MESSAGE_APP -> {
                val message = dataPacket.text ?: ""
                val channelName =
                    if (dataPacket.to == DataPacket.ID_BROADCAST) {
                        radioConfigRepository.channelSetFlow.first().settings.getOrNull(dataPacket.channel)?.name
                    } else {
                        null
                    }
                serviceNotifications.updateMessageNotification(
                    contactKey,
                    getSenderName(dataPacket),
                    message,
                    dataPacket.to == DataPacket.ID_BROADCAST,
                    channelName,
                    isSilent,
                )
            }

            PortNum.WAYPOINT_APP -> {
                val waypoint = dataPacket.waypoint ?: return
                val message = getString(Res.string.waypoint_received, waypoint.name)
                serviceNotifications.updateWaypointNotification(
                    contactKey,
                    getSenderName(dataPacket),
                    message,
                    waypoint.id,
                    isSilent,
                )
            }

            else -> return
        }
    }

    private fun rememberReaction(packet: MeshPacket) = scope.handledLaunch {
        val decoded = packet.decoded ?: return@handledLaunch
        val emoji = decoded.payload.toByteArray().decodeToString()
        val fromId = dataMapper.toNodeID(packet.from)
        val toId = dataMapper.toNodeID(packet.to)

        val reaction =
            ReactionEntity(
                myNodeNum = nodeManager.myNodeNum ?: 0,
                replyId = decoded.reply_id,
                userId = fromId,
                emoji = emoji,
                timestamp = System.currentTimeMillis(),
                snr = packet.rx_snr,
                rssi = packet.rx_rssi,
                hopsAway =
                if (packet.hop_start == 0 || packet.hop_limit > packet.hop_start) {
                    HOPS_AWAY_UNAVAILABLE
                } else {
                    packet.hop_start - packet.hop_limit
                },
                packetId = packet.id,
                status = MessageStatus.RECEIVED,
                to = toId,
                channel = packet.channel,
            )

        // Check for duplicates before inserting
        val existingReactions = packetRepository.get().findReactionsWithId(packet.id)
        if (existingReactions.isNotEmpty()) {
            Logger.d {
                "Skipping duplicate reaction: packetId=${packet.id} replyId=${decoded.reply_id} " +
                    "from=$fromId emoji=$emoji (already have ${existingReactions.size} reaction(s))"
            }
            return@handledLaunch
        }

        packetRepository.get().insertReaction(reaction)
        updateReactionNotification(decoded.reply_id, fromId, packet, emoji)
    }

    private suspend fun updateReactionNotification(replyId: Int, fromId: String, packet: MeshPacket, emoji: String) {
        // Find the original packet to get the contactKey
        packetRepository.get().getPacketByPacketId(replyId)?.let { original ->
            val contactKey = original.packet.contact_key
            val conversationMuted = packetRepository.get().getContactSettings(contactKey).isMuted
            val nodeMuted = nodeManager.nodeDBbyID[fromId]?.isMuted == true
            val isSilent = conversationMuted || nodeMuted
            val channelName =
                if (original.packet.data.to == DataPacket.ID_BROADCAST) {
                    radioConfigRepository.channelSetFlow.first().settings.getOrNull(original.packet.data.channel)?.name
                } else {
                    null
                }
            serviceNotifications.updateReactionNotification(
                contactKey,
                dataMapper.toDataPacket(packet)?.let { getSenderName(it) } ?: getString(Res.string.unknown_username),
                emoji,
                original.packet.data.to == DataPacket.ID_BROADCAST,
                channelName,
                isSilent,
            )
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
        private const val MAX_RETRY_ATTEMPTS = 2
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
