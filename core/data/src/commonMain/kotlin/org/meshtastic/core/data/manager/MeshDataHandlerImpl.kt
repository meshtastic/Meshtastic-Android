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
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.ByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.model.util.MeshDataMapper
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.model.util.toOneLiner
import org.meshtastic.core.repository.AdminPacketHandler
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.MessageFilter
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.StoreForwardPacketHandler
import org.meshtastic.core.repository.TelemetryPacketHandler
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.critical_alert
import org.meshtastic.core.resources.error_duty_cycle
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.unknown_username
import org.meshtastic.core.resources.waypoint_received
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.Routing
import org.meshtastic.proto.StatusMessage
import org.meshtastic.proto.User
import org.meshtastic.proto.Waypoint

/**
 * Implementation of [MeshDataHandler] that decodes and routes incoming mesh data packets.
 *
 * This class handles the complexity of:
 * 1. Mapping raw [MeshPacket] objects to domain-friendly [DataPacket] objects.
 * 2. Routing packets to specialized handlers (e.g., Traceroute, NeighborInfo, Telemetry, Admin, SFPP).
 * 3. Managing message history and persistence.
 * 4. Triggering notifications for various packet types (Text, Waypoints).
 */
@Suppress("LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod")
@Single
class MeshDataHandlerImpl(
    private val nodeManager: NodeManager,
    private val packetHandler: PacketHandler,
    private val serviceRepository: ServiceRepository,
    private val packetRepository: Lazy<PacketRepository>,
    private val serviceBroadcasts: ServiceBroadcasts,
    private val notificationManager: NotificationManager,
    private val serviceNotifications: MeshServiceNotifications,
    private val analytics: PlatformAnalytics,
    private val dataMapper: MeshDataMapper,
    private val tracerouteHandler: TracerouteHandler,
    private val neighborInfoHandler: NeighborInfoHandler,
    private val radioConfigRepository: RadioConfigRepository,
    private val messageFilter: MessageFilter,
    private val storeForwardHandler: StoreForwardPacketHandler,
    private val telemetryHandler: TelemetryPacketHandler,
    private val adminPacketHandler: AdminPacketHandler,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : MeshDataHandler {

    private val rememberDataType =
        setOf(
            PortNum.TEXT_MESSAGE_APP.value,
            PortNum.ALERT_APP.value,
            PortNum.WAYPOINT_APP.value,
            PortNum.NODE_STATUS_APP.value,
        )

    override fun handleReceivedData(packet: MeshPacket, myNodeNum: Int, logUuid: String?, logInsertJob: Job?) {
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
        val decoded = packet.decoded ?: return shouldBroadcast
        when (decoded.portnum) {
            PortNum.TEXT_MESSAGE_APP -> handleTextMessage(packet, dataPacket, myNodeNum)

            PortNum.NODE_STATUS_APP -> handleNodeStatus(packet, dataPacket, myNodeNum)

            PortNum.ALERT_APP -> rememberDataPacket(dataPacket, myNodeNum)

            PortNum.WAYPOINT_APP -> handleWaypoint(packet, dataPacket, myNodeNum)

            PortNum.POSITION_APP -> handlePosition(packet, dataPacket, myNodeNum)

            PortNum.NODEINFO_APP -> if (!fromUs) handleNodeInfo(packet)

            PortNum.TELEMETRY_APP -> telemetryHandler.handleTelemetry(packet, dataPacket, myNodeNum)

            else ->
                shouldBroadcast =
                    handleSpecializedDataPacket(packet, dataPacket, myNodeNum, fromUs, logUuid, logInsertJob)
        }
        return shouldBroadcast
    }

    private fun handleSpecializedDataPacket(
        packet: MeshPacket,
        dataPacket: DataPacket,
        myNodeNum: Int,
        fromUs: Boolean,
        logUuid: String?,
        logInsertJob: Job?,
    ): Boolean {
        var shouldBroadcast = !fromUs
        val decoded = packet.decoded ?: return shouldBroadcast
        when (decoded.portnum) {
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
            }

            PortNum.STORE_FORWARD_APP -> {
                storeForwardHandler.handleStoreAndForward(packet, dataPacket, myNodeNum)
            }

            PortNum.STORE_FORWARD_PLUSPLUS_APP -> {
                storeForwardHandler.handleStoreForwardPlusPlus(packet)
            }

            PortNum.ADMIN_APP -> {
                adminPacketHandler.handleAdminMessage(packet, myNodeNum)
            }

            PortNum.NEIGHBORINFO_APP -> {
                neighborInfoHandler.handleNeighborInfo(packet)
                shouldBroadcast = true
            }

            PortNum.ATAK_PLUGIN,
            PortNum.ATAK_FORWARDER,
            PortNum.PRIVATE_APP,
            -> {
                shouldBroadcast = true
            }

            PortNum.RANGE_TEST_APP,
            PortNum.DETECTION_SENSOR_APP,
            -> {
                handleRangeTest(dataPacket, myNodeNum)
                shouldBroadcast = true
            }

            else -> {
                // By default, if we don't know what it is, we should probably broadcast it
                // so that external apps can handle it.
                shouldBroadcast = true
            }
        }
        return shouldBroadcast
    }

    private fun handleRangeTest(dataPacket: DataPacket, myNodeNum: Int) {
        val u = dataPacket.copy(dataType = PortNum.TEXT_MESSAGE_APP.value)
        rememberDataPacket(u, myNodeNum)
    }

    private fun handlePaxCounter(packet: MeshPacket) {
        val payload = packet.decoded?.payload ?: return
        val p = Paxcount.ADAPTER.decodeOrNull(payload, Logger) ?: return
        nodeManager.handleReceivedPaxcounter(packet.from, p)
    }

    private fun handlePosition(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: return
        val p = Position.ADAPTER.decodeOrNull(payload, Logger) ?: return
        Logger.d { "Position from ${packet.from}: ${Position.ADAPTER.toOneLiner(p)}" }
        nodeManager.handleReceivedPosition(packet.from, myNodeNum, p, dataPacket.time)
    }

    private fun handleWaypoint(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: return
        val u = Waypoint.ADAPTER.decode(payload)
        if (u.locked_to != 0 && u.locked_to != packet.from) return
        val currentSecond = nowSeconds.toInt()
        rememberDataPacket(dataPacket, myNodeNum, updateNotification = u.expire > currentSecond)
    }

    private fun handleTextMessage(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val decoded = packet.decoded ?: return
        if (decoded.reply_id != 0 && decoded.emoji != 0) {
            rememberReaction(packet)
        } else {
            rememberDataPacket(dataPacket, myNodeNum)
        }
    }

    private fun handleNodeInfo(packet: MeshPacket) {
        val payload = packet.decoded?.payload ?: return
        val u =
            User.ADAPTER.decode(payload)
                .let { if (it.is_licensed == true) it.copy(public_key = ByteString.EMPTY) else it }
                .let {
                    if (packet.via_mqtt == true && !it.long_name.endsWith(" (MQTT)")) {
                        it.copy(long_name = "${it.long_name} (MQTT)")
                    } else {
                        it
                    }
                }
        nodeManager.handleReceivedUser(packet.from, u, packet.channel)
    }

    private fun handleNodeStatus(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: return
        val s = StatusMessage.ADAPTER.decodeOrNull(payload, Logger) ?: return
        nodeManager.handleReceivedNodeStatus(packet.from, s)
        rememberDataPacket(dataPacket, myNodeNum)
    }

    private fun handleRouting(packet: MeshPacket, dataPacket: DataPacket) {
        val payload = packet.decoded?.payload ?: return
        val r = Routing.ADAPTER.decodeOrNull(payload, Logger) ?: return
        if (r.error_reason == Routing.Error.DUTY_CYCLE_LIMIT) {
            scope.launch {
                serviceRepository.setErrorMessage(getStringSuspend(Res.string.error_duty_cycle), Severity.Warn)
            }
        }
        handleAckNak(
            packet.decoded?.request_id ?: 0,
            nodeManager.toNodeID(packet.from),
            r.error_reason?.value ?: 0,
            dataPacket.relayNode,
        )
        packet.decoded?.request_id?.let { packetHandler.removeResponse(it, complete = true) }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun handleAckNak(requestId: Int, fromId: String, routingError: Int, relayNode: Int?) {
        scope.handledLaunch {
            val isAck = routingError == Routing.Error.NONE.value
            val p = packetRepository.value.getPacketByPacketId(requestId)
            val reaction = packetRepository.value.getReactionByPacketId(requestId)

            @Suppress("MaxLineLength")
            Logger.d {
                val statusInfo = "status=${p?.status ?: reaction?.status}"
                "[ackNak] req=$requestId routeErr=$routingError isAck=$isAck " +
                    "packetId=${p?.id ?: reaction?.packetId} dataId=${p?.id} $statusInfo"
            }

            val m =
                when {
                    isAck && (fromId == p?.to || fromId == reaction?.to) -> MessageStatus.RECEIVED
                    isAck -> MessageStatus.DELIVERED
                    else -> MessageStatus.ERROR
                }
            if (p != null && p.status != MessageStatus.RECEIVED) {
                val updatedPacket =
                    p.copy(status = m, relays = if (isAck) p.relays + 1 else p.relays, relayNode = relayNode)
                packetRepository.value.update(updatedPacket, routingError = routingError)
            }

            reaction?.let { r ->
                if (r.status != MessageStatus.RECEIVED) {
                    var updated = r.copy(status = m, routingError = routingError, relayNode = relayNode)
                    if (isAck) {
                        updated = updated.copy(relays = updated.relays + 1)
                    }
                    packetRepository.value.updateReaction(updated)
                }
            }

            serviceBroadcasts.broadcastMessageStatus(requestId, m)
        }
    }

    override fun rememberDataPacket(dataPacket: DataPacket, myNodeNum: Int, updateNotification: Boolean) {
        if (dataPacket.dataType !in rememberDataType) return
        val fromLocal =
            dataPacket.from == DataPacket.ID_LOCAL || dataPacket.from == DataPacket.nodeNumToDefaultId(myNodeNum)
        val toBroadcast = dataPacket.to == DataPacket.ID_BROADCAST
        val contactId = if (fromLocal || toBroadcast) dataPacket.to else dataPacket.from

        // contactKey: unique contact key filter (channel)+(nodeId)
        val contactKey = "${dataPacket.channel}$contactId"

        scope.handledLaunch {
            packetRepository.value.apply {
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

                // Check if message should be filtered
                val isFiltered = shouldFilterMessage(dataPacket, contactKey)

                insert(
                    dataPacket,
                    myNodeNum,
                    contactKey,
                    nowMillis,
                    read = fromLocal || isFiltered,
                    filtered = isFiltered,
                )
                if (!isFiltered) {
                    handlePacketNotification(dataPacket, contactKey, updateNotification)
                }
            }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun PacketRepository.shouldFilterMessage(dataPacket: DataPacket, contactKey: String): Boolean {
        val isIgnored = nodeManager.nodeDBbyID[dataPacket.from]?.isIgnored == true
        if (isIgnored) return true

        if (dataPacket.dataType != PortNum.TEXT_MESSAGE_APP.value) return false
        val isFilteringDisabled = getContactSettings(contactKey).filteringDisabled
        return messageFilter.shouldFilter(dataPacket.text.orEmpty(), isFilteringDisabled)
    }

    private suspend fun handlePacketNotification(
        dataPacket: DataPacket,
        contactKey: String,
        updateNotification: Boolean,
    ) {
        val conversationMuted = packetRepository.value.getContactSettings(contactKey).isMuted
        val nodeMuted = nodeManager.nodeDBbyID[dataPacket.from]?.isMuted == true
        val isSilent = conversationMuted || nodeMuted
        if (dataPacket.dataType == PortNum.ALERT_APP.value && !isSilent) {
            scope.launch {
                notificationManager.dispatch(
                    Notification(
                        title = getSenderName(dataPacket),
                        message = dataPacket.alert ?: getStringSuspend(Res.string.critical_alert),
                        category = Notification.Category.Alert,
                        contactKey = contactKey,
                    ),
                )
            }
        } else if (updateNotification && !isSilent) {
            scope.handledLaunch { updateNotification(contactKey, dataPacket, isSilent) }
        }
    }

    private suspend fun getSenderName(packet: DataPacket): String {
        if (packet.from == DataPacket.ID_LOCAL) {
            val myId = nodeManager.getMyId()
            return nodeManager.nodeDBbyID[myId]?.user?.long_name ?: getStringSuspend(Res.string.unknown_username)
        }
        return nodeManager.nodeDBbyID[packet.from]?.user?.long_name ?: getStringSuspend(Res.string.unknown_username)
    }

    private suspend fun updateNotification(contactKey: String, dataPacket: DataPacket, isSilent: Boolean) {
        when (dataPacket.dataType) {
            PortNum.TEXT_MESSAGE_APP.value -> {
                val message = dataPacket.text!!
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

            PortNum.WAYPOINT_APP.value -> {
                val message = getStringSuspend(Res.string.waypoint_received, dataPacket.waypoint!!.name)
                notificationManager.dispatch(
                    Notification(
                        title = getSenderName(dataPacket),
                        message = message,
                        category = Notification.Category.Message,
                        contactKey = contactKey,
                        isSilent = isSilent,
                    ),
                )
            }

            else -> return
        }
    }

    @Suppress("LongMethod", "KotlinConstantConditions")
    private fun rememberReaction(packet: MeshPacket) = scope.handledLaunch {
        val decoded = packet.decoded ?: return@handledLaunch
        val emoji = decoded.payload.toByteArray().decodeToString()
        val fromId = nodeManager.toNodeID(packet.from)

        val fromNode = nodeManager.nodeDBbyNodeNum[packet.from] ?: Node(num = packet.from)
        val toNode = nodeManager.nodeDBbyNodeNum[packet.to] ?: Node(num = packet.to)

        val reaction =
            Reaction(
                replyId = decoded.reply_id,
                user = fromNode.user,
                emoji = emoji,
                timestamp = nowMillis,
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
                to = toNode.user.id,
                channel = packet.channel,
            )

        // Check for duplicates before inserting
        val existingReactions = packetRepository.value.findReactionsWithId(packet.id)
        if (existingReactions.isNotEmpty()) {
            Logger.d {
                "Skipping duplicate reaction: packetId=${packet.id} replyId=${decoded.reply_id} " +
                    "from=$fromId emoji=$emoji (already have ${existingReactions.size} reaction(s))"
            }
            return@handledLaunch
        }

        packetRepository.value.insertReaction(reaction, nodeManager.myNodeNum.value ?: 0)

        // Find the original packet to get the contactKey
        packetRepository.value.getPacketByPacketId(decoded.reply_id)?.let { originalPacket ->
            // Skip notification if the original message was filtered
            val targetId =
                if (originalPacket.from == DataPacket.ID_LOCAL) originalPacket.to else originalPacket.from
            val contactKey = "${originalPacket.channel}$targetId"
            val conversationMuted = packetRepository.value.getContactSettings(contactKey).isMuted
            val nodeMuted = nodeManager.nodeDBbyID[fromId]?.isMuted == true
            val isSilent = conversationMuted || nodeMuted

            if (!isSilent) {
                val channelName =
                    if (originalPacket.to == DataPacket.ID_BROADCAST) {
                        radioConfigRepository.channelSetFlow
                            .first()
                            .settings
                            .getOrNull(originalPacket.channel)
                            ?.name
                    } else {
                        null
                    }
                serviceNotifications.updateReactionNotification(
                    contactKey,
                    getSenderName(dataMapper.toDataPacket(packet)!!),
                    emoji,
                    originalPacket.to == DataPacket.ID_BROADCAST,
                    channelName,
                    isSilent,
                )
            }
        }
    }

    companion object {
        private const val HOPS_AWAY_UNAVAILABLE = -1
    }
}
