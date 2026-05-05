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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState as AppConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConnectionState as SdkConnectionState
import org.meshtastic.sdk.MeshEvent
import org.meshtastic.sdk.NodeChange
import org.meshtastic.sdk.NodeId

/**
 * Bridges SDK reactive flows into the legacy repository layer and routes [ServiceAction]s
 * directly through the SDK, bypassing the old CommandSender/MeshActionHandler pipeline.
 *
 * The SDK owns the transport and all state; this bridge maps SDK emissions into [ServiceRepository]
 * and [NodeManager] so that existing feature-module UI code (which observes those repositories)
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
    private val nodeManager: NodeManager,
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
                        nodeManager.clear()
                        change.nodes.forEach { (_, nodeInfo) ->
                            nodeManager.installNodeInfo(nodeInfo, withBroadcast = false)
                        }
                        nodeManager.setNodeDbReady(true)
                    }
                    is NodeChange.Added -> nodeManager.installNodeInfo(change.node, withBroadcast = true)
                    is NodeChange.Updated -> nodeManager.installNodeInfo(change.node, withBroadcast = true)
                    is NodeChange.Removed -> nodeManager.removeByNodenum(change.nodeId.raw)
                }
            }
            .launchIn(scope)

        // ── Own node identity ───────────────────────────────────────────────
        accessor.client
            .flatMapLatest { client -> client?.ownNode ?: flowOf(null) }
            .onEach { ownNode -> if (ownNode != null) nodeManager.setMyNodeNum(ownNode.num) }
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
                    is MeshEvent.SecurityWarning -> Logger.w { "[SdkBridge] Security warning: $event" }
                    is MeshEvent.PacketsDropped -> Logger.w { "[SdkBridge] Packets dropped: ${event.count} from ${event.flow}" }
                    else -> Logger.d { "[SdkBridge] Event: $event" }
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

        when (action) {
            is ServiceAction.Favorite -> {
                val node = action.node
                client.admin.setFavorite(NodeId(node.num), !node.isFavorite)
                nodeManager.updateNode(node.num) { it.copy(isFavorite = !node.isFavorite) }
            }

            is ServiceAction.Ignore -> {
                val node = action.node
                val newIgnored = !node.isIgnored
                client.admin.setIgnored(NodeId(node.num), newIgnored)
                nodeManager.updateNode(node.num) { it.copy(isIgnored = newIgnored) }
                packetRepository.value.updateFilteredBySender(node.user.id, newIgnored)
            }

            is ServiceAction.Mute -> {
                val node = action.node
                client.admin.toggleMuted(NodeId(node.num))
                nodeManager.updateNode(node.num) { it.copy(isMuted = !node.isMuted) }
            }

            is ServiceAction.Reaction -> {
                val channel = action.contactKey[0].digitToInt()
                val destId = action.contactKey.substring(1)
                val destNum = DataPacket.idToDefaultNodeNum(destId.removePrefix("!"))
                    ?: DataPacket.NODENUM_BROADCAST
                client.send(
                    MeshPacket(
                        to = destNum,
                        channel = channel,
                        want_ack = true,
                        decoded = Data(
                            portnum = PortNum.TEXT_MESSAGE_APP,
                            payload = action.emoji.encodeToByteArray().toByteString(),
                            emoji = EMOJI_INDICATOR,
                            reply_id = action.replyId,
                        ),
                    ),
                )
            }

            is ServiceAction.ImportContact -> {
                val verified = action.contact.copy(manually_verified = true)
                client.admin.addContact(verified)
                nodeManager.handleReceivedUser(
                    verified.node_num,
                    verified.user ?: User(),
                    manuallyVerified = true,
                )
            }

            is ServiceAction.SendContact -> {
                val result = runCatching { client.admin.addContact(action.contact) }
                action.result.complete(result.getOrNull() is AdminResult.Success)
            }

            is ServiceAction.GetDeviceMetadata -> {
                val payload = AdminMessage.ADAPTER.encode(
                    AdminMessage(get_device_metadata_request = true),
                ).toByteString()
                client.send(
                    MeshPacket(
                        to = action.destNum,
                        want_ack = true,
                        decoded = Data(
                            portnum = PortNum.ADMIN_APP,
                            payload = payload,
                            want_response = true,
                        ),
                    ),
                )
            }
        }
    }

    companion object {
        private const val EMOJI_INDICATOR = 1

        fun mapConnectionState(sdkState: SdkConnectionState): AppConnectionState = when (sdkState) {
            is SdkConnectionState.Disconnected -> AppConnectionState.Disconnected
            is SdkConnectionState.Connecting -> AppConnectionState.Connecting
            is SdkConnectionState.Configuring -> AppConnectionState.Connecting
            is SdkConnectionState.Connected -> AppConnectionState.Connected
            is SdkConnectionState.Reconnecting -> AppConnectionState.DeviceSleep
        }
    }
}
