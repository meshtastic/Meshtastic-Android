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

/**
 * Handles inline reply and mark-read actions from car messaging notifications. Uses explicit intent targeting to
 * prevent interception by other apps.
 */
class CarReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CarNotificationManager.ACTION_REPLY -> handleReply(intent)
            CarNotificationManager.ACTION_MARK_READ -> handleMarkRead(intent)
        }
    }

    private fun handleReply(intent: Intent) {
        val conversationId = intent.getStringExtra(CarNotificationManager.EXTRA_CONVERSATION_ID) ?: return
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(CarNotificationManager.KEY_TEXT_REPLY)?.toString() ?: return

        Logger.d(tag = TAG) { "Reply to $conversationId: $replyText" }
        // TODO: Wire to message send repository once car messaging send is implemented
    }

    private fun handleMarkRead(intent: Intent) {
        val conversationId = intent.getStringExtra(CarNotificationManager.EXTRA_CONVERSATION_ID) ?: return
        Logger.d(tag = TAG) { "Mark read: $conversationId" }
        // TODO: Wire to read receipt repository
    }

    companion object {
        private const val TAG = "CarReplyReceiver"
    }
}
