/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.entity.ReactionEntity
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.proto.PortNum
import javax.inject.Inject

@AndroidEntryPoint
class ReactionReceiver : BroadcastReceiver() {
    @Inject lateinit var commandSender: MeshCommandSender

    @Inject lateinit var meshServiceNotifications: MeshServiceNotifications

    @Inject lateinit var packetRepository: PacketRepository

    @Inject lateinit var nodeManager: MeshNodeManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val REACT_ACTION = "com.geeksville.mesh.REACT_ACTION"
        const val EXTRA_PACKET_ID = "packetId"
        const val EXTRA_EMOJI = "emoji"
        const val EXTRA_CONTACT_KEY = "contactKey"
        const val EXTRA_TO_ID = "toId"
        const val EXTRA_CHANNEL_INDEX = "channelIndex"
        private const val EMOJI_INDICATOR = 1
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != REACT_ACTION) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val packetId = intent.getIntExtra(EXTRA_PACKET_ID, 0)
                val emoji = intent.getStringExtra(EXTRA_EMOJI)
                val toId = intent.getStringExtra(EXTRA_TO_ID)
                val channelIndex = intent.getIntExtra(EXTRA_CHANNEL_INDEX, 0)
                val contactKey = intent.getStringExtra(EXTRA_CONTACT_KEY)

                @Suppress("ComplexCondition")
                if (packetId == 0 || emoji.isNullOrEmpty() || toId.isNullOrEmpty() || contactKey.isNullOrEmpty()) {
                    return@launch
                }

                // Reactions are text messages with a replyId and emoji set
                val reactionPacket =
                    DataPacket(
                        to = toId,
                        channel = channelIndex,
                        bytes = emoji.encodeToByteArray().toByteString(),
                        dataType = PortNum.TEXT_MESSAGE_APP.value,
                        replyId = packetId,
                        wantAck = true,
                        emoji = EMOJI_INDICATOR,
                    )
                commandSender.sendData(reactionPacket)

                val reaction =
                    ReactionEntity(
                        myNodeNum = nodeManager.myNodeNum ?: 0,
                        replyId = packetId,
                        userId = nodeManager.getMyId().takeIf { it.isNotEmpty() } ?: DataPacket.ID_LOCAL,
                        emoji = emoji,
                        timestamp = System.currentTimeMillis(),
                        packetId = reactionPacket.id,
                        status = MessageStatus.QUEUED,
                        to = toId,
                        channel = channelIndex,
                    )
                packetRepository.insertReaction(reaction)

                // Dismiss the notification after reacting
                meshServiceNotifications.cancelMessageNotification(contactKey)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
