/*
 * Copyright (c) 2025 Meshtastic LLC
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
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceRepository

/**
 * A [BroadcastReceiver] that handles inline replies from notifications.
 *
 * This receiver is triggered when a user replies to a message directly from a notification. It extracts the reply text
 * and the contact key from the intent, sends the message using the [ServiceRepository], and then cancels the original
 * notification.
 */
@AndroidEntryPoint
class ReplyReceiver : BroadcastReceiver() {
    @Inject lateinit var serviceRepository: ServiceRepository

    @Inject lateinit var meshServiceNotifications: MeshServiceNotifications

    companion object {
        const val REPLY_ACTION = "com.geeksville.mesh.REPLY_ACTION"
        const val CONTACT_KEY = "contactKey"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    private fun sendMessage(str: String, contactKey: String = "0${DataPacket.ID_BROADCAST}") {
        // contactKey: unique contact key filter (channel)+(nodeId)
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey
        val p = DataPacket(dest, channel ?: 0, str)
        serviceRepository.meshService?.send(p)
    }

    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)

        if (remoteInput != null) {
            val contactKey = intent.getStringExtra(CONTACT_KEY) ?: ""
            val message = remoteInput.getCharSequence(KEY_TEXT_REPLY)?.toString() ?: ""
            sendMessage(message, contactKey)
            MeshServiceNotificationsImpl(context).cancelMessageNotification(contactKey)
        }
    }
}
