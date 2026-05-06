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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.sdk.StoreForwardEvent

internal class SdkPacketBridge(
    private val serviceRepository: ServiceRepository,
    private val packetRepository: Lazy<PacketRepository>,
    private val nodeRepository: NodeRepository,
) {
    fun observe(accessor: RadioClientAccessor, scope: CoroutineScope) {
        accessor.client
            .flatMapLatest { client -> client?.packets ?: emptyFlow() }
            .onEach { packet -> serviceRepository.emitMeshPacket(packet) }
            .launchIn(scope)

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
            .onEach(::handleStoreForwardEvent)
            .launchIn(scope)
    }

    private suspend fun handleStoreForwardEvent(event: StoreForwardEvent) {
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
        }
    }
}
