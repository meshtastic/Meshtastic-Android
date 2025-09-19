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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.LocalStats
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.service.ReplyReceiver.Companion.KEY_TEXT_REPLY
import com.geeksville.mesh.util.formatUptime
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI

/**
 * Manages the creation and display of all app notifications.
 *
 * This class centralizes notification logic, including channel creation, builder configuration, and displaying
 * notifications for various events like new messages, alerts, and service status changes.
 */
@Suppress("TooManyFunctions")
class MeshServiceNotifications(private val context: Context) {

    private val notificationManager = context.getSystemService<NotificationManager>()!!

    companion object {
        private const val FIFTEEN_MINUTES_IN_MILLIS = 15L * 60 * 1000
        const val MAX_BATTERY_LEVEL = 100
        const val SERVICE_NOTIFY_ID = 101
        private val NOTIFICATION_LIGHT_COLOR = Color.BLUE
    }

    /**
     * Sealed class to define the properties of each notification channel. This centralizes channel configuration and
     * makes it type-safe.
     */
    private sealed class NotificationType(
        val channelId: String,
        @StringRes val channelNameRes: Int,
        val importance: Int,
    ) {
        object ServiceState :
            NotificationType(
                "my_service",
                R.string.meshtastic_service_notifications,
                NotificationManager.IMPORTANCE_MIN,
            )

        object DirectMessage :
            NotificationType(
                "my_messages",
                R.string.meshtastic_messages_notifications,
                NotificationManager.IMPORTANCE_HIGH,
            )

        object BroadcastMessage :
            NotificationType(
                "my_broadcasts",
                R.string.meshtastic_broadcast_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object Alert :
            NotificationType(
                "my_alerts",
                R.string.meshtastic_alerts_notifications,
                NotificationManager.IMPORTANCE_HIGH,
            )

        object NewNode :
            NotificationType(
                "new_nodes",
                R.string.meshtastic_new_nodes_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object LowBatteryLocal :
            NotificationType(
                "low_battery",
                R.string.meshtastic_low_battery_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object LowBatteryRemote :
            NotificationType(
                "low_battery_remote",
                R.string.meshtastic_low_battery_temporary_remote_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object Client :
            NotificationType("client_notifications", R.string.client_notification, NotificationManager.IMPORTANCE_HIGH)

        companion object {
            // A list of all types for easy initialization.
            fun allTypes() = listOf(
                ServiceState,
                DirectMessage,
                BroadcastMessage,
                Alert,
                NewNode,
                LowBatteryLocal,
                LowBatteryRemote,
                Client,
            )
        }
    }

    fun clearNotifications() {
        notificationManager.cancelAll()
    }

    /**
     * Creates all necessary notification channels on devices running Android O or newer. This should be called once
     * when the service is created.
     */
    fun initChannels() {
        NotificationType.allTypes().forEach { type -> createNotificationChannel(type) }
    }

    private fun createNotificationChannel(type: NotificationType) {
        if (notificationManager.getNotificationChannel(type.channelId) != null) return

        val channelName = context.getString(type.channelNameRes)
        val channel =
            NotificationChannel(type.channelId, channelName, type.importance).apply {
                lightColor = NOTIFICATION_LIGHT_COLOR
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // Default, can be overridden

                // Type-specific configurations
                when (type) {
                    NotificationType.ServiceState -> {
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    }
                    NotificationType.DirectMessage,
                    NotificationType.BroadcastMessage,
                    NotificationType.NewNode,
                    NotificationType.LowBatteryLocal,
                    NotificationType.LowBatteryRemote,
                    -> {
                        setShowBadge(true)
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        if (type == NotificationType.LowBatteryRemote) enableVibration(true)
                    }
                    NotificationType.Alert -> {
                        setShowBadge(true)
                        enableLights(true)
                        enableVibration(true)
                        setBypassDnd(true)
                        val alertSoundUri =
                            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.alert}".toUri()
                        setSound(
                            alertSoundUri,
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM) // More appropriate for an alert
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                    }
                    NotificationType.Client -> {
                        setShowBadge(true)
                    }
                }
            }
        notificationManager.createNotificationChannel(channel)
    }

    // region Public Notification Methods
    fun updateServiceStateNotification(
        summaryString: String?,
        localStats: LocalStats? = null,
        currentStatsUpdatedAtMillis: Long? = System.currentTimeMillis(),
    ): Notification {
        val notification =
            createServiceStateNotification(
                name = summaryString.orEmpty(),
                message = localStats.formatToString(),
                nextUpdateAt = currentStatsUpdatedAtMillis?.plus(FIFTEEN_MINUTES_IN_MILLIS),
            )
        notificationManager.notify(SERVICE_NOTIFY_ID, notification)
        return notification
    }

    fun updateMessageNotification(contactKey: String, name: String, message: String, isBroadcast: Boolean) {
        val notification = createMessageNotification(contactKey, name, message, isBroadcast)
        // Use a consistent, unique ID for each message conversation.
        notificationManager.notify(contactKey.hashCode(), notification)
    }

    fun showAlertNotification(contactKey: String, name: String, alert: String) {
        val notification = createAlertNotification(contactKey, name, alert)
        // Use a consistent, unique ID for each alert source.
        notificationManager.notify(name.hashCode(), notification)
    }

    fun showNewNodeSeenNotification(node: NodeEntity) {
        val notification = createNewNodeSeenNotification(node.user.shortName, node.user.longName)
        notificationManager.notify(node.num, notification)
    }

    fun showOrUpdateLowBatteryNotification(node: NodeEntity, isRemote: Boolean) {
        val notification = createLowBatteryNotification(node, isRemote)
        notificationManager.notify(node.num, notification)
    }

    fun showClientNotification(clientNotification: MeshProtos.ClientNotification) {
        val notification =
            createClientNotification(context.getString(R.string.client_notification), clientNotification.message)
        notificationManager.notify(clientNotification.toString().hashCode(), notification)
    }

    fun cancelMessageNotification(contactKey: String) = notificationManager.cancel(contactKey.hashCode())

    fun cancelLowBatteryNotification(node: NodeEntity) = notificationManager.cancel(node.num)

    fun clearClientNotification(notification: MeshProtos.ClientNotification) =
        notificationManager.cancel(notification.toString().hashCode())

    // endregion

    // region Notification Creation
    private fun createServiceStateNotification(name: String, message: String?, nextUpdateAt: Long?): Notification {
        val builder =
            commonBuilder(NotificationType.ServiceState)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .setContentTitle(name)
                .setShowWhen(true)

        message?.let {
            builder.setContentText(it)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }

        nextUpdateAt?.let {
            builder.setWhen(it)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
        }

        return builder.build()
    }

    private fun createMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
    ): Notification {
        val type = if (isBroadcast) NotificationType.BroadcastMessage else NotificationType.DirectMessage
        val builder = commonBuilder(type, createOpenMessageIntent(contactKey))

        val person = Person.Builder().setName(name).build()
        val style = NotificationCompat.MessagingStyle(person).addMessage(message, System.currentTimeMillis(), person)

        builder
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setStyle(style)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        // Only add reply action for direct messages, not broadcasts
        if (!isBroadcast) {
            builder.addAction(createReplyAction(contactKey))
        }

        return builder.build()
    }

    private fun createAlertNotification(contactKey: String, name: String, alert: String): Notification {
        val person = Person.Builder().setName(name).build()
        val style = NotificationCompat.MessagingStyle(person).addMessage(alert, System.currentTimeMillis(), person)

        return commonBuilder(NotificationType.Alert, createOpenMessageIntent(contactKey))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setStyle(style)
            .build()
    }

    private fun createNewNodeSeenNotification(name: String, message: String?): Notification {
        val title = context.getString(R.string.new_node_seen).format(name)
        val builder =
            commonBuilder(NotificationType.NewNode)
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)

        message?.let {
            builder.setContentText(it)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }
        return builder.build()
    }

    private fun createLowBatteryNotification(node: NodeEntity, isRemote: Boolean): Notification {
        val type = if (isRemote) NotificationType.LowBatteryRemote else NotificationType.LowBatteryLocal
        val title = context.getString(R.string.low_battery_title).format(node.shortName)
        val message =
            context.getString(R.string.low_battery_message).format(node.longName, node.deviceMetrics.batteryLevel)

        return commonBuilder(type)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(MAX_BATTERY_LEVEL, node.deviceMetrics.batteryLevel, false)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .build()
    }

    private fun createClientNotification(name: String, message: String?): Notification =
        commonBuilder(NotificationType.Client)
            .setCategory(Notification.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentTitle(name)
            .apply {
                message?.let {
                    setContentText(it)
                    setStyle(NotificationCompat.BigTextStyle().bigText(it))
                }
            }
            .build()

    // endregion

    // region Helper/Builder Methods
    private val openAppIntent: PendingIntent by lazy {
        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createOpenMessageIntent(contactKey: String): PendingIntent {
        val deepLinkUri = "$DEEP_LINK_BASE_URI/messages/$contactKey".toUri()
        val deepLinkIntent =
            Intent(Intent.ACTION_VIEW, deepLinkUri, context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(contactKey.hashCode(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    private fun createReplyAction(contactKey: String): NotificationCompat.Action {
        val replyLabel = context.getString(R.string.reply)
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).setLabel(replyLabel).build()

        val replyIntent =
            Intent(context, ReplyReceiver::class.java).apply {
                action = ReplyReceiver.REPLY_ACTION
                putExtra(ReplyReceiver.CONTACT_KEY, contactKey)
            }
        val replyPendingIntent =
            PendingIntent.getBroadcast(
                context,
                contactKey.hashCode(),
                replyIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, replyLabel, replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build()
    }

    private fun commonBuilder(
        type: NotificationType,
        contentIntent: PendingIntent? = null,
    ): NotificationCompat.Builder {
        val smallIcon = R.drawable.app_icon

        return NotificationCompat.Builder(context, type.channelId)
            .setSmallIcon(smallIcon)
            .setColor(NOTIFICATION_LIGHT_COLOR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent ?: openAppIntent)
    }
    // endregion
}

// Extension function to format LocalStats into a readable string.
private fun LocalStats?.formatToString(): String {
    if (this == null) return "No Local Stats"

    return this.allFields
        .mapNotNull { (k, v) ->
            when (k.name) {
                "num_online_nodes",
                "num_total_nodes",
                -> null // Exclude these fields
                "uptime_seconds" -> "Uptime: ${formatUptime(v as Int)}"
                "channel_utilization" -> "ChUtil: %.2f%%".format(v)
                "air_util_tx" -> "AirUtilTX: %.2f%%".format(v)
                else -> {
                    val formattedKey = k.name.replace('_', ' ').replaceFirstChar { it.titlecase() }
                    "$formattedKey: $v"
                }
            }
        }
        .joinToString("\n")
}
