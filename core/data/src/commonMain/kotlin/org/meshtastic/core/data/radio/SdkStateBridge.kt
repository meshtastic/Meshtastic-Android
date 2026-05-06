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
package org.meshtastic.core.data.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState as AppConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ChannelIndex
import org.meshtastic.sdk.ConnectionState as SdkConnectionState
import org.meshtastic.sdk.MeshEvent
import org.meshtastic.sdk.NodeChange
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.StoreForwardEvent

/**
 * Bridges SDK reactive flows into the repository layer and routes [ServiceAction]s
 * directly through the SDK, bypassing the old CommandSender/MeshActionHandler pipeline.
 *
 * The SDK owns the transport and all state; this bridge maps SDK emissions into [ServiceRepository]
 * and [NodeRepository] so that existing feature-module UI code (which observes those repositories)
 * continues to work without modification.
 *
 * **Lifecycle:** Created as a Koin `@Single`. Automatically subscribes to [RadioClientAccessor.client]
 * and starts/stops collection as clients come and go.
 */
@Single
@Suppress("TooManyFunctions")
class SdkStateBridge(
    private val accessor: RadioClientAccessor,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val packetRepository: Lazy<PacketRepository>,
    private val locationManager: MeshLocationManager,
    private val uiPrefs: UiPrefs,
    private val radioController: RadioController,
    private val dispatchers: CoroutineDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var locationJob: Job? = null

    init {
        startBridge()
    }

    private fun startBridge() {

        // ── Connection state ────────────────────────────────────────────────
        accessor.client
            .flatMapLatest { client -> client?.connection ?: flowOf(SdkConnectionState.Disconnected) }
            .onEach { sdkState -> serviceRepository.setConnectionState(mapConnectionState(sdkState)) }
            .launchIn(scope)

        // ── Node updates (position, telemetry, user all included in NodeInfo) ─
        accessor.client
            .flatMapLatest { client -> client?.nodes ?: flowOf() }
            .onEach { change ->
                when (change) {
                    is NodeChange.Snapshot -> {
                        nodeRepository.clear()
                        change.nodes.forEach { (_, nodeInfo) ->
                            nodeRepository.installNodeInfo(nodeInfo, withBroadcast = false)
                        }
                        nodeRepository.setNodeDbReady(true)
                    }
                    is NodeChange.Added -> nodeRepository.installNodeInfo(change.node, withBroadcast = true)
                    is NodeChange.Updated -> nodeRepository.installNodeInfo(change.node, withBroadcast = true)
                    is NodeChange.Removed -> nodeRepository.removeByNodenum(change.nodeId.raw)
                    is NodeChange.WentOffline -> {
                        val nodeNum = change.nodeId.raw
                        Logger.d {
                            "[SdkBridge] Node ${DataPacket.nodeNumToDefaultId(nodeNum)} went offline (last heard: ${change.lastHeard})"
                        }
                        if (nodeRepository.nodeDBbyNodeNum.containsKey(nodeNum)) {
                            nodeRepository.updateNode(nodeNum) { node ->
                                node.copy(lastHeard = minOf(node.lastHeard, change.lastHeard, onlineTimeThreshold()))
                            }
                        }
                    }
                    is NodeChange.CameOnline -> {
                        val nodeNum = change.nodeId.raw
                        Logger.d { "[SdkBridge] Node ${DataPacket.nodeNumToDefaultId(nodeNum)} came online" }
                        if (nodeRepository.nodeDBbyNodeNum.containsKey(nodeNum)) {
                            nodeRepository.updateNode(nodeNum) { node ->
                                node.copy(lastHeard = maxOf(node.lastHeard, nowSeconds.toInt()))
                            }
                        }
                    }
                }
            }
            .launchIn(scope)

        // ── Own node identity ───────────────────────────────────────────────
        accessor.client
            .flatMapLatest { client -> client?.ownNode ?: flowOf(null) }
            .onEach { ownNode -> if (ownNode != null) nodeRepository.setMyNodeNum(ownNode.num) }
            .launchIn(scope)

        // ── Raw packet forward (for RadioConfigViewModel + TAK) ─────────────
        accessor.client
            .flatMapLatest { client -> client?.packets ?: flowOf() }
            .onEach { packet -> serviceRepository.emitMeshPacket(packet) }
            .launchIn(scope)

        // ── Events (notifications, security, backpressure) ──────────────────
        accessor.client
            .flatMapLatest { client -> client?.events ?: flowOf() }
            .onEach { event ->
                when (event) {
                    is MeshEvent.DeviceRebooted -> {
                        Logger.i { "[SdkBridge] Device rebooted" }
                        serviceRepository.setClientNotification(
                            ClientNotification(message = "Device rebooted"),
                        )
                    }
                    is MeshEvent.CongestionWarning -> {
                        Logger.w {
                            "[SdkBridge] Congestion warning: level=${event.metrics.level}, airUtil=${event.metrics.airUtilTx}%, channelUtil=${event.metrics.channelUtil}%"
                        }
                        serviceRepository.setCongestionLevel(event.metrics.level)
                    }
                    is MeshEvent.SecurityWarning -> Logger.w { "[SdkBridge] Security warning: $event" }
                    is MeshEvent.PacketsDropped -> Logger.w { "[SdkBridge] Packets dropped: ${event.count} from ${event.flow}" }
                    else -> Logger.d { "[SdkBridge] Event: $event" }
                }
            }
            .launchIn(scope)

        // ── Store-and-Forward (server discovery + replay lifecycle) ───────────
        accessor.client
            .flatMapLatest { client ->
                client?.storeForward?.servers
                    ?.map { servers -> servers.map { it.raw } }
                    ?: flowOf(emptyList())
            }
            .onEach { servers -> serviceRepository.setStoreForwardServers(servers) }
            .launchIn(scope)

        accessor.client
            .flatMapLatest { client -> client?.storeForward?.events ?: emptyFlow() }
            .onEach { event ->
                when (event) {
                    is StoreForwardEvent.ServerDiscovered -> {
                        Logger.i {
                            "[SdkBridge] S&F server discovered: ${DataPacket.nodeNumToDefaultId(event.nodeId.raw)}"
                        }
                    }
                    is StoreForwardEvent.ServerLost -> {
                        Logger.i {
                            "[SdkBridge] S&F server lost: ${DataPacket.nodeNumToDefaultId(event.nodeId.raw)}"
                        }
                    }
                    is StoreForwardEvent.HistoryReplayStarted -> {
                        Logger.i {
                            "[SdkBridge] S&F history replay started from " +
                                "${DataPacket.nodeNumToDefaultId(event.server.raw)} count=${event.messageCount}"
                        }
                    }
                    is StoreForwardEvent.HistoryReplayComplete -> {
                        Logger.i {
                            "[SdkBridge] S&F history replay complete from " +
                                "${DataPacket.nodeNumToDefaultId(event.server.raw)} delivered=${event.delivered}"
                        }
                    }
                    is StoreForwardEvent.Heartbeat -> {
                        Logger.d {
                            "[SdkBridge] S&F heartbeat from ${DataPacket.nodeNumToDefaultId(event.server.raw)}"
                        }
                    }
                    is StoreForwardEvent.SfppLinkProvided -> {
                        event.messageHash?.let { hash ->
                            val status = if (event.confirmed) MessageStatus.SFPP_CONFIRMED else MessageStatus.SFPP_ROUTING
                            packetRepository.value.updateSFPPStatus(
                                packetId = event.packetId,
                                from = event.from,
                                to = event.to,
                                hash = hash,
                                status = status,
                                rxTime = 0L,
                                myNodeNum = nodeRepository.myNodeNum.value ?: 0,
                            )
                        }
                    }
                    is StoreForwardEvent.SfppCanonAnnounced -> {
                        packetRepository.value.updateSFPPStatusByHash(
                            hash = event.messageHash,
                            status = MessageStatus.SFPP_CONFIRMED,
                            rxTime = event.rxTime,
                        )
                    }
                    else -> Logger.d { "[SdkBridge] S&F event: $event" }
                }
            }
            .launchIn(scope)

        // ── ServiceAction routing (replaces MeshServiceOrchestrator dispatch) ─
        serviceRepository.serviceAction
            .onEach { action -> handleServiceAction(action) }
            .launchIn(scope)

        // ── Location publishing ─────────────────────────────────────────────
        accessor.client
            .flatMapLatest { client -> client?.ownNode ?: flowOf(null) }
            .onEach { ownNode ->
                locationJob?.cancel()
                locationJob = null
                if (ownNode != null) {
                    locationJob = uiPrefs.shouldProvideNodeLocation(ownNode.num)
                        .onEach { shouldProvide ->
                            if (shouldProvide) {
                                locationManager.start(scope) { pos ->
                                    scope.launch {
                                        val packet = DataPacket(
                                            bytes = okio.ByteString.of(
                                                *org.meshtastic.proto.Position.ADAPTER.encode(pos),
                                            ),
                                            dataType = PortNum.POSITION_APP.value,
                                        )
                                        radioController.sendMessage(packet)
                                    }
                                }
                            } else {
                                locationManager.stop()
                            }
                        }
                        .launchIn(scope)
                }
            }
            .launchIn(scope)

        Logger.i { "SdkStateBridge started — SDK owns transport + ServiceAction dispatch" }
    }

    // ── ServiceAction handling ───────────────────────────────────────────────

    private suspend fun handleServiceAction(action: ServiceAction) {
        val client = accessor.client.value
        if (client == null) {
            Logger.w { "[SdkBridge] ServiceAction ${action::class.simpleName} dropped — no client" }
            if (action is ServiceAction.SendContact) action.result.complete(false)
            return
        }

        try {
            dispatchAction(client, action)
        } catch (e: Exception) {
            Logger.e(e) { "[SdkBridge] ServiceAction ${action::class.simpleName} failed" }
            if (action is ServiceAction.SendContact) action.result.complete(false)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun dispatchAction(client: org.meshtastic.sdk.RadioClient, action: ServiceAction) {
        when (action) {
            is ServiceAction.Favorite -> {
                val node = action.node
                val result = runCatching { client.admin.setFavorite(NodeId(node.num), !node.isFavorite) }
                if (result.isSuccess) {
                    nodeRepository.updateNode(node.num) { it.copy(isFavorite = !node.isFavorite) }
                } else {
                    Logger.w(result.exceptionOrNull()) { "[SdkBridge] setFavorite failed for ${node.num}" }
                }
            }

            is ServiceAction.Ignore -> {
                val node = action.node
                val newIgnored = !node.isIgnored
                val result = runCatching { client.admin.setIgnored(NodeId(node.num), newIgnored) }
                if (result.isSuccess) {
                    nodeRepository.updateNode(node.num) { it.copy(isIgnored = newIgnored) }
                    packetRepository.value.updateFilteredBySender(node.user.id, newIgnored)
                } else {
                    Logger.w(result.exceptionOrNull()) { "[SdkBridge] setIgnored failed for ${node.num}" }
                }
            }

            is ServiceAction.Mute -> {
                val node = action.node
                val result = runCatching { client.admin.toggleMuted(NodeId(node.num)) }
                if (result.isSuccess) {
                    nodeRepository.updateNode(node.num) { it.copy(isMuted = !node.isMuted) }
                } else {
                    Logger.w(result.exceptionOrNull()) { "[SdkBridge] toggleMuted failed for ${node.num}" }
                }
            }

            is ServiceAction.Reaction -> {
                val channel = action.contactKey[0].digitToInt()
                val destId = action.contactKey.substring(1)
                val destNum = runCatching { DataPacket.parseNodeNum(destId) }.getOrDefault(DataPacket.BROADCAST)
                client.sendReaction(
                    emoji = action.emoji,
                    to = NodeId(destNum),
                    channel = ChannelIndex(channel),
                    replyId = action.replyId,
                )
            }

            is ServiceAction.ImportContact -> {
                val verified = action.contact.copy(manually_verified = true)
                val result = runCatching { client.admin.addContact(verified) }
                if (result.isSuccess) {
                    nodeRepository.handleReceivedUser(
                        verified.node_num,
                        verified.user ?: User(),
                        manuallyVerified = true,
                    )
                } else {
                    Logger.w(result.exceptionOrNull()) { "[SdkBridge] importContact failed" }
                }
            }

            is ServiceAction.SendContact -> {
                val result = runCatching { client.admin.addContact(action.contact) }
                action.result.complete(result.getOrNull() is AdminResult.Success)
            }

            is ServiceAction.GetDeviceMetadata -> {
                client.admin.forNode(NodeId(action.destNum)).getDeviceMetadata()
            }
        }
    }

    companion object {
        private fun mapConnectionState(sdkState: SdkConnectionState): AppConnectionState = when (sdkState) {
            is SdkConnectionState.Disconnected -> AppConnectionState.Disconnected
            is SdkConnectionState.Connecting -> AppConnectionState.Connecting(attempt = sdkState.attempt)
            is SdkConnectionState.Configuring -> AppConnectionState.Configuring(
                phase = sdkState.phase.name,
                progress = sdkState.progress,
            )
            is SdkConnectionState.Connected -> AppConnectionState.Connected
            is SdkConnectionState.Reconnecting -> AppConnectionState.Reconnecting(attempt = sdkState.attempt)
        }
    }
}
