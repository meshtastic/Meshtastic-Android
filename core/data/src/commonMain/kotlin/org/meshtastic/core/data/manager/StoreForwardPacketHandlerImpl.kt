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
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.StoreForwardPacketHandler
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.StoreAndForward
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementation of [StoreForwardPacketHandler] that keeps legacy S&F parsing for backward compatibility.
 *
 * SF++ parsing/status updates are now delegated to the SDK and consumed via [org.meshtastic.core.data.radio.SdkStateBridge].
 */
@Single
class StoreForwardPacketHandlerImpl(
    private val nodeRepository: NodeRepository,
    private val packetRepository: Lazy<PacketRepository>,
    private val historyManager: HistoryManager,
    private val dataHandler: Lazy<MeshDataHandler>,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : StoreForwardPacketHandler {

    override fun handleStoreAndForward(packet: MeshPacket, dataPacket: DataPacket, myNodeNum: Int) {
        val payload = packet.decoded?.payload ?: return
        val u = StoreAndForward.ADAPTER.decode(payload)
        handleReceivedStoreAndForward(dataPacket, u, myNodeNum)
    }

    override fun handleStoreForwardPlusPlus(packet: MeshPacket) {
        Logger.d { "SFPP packet received from=${packet.from} (handled by SDK)" }
    }

    private fun handleReceivedStoreAndForward(dataPacket: DataPacket, s: StoreAndForward, myNodeNum: Int) {
        val lastRequest = s.history?.last_request ?: 0
        Logger.d { "StoreAndForward from=${dataPacket.from} lastRequest=$lastRequest" }
        when {
            s.stats != null -> {
                val text = s.stats.toString()
                val u =
                    dataPacket.copy(
                        bytes = text.encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                    )
                dataHandler.value.rememberDataPacket(u, myNodeNum)
            }

            s.history != null -> {
                val h = s.history!!
                val text =
                    "Total messages: ${h.history_messages}\n" +
                        "History window: ${h.window.milliseconds.inWholeMinutes} min\n" +
                        "Last request: ${h.last_request}"
                val u =
                    dataPacket.copy(
                        bytes = text.encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                    )
                dataHandler.value.rememberDataPacket(u, myNodeNum)
                historyManager.updateStoreForwardLastRequest("router_history", h.last_request, "Unknown")
            }

            s.heartbeat != null -> {
                val hb = s.heartbeat!!
                Logger.d { "rxHeartbeat from=${dataPacket.from} period=${hb.period} secondary=${hb.secondary}" }
            }

            s.text != null -> {
                if (s.rr == StoreAndForward.RequestResponse.ROUTER_TEXT_BROADCAST) {
                    dataPacket.to = DataPacket.BROADCAST
                }
                val u = dataPacket.copy(bytes = s.text, dataType = PortNum.TEXT_MESSAGE_APP.value)
                dataHandler.value.rememberDataPacket(u, myNodeNum)
            }

            else -> {}
        }
    }
}
