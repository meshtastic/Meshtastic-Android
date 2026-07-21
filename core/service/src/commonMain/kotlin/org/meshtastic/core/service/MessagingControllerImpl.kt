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
package org.meshtastic.core.service

import co.touchlab.kermit.Logger
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.ContactKey
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MessagingController
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User

/**
 * [MessagingController] implementation: sends data packets, reactions, and shared contacts.
 *
 * Focused collaborator of [RadioControllerImpl]. Mirrors the SDK's `RadioClient.send*` surface — when the SDK is
 * adopted this becomes a thin adapter over `RadioClient`.
 */
internal class MessagingControllerImpl(
    private val commandSender: CommandSender,
    private val nodeManager: NodeManager,
    private val nodeRepository: NodeRepository,
    private val dataHandler: Lazy<MeshDataHandler>,
    private val analytics: PlatformAnalytics,
    private val packetRepository: Lazy<PacketRepository>,
) : MessagingController {

    private val myNodeNum: Int
        get() = nodeManager.myNodeNum.value ?: 0

    override suspend fun sendMessage(packet: DataPacket) {
        commandSender.sendData(packet)
        dataHandler.value.rememberDataPacket(packet, myNodeNum, false)
        val bytes = packet.bytes ?: ByteString.EMPTY
        analytics.track("data_send", DataPair("num_bytes", bytes.size), DataPair("type", packet.dataType))
    }

    override suspend fun sendReaction(emoji: String, replyId: Int, contactKey: String) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val parsedKey = ContactKey(contactKey)
        val channel = parsedKey.channel
        val destId = parsedKey.addressString
        val dataPacket =
            DataPacket(
                to = destId,
                dataType = PortNum.TEXT_MESSAGE_APP.value,
                bytes = emoji.encodeToByteArray().toByteString(),
                channel = channel,
                replyId = replyId,
                wantAck = true,
                emoji = EMOJI_INDICATOR,
            )
                .apply { from = nodeManager.getMyId().takeIf { it.isNotEmpty() } ?: NodeAddress.ID_LOCAL }
        commandSender.sendData(dataPacket)
        val user = nodeManager.nodeDBbyNodeNum[myNum]?.user ?: User(id = nodeManager.getMyId())
        packetRepository.value.insertReaction(
            Reaction(
                replyId = replyId,
                user = user,
                emoji = emoji,
                timestamp = nowMillis,
                snr = 0f,
                rssi = 0,
                hopsAway = 0,
                packetId = dataPacket.id,
                status = MessageStatus.QUEUED,
                to = destId,
                channel = channel,
            ),
            myNum,
        )
    }

    override suspend fun sendSharedContact(nodeNum: Int): Boolean {
        val myNum = nodeManager.myNodeNum.value ?: return false
        val nodeDef = nodeRepository.getNode(NodeAddress.numToDefaultId(nodeNum))
        val contact =
            SharedContact(node_num = nodeDef.num, user = nodeDef.user, manually_verified = nodeDef.manuallyVerified)
        return safeCatching { commandSender.sendAdminAwait(myNum) { AdminMessage(add_contact = contact) } }
            .getOrDefault(false)
    }

    override suspend fun importContact(contact: SharedContact) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val user = contact.user
        if (contact.node_num == 0 || user == null) {
            Logger.w { "importContact rejected: missing node_num or user (node_num=${contact.node_num})" }
            return
        }
        // Cross-platform policy: honor the verification state encoded by the sharer as-is
        // (see meshtastic/design standards/audits/nfc-alignment-audit.md).
        commandSender.sendAdmin(myNum) { AdminMessage(add_contact = contact) }
        nodeManager.handleReceivedUser(contact.node_num, user, manuallyVerified = contact.manually_verified)
    }

    private companion object {
        private const val EMOJI_INDICATOR = 1
    }
}
