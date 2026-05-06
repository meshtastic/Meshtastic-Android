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
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState as AppConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ChannelIndex
import org.meshtastic.sdk.ConnectionState as SdkConnectionState
import org.meshtastic.sdk.NodeId

/**
 * Bridges SDK reactive flows into the repository layer and routes [ServiceAction]s
 * directly through the SDK, bypassing the old CommandSender/MeshActionHandler pipeline.
 *
 * The SDK owns the transport and all state; this bridge maps SDK emissions into [ServiceRepository]
 * and [NodeRepository] so that existing feature-module UI code (which observes those repositories)
 * continues to work without modification.
 */
@Single
class SdkStateBridge(
    private val accessor: RadioClientAccessor,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val packetRepository: Lazy<PacketRepository>,
    private val locationManager: MeshLocationManager,
    topologyService: MeshTopologyService,
    private val uiPrefs: UiPrefs,
    private val dispatchers: CoroutineDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private var locationJob: Job? = null

    private val nodeBridge = SdkNodeBridge(nodeRepository = nodeRepository, topologyService = topologyService)
    private val packetBridge = SdkPacketBridge(
        serviceRepository = serviceRepository,
        packetRepository = packetRepository,
        nodeRepository = nodeRepository,
    )
    private val topologyBridge = SdkTopologyBridge(topologyService = topologyService)
    private val eventBridge = SdkEventBridge(serviceRepository = serviceRepository)

    init {
        bind()
    }

    private fun bind() {
        bindConnectionState()
        nodeBridge.observe(accessor, scope)
        packetBridge.observe(accessor, scope)
        topologyBridge.observe(accessor, scope)
        eventBridge.observe(accessor, scope)
        bindServiceActions()
        bindLocationPublishing()
        Logger.i { "SdkStateBridge started — SDK owns transport + ServiceAction dispatch" }
    }

    private fun bindConnectionState() {
        accessor.client
            .flatMapLatest { client -> client?.connection ?: flowOf(SdkConnectionState.Disconnected) }
            .onEach { sdkState -> serviceRepository.setConnectionState(mapConnectionState(sdkState)) }
            .launchIn(scope)
    }

    private fun bindServiceActions() {
        serviceRepository.serviceAction
            .onEach { action -> handleServiceAction(action) }
            .launchIn(scope)
    }

    private fun bindLocationPublishing() {
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
                                        val client = accessor.client.value ?: return@launch
                                        val posBytes = org.meshtastic.proto.Position.ADAPTER.encode(pos)
                                        client.send(
                                            portnum = PortNum.POSITION_APP,
                                            payload = posBytes,
                                            wantAck = false,
                                        )
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
    }

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
                    nodeRepository.updateNode(node.num) { n ->
                        if (newIgnored) {
                            n.copy(
                                isIgnored = true,
                                position = Position(),
                                deviceMetrics = DeviceMetrics(),
                                publicKey = null,
                            )
                        } else {
                            n.copy(isIgnored = false)
                        }
                    }
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
