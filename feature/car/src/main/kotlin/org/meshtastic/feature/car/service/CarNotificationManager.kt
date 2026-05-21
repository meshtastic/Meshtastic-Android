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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import org.koin.core.annotation.Single

@Single
class CarNotificationManager(private val context: Context) {

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL_ID, "Mesh Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Messages from Meshtastic mesh network"
                }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun postMessagingNotification(conversationId: String, senderName: String, messages: List<Pair<String, Long>>) {
        val person = Person.Builder().setName(senderName).build()

        val messagingStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
        messagingStyle.setConversationTitle(senderName)
        messages.forEach { (text, timestamp) -> messagingStyle.addMessage(text, timestamp, person) }

        val replyAction = buildReplyAction(conversationId)
        val markReadAction = buildMarkReadAction(conversationId)

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setStyle(messagingStyle)
                .addAction(replyAction)
                .addAction(markReadAction)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()

        NotificationManagerCompat.from(context).notify(conversationId.hashCode(), notification)
    }

    private fun buildReplyAction(conversationId: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel("Reply").build()

        val replyIntent =
            PendingIntent.getBroadcast(
                context,
                conversationId.hashCode(),
                Intent(ACTION_REPLY).putExtra(EXTRA_CONVERSATION_ID, conversationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

        return NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Reply", replyIntent)
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun buildMarkReadAction(conversationId: String): NotificationCompat.Action {
        val markReadIntent =
            PendingIntent.getBroadcast(
                context,
                conversationId.hashCode() + 1,
                Intent(ACTION_MARK_READ).putExtra(EXTRA_CONVERSATION_ID, conversationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Action.Builder(android.R.drawable.ic_menu_view, "Mark as Read", markReadIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "meshtastic_car_messages"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val ACTION_REPLY = "org.meshtastic.feature.car.REPLY"
        const val ACTION_MARK_READ = "org.meshtastic.feature.car.MARK_READ"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
    }
}
