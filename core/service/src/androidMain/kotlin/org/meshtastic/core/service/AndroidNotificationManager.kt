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

import android.app.NotificationChannel
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.meshtastic_alerts_notifications
import org.meshtastic.core.resources.meshtastic_low_battery_notifications
import org.meshtastic.core.resources.meshtastic_messages_notifications
import org.meshtastic.core.resources.meshtastic_new_nodes_notifications
import org.meshtastic.core.resources.meshtastic_service_notifications
import android.app.NotificationManager as SystemNotificationManager

@Single
class AndroidNotificationManager(private val context: Context) : NotificationManager {

    private val notificationManager = context.getSystemService<SystemNotificationManager>()!!

    private data class ChannelConfig(val id: String, val importance: Int)

    init {
        initChannels()
    }

    private fun initChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels =
                listOf(
                    createChannel(Notification.Category.Message, Res.string.meshtastic_messages_notifications),
                    createChannel(Notification.Category.NodeEvent, Res.string.meshtastic_new_nodes_notifications),
                    createChannel(Notification.Category.Battery, Res.string.meshtastic_low_battery_notifications),
                    createChannel(Notification.Category.Alert, Res.string.meshtastic_alerts_notifications),
                    createChannel(Notification.Category.Service, Res.string.meshtastic_service_notifications),
                )
            notificationManager.createNotificationChannels(channels)
            notificationManager.removeLegacyCategoryChannels()
        }
    }

    private fun createChannel(
        category: Notification.Category,
        nameRes: org.jetbrains.compose.resources.StringResource,
    ): NotificationChannel {
        val channelConfig = category.channelConfig()
        return NotificationChannel(channelConfig.id, getString(nameRes), channelConfig.importance)
    }

    // Keep category-to-channel mapping aligned with MeshServiceNotificationsImpl.NotificationType IDs.
    private fun Notification.Category.channelConfig(): ChannelConfig = when (this) {
        Notification.Category.Message ->
            ChannelConfig(
                id = NotificationChannels.MESSAGES,
                importance = SystemNotificationManager.IMPORTANCE_HIGH,
            )
        Notification.Category.NodeEvent ->
            ChannelConfig(
                id = NotificationChannels.NEW_NODES,
                importance = SystemNotificationManager.IMPORTANCE_DEFAULT,
            )
        Notification.Category.Battery ->
            ChannelConfig(
                id = NotificationChannels.LOW_BATTERY,
                importance = SystemNotificationManager.IMPORTANCE_DEFAULT,
            )
        Notification.Category.Alert ->
            ChannelConfig(id = NotificationChannels.ALERTS, importance = SystemNotificationManager.IMPORTANCE_HIGH)
        Notification.Category.Service ->
            ChannelConfig(id = NotificationChannels.SERVICE, importance = SystemNotificationManager.IMPORTANCE_MIN)
    }

    override fun dispatch(notification: Notification) {
        val builder =
            NotificationCompat.Builder(context, notification.category.channelConfig().id)
                .setContentTitle(notification.title)
                .setContentText(notification.message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setSilent(notification.isSilent)

        notification.group?.let { builder.setGroup(it) }

        if (notification.type == Notification.Type.Error) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        val id = notification.id ?: notification.hashCode()
        notificationManager.notify(id, builder.build())
    }

    override fun cancel(id: Int) {
        notificationManager.cancel(id)
    }

    override fun cancelAll() {
        notificationManager.cancelAll()
    }
}
