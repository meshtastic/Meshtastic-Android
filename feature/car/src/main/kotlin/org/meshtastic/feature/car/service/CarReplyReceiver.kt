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
package org.meshtastic.feature.car.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase

/**
 * Handles inline reply and mark-read actions from car messaging notifications. Uses [goAsync] to keep the receiver
 * alive while the coroutine completes, preventing premature process kill.
 */
class CarReplyReceiver :
    BroadcastReceiver(),
    KoinComponent {

    private val sendMessageUseCase: SendMessageUseCase by inject()
    private val packetRepository: PacketRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (intent.action) {
                    CarNotificationManager.ACTION_REPLY -> handleReply(intent)
                    CarNotificationManager.ACTION_MARK_READ -> handleMarkRead(intent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReply(intent: Intent) {
        val conversationId = intent.getStringExtra(CarNotificationManager.EXTRA_CONVERSATION_ID) ?: return
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(CarNotificationManager.KEY_TEXT_REPLY)?.toString() ?: return

        Logger.d(tag = TAG) { "Reply to conversation: $conversationId (${replyText.length} chars)" }
        runCatching { sendMessageUseCase(replyText, conversationId) }
            .onFailure { error -> Logger.e(tag = TAG, throwable = error) { "Failed to send reply" } }
    }

    private suspend fun handleMarkRead(intent: Intent) {
        val conversationId = intent.getStringExtra(CarNotificationManager.EXTRA_CONVERSATION_ID) ?: return
        Logger.d(tag = TAG) { "Mark read: $conversationId" }
        runCatching { packetRepository.clearUnreadCount(conversationId, System.currentTimeMillis()) }
            .onFailure { error -> Logger.e(tag = TAG, throwable = error) { "Failed to mark as read" } }
    }

    companion object {
        private const val TAG = "CarReplyReceiver"
    }
}
