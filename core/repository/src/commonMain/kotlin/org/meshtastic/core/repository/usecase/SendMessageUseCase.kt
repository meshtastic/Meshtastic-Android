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
package org.meshtastic.core.repository.usecase

import co.touchlab.kermit.Logger
import org.meshtastic.core.common.util.HomoglyphCharacterStringTransformer
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.proto.Config
import kotlin.random.Random

/**
 * Use case for sending a message over the mesh network.
 *
 * This component orchestrates the process of:
 * 1. Resolving the destination and sender information.
 * 2. Handling implicit actions for direct messages (e.g., sharing contacts, favoriting).
 * 3. Applying message transformations (e.g., homoglyph encoding).
 * 4. Persisting the outgoing message in the local history.
 * 5. Enqueuing the message for durable delivery via the platform's message queue.
 *
 * This implementation is platform-agnostic and relies on injected repositories and controllers.
 */
interface SendMessageUseCase {
    suspend operator fun invoke(text: String, contactKey: String = "0${DataPacket.ID_BROADCAST}", replyId: Int? = null)
}

@Suppress("TooGenericExceptionCaught")
class SendMessageUseCaseImpl(
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val radioController: RadioController,
    private val homoglyphEncodingPrefs: HomoglyphPrefs,
    private val messageQueue: MessageQueue,
) : SendMessageUseCase {

    /**
     * Executes the send message workflow.
     *
     * @param text The plain text message to send.
     * @param contactKey The identifier of the target contact or channel (e.g., "0!ffffffff" for broadcast).
     * @param replyId Optional ID of a message being replied to.
     */
    @Suppress("NestedBlockDepth", "LongMethod", "CyclomaticComplexMethod")
    override suspend operator fun invoke(text: String, contactKey: String, replyId: Int?) {
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        val ourNode = nodeRepository.ourNodeInfo.value
        val fromId = ourNode?.user?.id ?: DataPacket.ID_LOCAL

        // Direct message side-effects: share the contact's public key (PKI) or
        // favorite the node (legacy) before sending the first message.  PKI DMs use
        // channel == PKC_CHANNEL_INDEX (8); legacy DMs have no channel prefix
        // (channel == null).  Both formats target a specific node.
        val isDirectMessage = channel == null || channel == DataPacket.PKC_CHANNEL_INDEX
        if (isDirectMessage) {
            val destNode = nodeRepository.getNode(dest)
            val fwVersion = ourNode?.metadata?.firmware_version
            val isClientBase = ourNode?.user?.role == Config.DeviceConfig.Role.CLIENT_BASE
            val capabilities = Capabilities(fwVersion)

            if (capabilities.canSendVerifiedContacts) {
                // Best-effort: inform firmware of the destination's public key
                // for its NodeDB cache.  The MeshPacket itself carries the key
                // directly, so the message can be encrypted regardless.
                sendSharedContact(destNode)
            } else if (channel == null) {
                // Legacy favoriting only applies to old-style DMs without PKI
                if (!destNode.isFavorite && !isClientBase) {
                    favoriteNode(destNode)
                }
            }
        }

        // Apply homoglyph encoding
        val finalMessageText =
            if (homoglyphEncodingPrefs.homoglyphEncodingEnabled.value) {
                HomoglyphCharacterStringTransformer.optimizeUtf8StringWithHomoglyphs(text)
            } else {
                text
            }

        val packetId = Random.nextInt(1, Int.MAX_VALUE)

        val packet =
            DataPacket(dest, channel ?: 0, finalMessageText, replyId).apply {
                from = fromId
                id = packetId
                status = MessageStatus.QUEUED
            }

        try {
            // Write to the DB to immediately reflect the queued state on the UI
            packetRepository.savePacket(
                myNodeNum = ourNode?.num ?: 0,
                contactKey = contactKey,
                packet = packet,
                receivedTime = nowMillis,
            )

            // Enqueue for durable transmission via the platform-specific queue
            messageQueue.enqueue(packetId)
        } catch (ex: Exception) {
            Logger.e(ex) { "Failed to enqueue message packet" }
        }
    }

    private suspend fun favoriteNode(node: Node) {
        try {
            radioController.favoriteNode(node.num)
        } catch (ex: Exception) {
            Logger.e(ex) { "Favorite node error" }
        }
    }

    private suspend fun sendSharedContact(node: Node) {
        try {
            val accepted = radioController.sendSharedContact(node.num)
            if (!accepted) {
                Logger.w { "Shared contact for node ${node.num} was not acknowledged by the radio" }
            }
        } catch (ex: Exception) {
            Logger.e(ex) { "Send shared contact error" }
        }
    }
}
