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
package org.meshtastic.wear.presentation

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.core.model.WearableReply
import org.meshtastic.wear.presentation.model.WearableMessageRepository

@KoinViewModel
class ChatViewModel(
    private val repository: WearableMessageRepository,
    application: Application,
) : ViewModel() {
    private val messageClient by lazy { Wearable.getMessageClient(application) }
    private val nodeClient by lazy { Wearable.getNodeClient(application) }
    private val json = Json

    val messages: StateFlow<List<WearableMessage>> = repository.syncedMessages

    fun sendMessage(contactKey: String, text: String) {
        val lastMsg = messages.value.firstOrNull { it.contactKey == contactKey }
        val address = lastMsg?.address
        val channelIndex = lastMsg?.channelIndex ?: 0

        // Add to local history immediately for UI feedback
        repository.addLocalMessage(
            WearableMessage(
                uuid = System.currentTimeMillis(),
                contactKey = contactKey,
                senderName = "You",
                senderShortName = "YO",
                text = text,
                isMe = true,
                timestamp = System.currentTimeMillis(),
                status = MessageStatus.QUEUED
            )
        )

        viewModelScope.launch {
            try {
                val reply = WearableReply(address = address, channelIndex = channelIndex, text = text)
                val replyJson = json.encodeToString(reply)
                
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, "/send_message", replyJson.toByteArray()).await()
                }
                Logger.d { "Sent reply: $text to $contactKey" }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to send message" }
            }
        }
    }

    fun openOnPhone(contactKey: String) {
        viewModelScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, "/open_on_phone", contactKey.toByteArray()).await()
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to trigger open on phone" }
            }
        }
    }
}
