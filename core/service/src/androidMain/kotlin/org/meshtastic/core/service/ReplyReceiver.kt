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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase

/**
 * A [BroadcastReceiver] that handles inline replies from notifications.
 *
 * This receiver is triggered when a user replies to a message directly from a notification. It extracts the reply text
 * and the contact key from the intent, sends the message through [SendMessageUseCase] — so notification replies get the
 * same pipeline as in-app sends (history save, durable queue, transforms) — and then cancels the notification.
 */
class ReplyReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val sendMessageUseCase: SendMessageUseCase by inject()

    private val meshServiceNotifications: MeshNotificationManager by inject()

    private val packetRepository: PacketRepository by inject()

    private val dispatchers: CoroutineDispatchers by inject()

    private val scope by lazy { CoroutineScope(dispatchers.io + SupervisorJob()) }

    companion object {
        private const val TAG = "ReplyReceiver"
        const val REPLY_ACTION = "org.meshtastic.app.REPLY_ACTION"
        const val CONTACT_KEY = "contactKey"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    @Suppress("TooGenericExceptionCaught") // a reply must never crash the receiver, whatever the radio throws
    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        if (remoteInput == null) {
            Logger.w(tag = TAG) { "reply received but RemoteInput was null" }
            return
        }

        val contactKey = intent.getStringExtra(CONTACT_KEY).orEmpty()
        val message = remoteInput.getCharSequence(KEY_TEXT_REPLY)?.toString().orEmpty()
        Logger.i(tag = TAG) { "reply for contactKey=$contactKey len=${message.length}" }

        val pendingResult: PendingResult? = goAsync()
        scope.launch {
            try {
                // Send first so the reply isn't lost; then dismiss. The cancel can't break the send.
                sendMessageUseCase(message, contactKey)
                // Replying implies the conversation has been read — mark it so, like the mark-as-read action.
                // Android Auto keys notification dismissal off read state, not just cancel().
                packetRepository.clearUnreadCount(contactKey, nowMillis)
                Logger.i(tag = TAG) { "reply sent + marked read" }
            } catch (e: Exception) {
                Logger.e(tag = TAG, throwable = e) { "reply send failed" }
            } finally {
                runCatching { meshServiceNotifications.cancelMessageNotification(contactKey) }
                    .onFailure { Logger.e(tag = TAG, throwable = it) { "cancel notification failed" } }
                pendingResult?.finish()
            }
        }
    }
}
