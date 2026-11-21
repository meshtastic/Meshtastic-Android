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
package org.meshtastic.core.repository

import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.RemoteShell
import org.meshtastic.proto.SharedContact

/**
 * Messaging operations — sending data packets, reactions, and shared contacts.
 *
 * Mirrors the SDK's send/messaging surface. When the SDK is adopted, implementations delegate to `RadioClient.send()` /
 * `RadioClient.sendText()`.
 *
 * @see RadioController which extends this interface for backward compatibility
 */
interface MessagingController {

    /** Sends a data packet to the mesh. */
    suspend fun sendMessage(packet: DataPacket)

    /** Sends an emoji reaction to a message. Awaits local DB persistence. */
    suspend fun sendReaction(emoji: String, replyId: Int, contactKey: String)

    /** Imports a shared contact into the firmware's NodeDB. */
    suspend fun importContact(contact: SharedContact)

    /**
     * Sends our shared contact information (identity and public key) to the firmware's NodeDB.
     *
     * @param nodeNum The destination node number.
     * @return `true` if the radio accepted the contact, `false` on timeout or failure.
     */
    suspend fun sendSharedContact(nodeNum: Int): Boolean

    /** A fresh non-zero remote shell session ID, or null without a radio session. */
    fun newRemoteShellSessionId(): Int?

    /**
     * Sends one remote shell [frame] to [destNum], PKI-encrypted with no `want_ack`.
     *
     * @throws PacketQueueRejectedException without a radio session, or with no public key stored for [destNum].
     */
    suspend fun sendRemoteShell(destNum: Int, frame: RemoteShell)
}
