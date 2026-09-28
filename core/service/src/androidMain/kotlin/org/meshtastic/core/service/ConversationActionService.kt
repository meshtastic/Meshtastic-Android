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

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.RemoteInput
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.usecase.SendMessageUseCase

/**
 * Runs a conversation notification's reply, mark-as-read and thumbs-up actions, which open no UI.
 *
 * A Service rather than a receiver because Android Auto's notification-messaging contract has these actions handled by
 * a Service; the platform allowlists the app to start one from a notification action. Every action finishes by
 * re-posting or cancelling the conversation, which is the only feedback the phone, a watch or a car gets.
 */
class ConversationActionService :
    Service(),
    KoinComponent {

    private val sendMessageUseCase: SendMessageUseCase by inject()
    private val packetRepository: PacketRepository by inject()
    private val radioController: RadioController by inject()
    private val serviceNotifications: MeshNotificationManager by inject()
    private val dispatchers: CoroutineDispatchers by inject()
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.io) }

    // stopSelf(startId) is a no-op unless startId is the newest start, so the service only stops once the last
    // outstanding action has finished, however the actions interleave.
    private var running = 0
    private var newestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(this) {
            running++
            newestStartId = startId
        }
        scope.launch {
            try {
                if (intent != null) handle(intent)
            } finally {
                finishedStartId()?.let(::stopSelf)
            }
        }
        return START_NOT_STICKY
    }

    /** The start id to stop with once the last outstanding action is done, or null while others still run. */
    private fun finishedStartId(): Int? = synchronized(this) {
        running--
        if (running == 0) newestStartId else null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun handle(intent: Intent) {
        val contactKey = intent.getStringExtra(EXTRA_CONTACT_KEY) ?: return
        when (intent.action) {
            ACTION_REPLY -> reply(intent, contactKey)

            ACTION_MARK_AS_READ -> {
                packetRepository.clearUnreadCount(contactKey, nowMillis)
                serviceNotifications.cancelMessageNotification(contactKey)
            }

            ACTION_REACT -> react(intent, contactKey)

            else -> Logger.w(tag = TAG) { "Unknown conversation action ${intent.action}" }
        }
    }

    @Suppress("TooGenericExceptionCaught") // a reply must never crash the service, whatever the radio throws
    private suspend fun reply(intent: Intent, contactKey: String) {
        val message = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT_REPLY)?.toString()
        if (message.isNullOrBlank()) {
            Logger.w(tag = TAG) { "Reply without text for contactKey=$contactKey" }
            return
        }
        try {
            // Send first so the reply is never lost to a notification failure.
            sendMessageUseCase(message, contactKey)
            // Replying reads the conversation; Android Auto keys dismissal off read state, not just cancel().
            packetRepository.clearUnreadCount(contactKey, nowMillis)
            // Re-post with the reply appended so the RemoteInput spinner resolves with visible feedback; fall back
            // to dismissal so it never hangs.
            safeCatching { serviceNotifications.refreshConversationAfterReply(contactKey) }
                .onFailure {
                    Logger.e(tag = TAG, throwable = it) { "Refresh after reply failed" }
                    safeCatching { serviceNotifications.cancelMessageNotification(contactKey) }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(tag = TAG, throwable = e) { "Reply send failed" }
            safeCatching { serviceNotifications.cancelMessageNotification(contactKey) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun react(intent: Intent, contactKey: String) {
        val emoji = intent.getStringExtra(EXTRA_EMOJI) ?: return
        val replyId = intent.getIntExtra(EXTRA_REPLY_ID, 0)
        try {
            radioController.sendReaction(emoji, replyId, contactKey)
            safeCatching { serviceNotifications.refreshConversationAfterReply(contactKey) }
                .onFailure { Logger.e(tag = TAG, throwable = it) { "Refresh after reaction failed" } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(tag = TAG, throwable = e) { "Reaction send failed" }
        }
    }

    companion object {
        private const val TAG = "ConversationActionService"
        const val ACTION_REPLY = "org.meshtastic.app.REPLY_ACTION"
        const val ACTION_MARK_AS_READ = "org.meshtastic.app.MARK_AS_READ"
        const val ACTION_REACT = "org.meshtastic.app.REACT_ACTION"
        const val EXTRA_CONTACT_KEY = "contact_key"
        const val EXTRA_REPLY_ID = "reply_id"
        const val EXTRA_EMOJI = "emoji"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }
}
