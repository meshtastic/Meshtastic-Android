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
import okio.IOException
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.SfppHasher
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.StoreForwardPacketHandler
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.StoreAndForward
import org.meshtastic.proto.StoreForwardPlusPlus
import kotlin.time.Duration.Companion.milliseconds

/** Implementation of [StoreForwardPacketHandler] that handles both legacy S&F and SF++ packets. */
@Single
class StoreForwardPacketHandlerImpl(
    private val nodeManager: NodeManager,
    private val packetRepository: Lazy<PacketRepository>,
    private val historyManager: HistoryManager,
    private val dataHandler: Lazy<MeshDataHandler>,
    private val radioInterfaceService: RadioInterfaceService,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : StoreForwardPacketHandler {

    override fun handleStoreAndForward(
        packet: MeshPacket,
        dataPacket: DataPacket,
        myNodeNum: Int,
        session: RadioSessionContext?,
    ) {
        val payload = packet.decoded?.payload ?: return
        val u =
            try {
                StoreAndForward.ADAPTER.decode(payload)
            } catch (e: IOException) {
                Logger.e(e) { "Failed to parse StoreAndForward packet" }
                return
            }
        handleReceivedStoreAndForward(dataPacket, u, myNodeNum, session)
    }

    @Suppress("LongMethod", "ReturnCount")
    override fun handleStoreForwardPlusPlus(packet: MeshPacket, session: RadioSessionContext?) {
        val payload = packet.decoded?.payload ?: return
        val sfpp =
            try {
                StoreForwardPlusPlus.ADAPTER.decode(payload)
            } catch (e: IOException) {
                Logger.e(e) { "Failed to parse StoreForwardPlusPlus packet" }
                return
            }
        Logger.d { "Received StoreForwardPlusPlus packet: $sfpp" }

        when (sfpp.sfpp_message_type) {
            StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE,
            StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE_FIRSTHALF,
            StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE_SECONDHALF,
            -> handleLinkProvide(sfpp, session)

            StoreForwardPlusPlus.SFPP_message_type.CANON_ANNOUNCE -> handleCanonAnnounce(sfpp, session)

            StoreForwardPlusPlus.SFPP_message_type.CHAIN_QUERY -> {
                Logger.i { "SF++: Node ${packet.from} is querying chain status" }
            }

            StoreForwardPlusPlus.SFPP_message_type.LINK_REQUEST -> {
                Logger.i { "SF++: Node ${packet.from} is requesting links" }
            }
        }
    }

    private fun handleLinkProvide(sfpp: StoreForwardPlusPlus, session: RadioSessionContext?) {
        val isFragment = sfpp.sfpp_message_type != StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE

        val status = if (sfpp.commit_hash.size == 0) MessageStatus.SFPP_ROUTING else MessageStatus.SFPP_CONFIRMED

        val hash =
            when {
                sfpp.message_hash.size != 0 -> sfpp.message_hash.toByteArray()

                !isFragment && sfpp.message.size != 0 -> {
                    SfppHasher.computeMessageHash(
                        encryptedPayload = sfpp.message.toByteArray(),
                        to =
                        if (sfpp.encapsulated_to == 0) {
                            NodeAddress.NODENUM_BROADCAST
                        } else {
                            sfpp.encapsulated_to
                        },
                        from = sfpp.encapsulated_from,
                        id = sfpp.encapsulated_id,
                    )
                }

                else -> null
            } ?: return

        Logger.d {
            "SFPP updateStatus: packetId=${sfpp.encapsulated_id} from=${sfpp.encapsulated_from} " +
                "to=${sfpp.encapsulated_to} myNodeNum=${nodeManager.myNodeNum.value} status=$status"
        }
        radioInterfaceService.launchSessionWork(
            scope = scope,
            session = session,
            onRejected = { Logger.d { "Dropped SF++ work from a retired transport session" } },
        ) {
            packetRepository.value.updateSFPPStatus(
                packetId = sfpp.encapsulated_id,
                from = sfpp.encapsulated_from,
                to = sfpp.encapsulated_to,
                hash = hash,
                status = status,
                rxTime = sfpp.encapsulated_rxtime.toLong() and 0xFFFFFFFFL,
                myNodeNum = nodeManager.myNodeNum.value ?: 0,
            )
        }
    }

    private fun handleCanonAnnounce(sfpp: StoreForwardPlusPlus, session: RadioSessionContext?) {
        radioInterfaceService.launchSessionWork(
            scope = scope,
            session = session,
            onRejected = { Logger.d { "Dropped SF++ work from a retired transport session" } },
        ) {
            sfpp.message_hash.let {
                packetRepository.value.updateSFPPStatusByHash(
                    hash = it.toByteArray(),
                    status = MessageStatus.SFPP_CONFIRMED,
                    rxTime = sfpp.encapsulated_rxtime.toLong() and 0xFFFFFFFFL,
                )
            }
        }
    }

    private fun handleReceivedStoreAndForward(
        dataPacket: DataPacket,
        s: StoreAndForward,
        myNodeNum: Int,
        session: RadioSessionContext?,
    ) {
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
                dataHandler.value.rememberDataPacket(u, myNodeNum, session = session)
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
                dataHandler.value.rememberDataPacket(u, myNodeNum, session = session)
                historyManager.updateStoreForwardLastRequest("router_history", h.last_request, "Unknown")
            }

            s.heartbeat != null -> {
                val hb = s.heartbeat!!
                Logger.d { "rxHeartbeat from=${dataPacket.from} period=${hb.period} secondary=${hb.secondary}" }
            }

            s.text != null -> {
                if (s.rr == StoreAndForward.RequestResponse.ROUTER_TEXT_BROADCAST) {
                    dataPacket.to = NodeAddress.ID_BROADCAST
                }
                val u = dataPacket.copy(bytes = s.text, dataType = PortNum.TEXT_MESSAGE_APP.value)
                dataHandler.value.rememberDataPacket(u, myNodeNum, session = session)
            }

            else -> {}
        }
    }
}
