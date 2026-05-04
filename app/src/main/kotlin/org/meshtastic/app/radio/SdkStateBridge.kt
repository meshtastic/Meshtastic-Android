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
package org.meshtastic.app.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState as AppConnectionState
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import org.meshtastic.proto.Position as ProtoPosition
import org.meshtastic.sdk.ConnectionState as SdkConnectionState
import org.meshtastic.sdk.MeshEvent
import org.meshtastic.sdk.NodeChange

/**
 * Bridges SDK reactive flows into the legacy repository layer.
 *
 * The SDK owns the transport and all state; this bridge maps SDK emissions into [ServiceRepository]
 * and [NodeManager] so that existing feature-module UI code (which observes those repositories)
 * continues to work without modification.
 *
 * **Lifecycle:** Created as a Koin `@Single`. Automatically subscribes to [RadioClientProvider.client]
 * and starts/stops collection as clients come and go. No explicit lifecycle management needed.
 *
 * **Mapping policy:**
 * - SDK `Connected` → app `Connected`
 * - SDK `Connecting` / `Configuring` → app `Connecting`
 * - SDK `Reconnecting` → app `DeviceSleep` (firmware went to sleep or transport dropped)
 * - SDK `Disconnected` → app `Disconnected`
 */
@Single
class SdkStateBridge(
    private val provider: RadioClientProvider,
    private val serviceRepository: ServiceRepository,
    private val nodeManager: NodeManager,
    private val dispatchers: CoroutineDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    init {
        startBridge()
    }

    private fun startBridge() {

        // ── Connection state bridge ─────────────────────────────────────────
        provider.client
            .flatMapLatest { client -> client?.connection ?: flowOf(SdkConnectionState.Disconnected) }
            .onEach { sdkState -> serviceRepository.setConnectionState(mapConnectionState(sdkState)) }
            .launchIn(scope)

        // ── Node updates bridge ─────────────────────────────────────────────
        provider.client
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
                    is NodeChange.Added -> {
                        nodeManager.installNodeInfo(change.node, withBroadcast = true)
                    }
                    is NodeChange.Updated -> {
                        nodeManager.installNodeInfo(change.node, withBroadcast = true)
                    }
                    is NodeChange.Removed -> {
                        nodeManager.removeByNodenum(change.nodeId.raw)
                    }
                }
            }
            .launchIn(scope)

        // ── Own node identity bridge ────────────────────────────────────────
        provider.client
            .flatMapLatest { client -> client?.ownNode ?: flowOf(null) }
            .onEach { ownNode ->
                if (ownNode != null) {
                    nodeManager.setMyNodeNum(ownNode.num)
                }
            }
            .launchIn(scope)

        // ── Inbound packet bridge (telemetry, position, user updates) ───────
        provider.client
            .flatMapLatest { client -> client?.packets ?: flowOf() }
            .onEach { packet ->
                val decoded = packet.decoded ?: return@onEach
                val fromNum = packet.from
                val myNodeNum = nodeManager.myNodeNum.value ?: 0
                val payloadBytes = decoded.payload?.toByteArray() ?: return@onEach

                when (decoded.portnum) {
                    PortNum.TELEMETRY_APP -> {
                        runCatching { Telemetry.ADAPTER.decode(payloadBytes) }
                            .onSuccess { nodeManager.handleReceivedTelemetry(fromNum, it) }
                    }
                    PortNum.POSITION_APP -> {
                        runCatching { ProtoPosition.ADAPTER.decode(payloadBytes) }
                            .onSuccess { nodeManager.handleReceivedPosition(fromNum, myNodeNum, it, packet.rx_time.toLong()) }
                    }
                    PortNum.NODEINFO_APP -> {
                        runCatching { User.ADAPTER.decode(payloadBytes) }
                            .onSuccess { nodeManager.handleReceivedUser(fromNum, it, packet.channel) }
                    }
                    else -> {
                        // Other port types are handled directly by feature VMs via SDK flows
                    }
                }
            }
            .launchIn(scope)

        // ── Events bridge (notifications, warnings) ─────────────────────────
        provider.client
            .flatMapLatest { client -> client?.events ?: flowOf() }
            .onEach { event ->
                when (event) {
                    is MeshEvent.DeviceRebooted -> {
                        Logger.i { "[SdkBridge] Device rebooted" }
                        serviceRepository.setClientNotification(
                            ClientNotification(message = "Device rebooted"),
                        )
                    }
                    is MeshEvent.SecurityWarning -> {
                        Logger.w { "[SdkBridge] Security warning: $event" }
                    }
                    is MeshEvent.PacketsDropped -> {
                        Logger.w { "[SdkBridge] Packets dropped: ${event.count} from ${event.flow}" }
                    }
                    else -> {
                        Logger.d { "[SdkBridge] Event: $event" }
                    }
                }
            }
            .launchIn(scope)

        Logger.i { "SdkStateBridge started" }
    }

    companion object {
        /** Map SDK connection states to the app's legacy connection states. */
        fun mapConnectionState(sdkState: SdkConnectionState): AppConnectionState = when (sdkState) {
            is SdkConnectionState.Disconnected -> AppConnectionState.Disconnected
            is SdkConnectionState.Connecting -> AppConnectionState.Connecting
            is SdkConnectionState.Configuring -> AppConnectionState.Connecting
            is SdkConnectionState.Connected -> AppConnectionState.Connected
            is SdkConnectionState.Reconnecting -> AppConnectionState.DeviceSleep
        }
    }
}
