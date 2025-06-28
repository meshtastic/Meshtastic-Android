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
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.net.toUri
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.LocalStats
import com.geeksville.mesh.android.notificationManager
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.navigation.DEEP_LINK_BASE_URI
import com.geeksville.mesh.service.ReplyReceiver.Companion.KEY_TEXT_REPLY
import com.geeksville.mesh.util.formatUptime

@Suppress("TooManyFunctions")
class MeshServiceNotifications(
    private val context: Context
) {

    val notificationLightColor = Color.BLUE

    companion object {
        private const val FIFTEEN_MINUTES_IN_MILLIS = 15L * 60 * 1000
        const val MAX_BATTERY_LEVEL = 100
    }

    private val notificationManager: NotificationManager get() = context.notificationManager

    // We have two notification channels: one for general service status and another one for messages
    val notifyId = 101

    fun clearNotifications() {
        notificationManager.cancelAll()
    }

    fun initChannels() {
        // create notification channels on service creation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            createMessageNotificationChannel()
            createBroadcastNotificationChannel()
            createAlertNotificationChannel()
            createNewNodeNotificationChannel()
            createLowBatteryNotificationChannel()
            createLowBatteryRemoteNotificationChannel()
            createClientNotificationChannel()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "my_service"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channelName = context.getString(R.string.meshtastic_service_notifications)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                lightColor = notificationLightColor
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createMessageNotificationChannel(): String {
        val channelId = "my_messages"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channelName = context.getString(R.string.meshtastic_messages_notifications)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lightColor = notificationLightColor
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createBroadcastNotificationChannel(): String {
        val channelId = "my_broadcasts"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channelName = context.getString(R.string.meshtastic_broadcast_notifications)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                lightColor = notificationLightColor
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createAlertNotificationChannel(): String {
        val channelId = "my_alerts"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channelName = context.getString(R.string.meshtastic_alerts_notifications)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
                lightColor = notificationLightColor
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                val alertSoundUri =
                    (
                            ContentResolver.SCHEME_ANDROID_RESOURCE +
                                    "://" + context.applicationContext.packageName +
                                    "/" + R.raw.alert
                            ).toUri()
                setSound(
                    alertSoundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNewNodeNotificationChannel(): String {
        val channelId = "new_nodes"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channelName = context.getString(R.string.meshtastic_new_nodes_notifications)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lightColor = notificationLightColor
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createLowBatteryNotificationChannel(): String {
        val channelId = "low_battery"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channelName = context.getString(R.string.meshtastic_low_battery_notifications)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lightColor = notificationLightColor
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    // FIXME, Once we get a dedicated settings page in the app, this function should be removed and
    //     the feature should be implemented in the regular low battery notification stuff
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createLowBatteryRemoteNotificationChannel(): String {
        val channelId = "low_battery_remote"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channelName =
                context.getString(R.string.meshtastic_low_battery_temporary_remote_notifications)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lightColor = notificationLightColor
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                setShowBadge(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createClientNotificationChannel(): String {
        val channelId = "client_notifications"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channelName = context.getString(R.string.client_notification)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lightColor = notificationLightColor
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        return channelId
    }

    private val channelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            ""
        }
    }

    private val messageChannelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createMessageNotificationChannel()
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            ""
        }
    }

    private val broadcastChannelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createBroadcastNotificationChannel()
        } else {
            ""
        }
    }

    private val alertChannelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createAlertNotificationChannel()
        } else {
            ""
        }
    }

    private val newNodeChannelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNewNodeNotificationChannel()
        } else {
            ""
        }
    }

    private val lowBatteryChannelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createLowBatteryNotificationChannel()
        } else {
            ""
        }
    }

    // FIXME, Once we get a dedicated settings page in the app, this function should be removed and
    //     the feature should be implemented in the regular low battery notification stuff
    private val lowBatteryRemoteChannelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createLowBatteryRemoteNotificationChannel()
        } else {
            ""
        }
    }

    private val clientNotificationChannelId: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createClientNotificationChannel()
        } else {
            ""
        }
    }

    private fun LocalStats?.formatToString(): String = this?.allFields?.mapNotNull { (k, v) ->
        when (k.name) {
            "num_online_nodes", "num_total_nodes" -> return@mapNotNull null
            "uptime_seconds" -> "Uptime: ${formatUptime(v as Int)}"
            "channel_utilization" -> "ChUtil: %.2f%%".format(v)
            "air_util_tx" -> "AirUtilTX: %.2f%%".format(v)
            else ->
                "${
                    k.name.replace('_', ' ').split(" ")
                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                }: $v"
        }
    }?.joinToString("\n") ?: "No Local Stats"

    fun updateServiceStateNotification(
        summaryString: String? = null,
        localStats: LocalStats? = null,
        currentStatsUpdatedAtMillis: Long? = null,
    ) {
        notificationManager.notify(
            notifyId,
            createServiceStateNotification(
                name = summaryString.orEmpty(),
                message = localStats.formatToString(),
                nextUpdateAt = currentStatsUpdatedAtMillis?.plus(FIFTEEN_MINUTES_IN_MILLIS)
            )
        )
    }

    fun cancelMessageNotification(contactKey: String) {
        notificationManager.cancel(contactKey.hashCode())
    }

    fun updateMessageNotification(contactKey: String, name: String, message: String, isBroadcast: Boolean) =
        notificationManager.notify(
            contactKey.hashCode(), // show unique notifications,
            createMessageNotification(contactKey, name, message, isBroadcast)
        )

    fun showAlertNotification(contactKey: String, name: String, alert: String) {
        notificationManager.notify(
            name.hashCode(), // show unique notifications,
            createAlertNotification(contactKey, name, alert)
        )
    }

    fun showNewNodeSeenNotification(node: NodeEntity) {
        notificationManager.notify(
            node.num, // show unique notifications
            createNewNodeSeenNotification(node.user.shortName, node.user.longName)
        )
    }

    fun showOrUpdateLowBatteryNotification(node: NodeEntity, isRemote: Boolean) {
        notificationManager.notify(
            node.num, // show unique notifications
            createLowBatteryNotification(node, isRemote)
        )
    }

    fun cancelLowBatteryNotification(node: NodeEntity) {
        notificationManager.cancel(node.num)
    }

    fun showClientNotification(notification: MeshProtos.ClientNotification) {
        notificationManager.notify(
            notification.toString().hashCode(), // show unique notifications
            createClientNotification(context.getString(R.string.client_notification), notification.message)
        )
    }
    fun clearClientNotification(notification: MeshProtos.ClientNotification) {
        notificationManager.cancel(notification.toString().hashCode())
    }

    private val openAppIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createMessageReplyIntent(contactKey: String): Intent {
        return Intent(context, ReplyReceiver::class.java).apply {
            action = ReplyReceiver.REPLY_ACTION
            putExtra(ReplyReceiver.CONTACT_KEY, contactKey)
        }
    }

    private fun createOpenMessageIntent(contactKey: String): PendingIntent {
        val intentFlags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        val deepLink = "$DEEP_LINK_BASE_URI/messages/$contactKey"
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            deepLink.toUri(),
            context,
            MainActivity::class.java
        ).apply {
            flags = intentFlags
        }

        val deepLinkPendingIntent: PendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(0, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return deepLinkPendingIntent
    }

    private fun commonBuilder(
        channel: String,
        contentIntent: PendingIntent? = null
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, channel)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent ?: openAppIntent)

        builder.setSmallIcon(
            // vector form icons don't work reliably on older androids
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                R.drawable.app_icon_novect
            } else {
                R.drawable.app_icon
            }
        )
        return builder
    }

    lateinit var serviceNotificationBuilder: NotificationCompat.Builder
    fun createServiceStateNotification(
        name: String,
        message: String? = null,
        nextUpdateAt: Long? = null
    ): Notification {
        if (!::serviceNotificationBuilder.isInitialized) {
            serviceNotificationBuilder = commonBuilder(channelId)
        }
        with(serviceNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_MIN
            setCategory(Notification.CATEGORY_SERVICE)
            setOngoing(true)
            setContentTitle(name)
            message?.let {
                setContentText(it)
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message),
                )
            }
            nextUpdateAt?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setWhen(it)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                }
            } ?: {
                setWhen(System.currentTimeMillis())
            }
            setShowWhen(true)
        }
        return serviceNotificationBuilder.build()
    }

    private fun createMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
    ): Notification {
        val channelId = if (isBroadcast) broadcastChannelId else messageChannelId
        val messageNotificationBuilder: NotificationCompat.Builder =
            commonBuilder(channelId, createOpenMessageIntent(contactKey))

        val person = Person.Builder().setName(name).build()
        // Key for the string that's delivered in the action's intent.
        val replyLabel: String = context.getString(R.string.reply)
        val remoteInput: RemoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).run {
            setLabel(replyLabel)
            build()
        }

        // Build a PendingIntent for the reply action to trigger.
        val replyPendingIntent: PendingIntent =
            PendingIntent.getBroadcast(
                context,
                contactKey.hashCode(),
                createMessageReplyIntent(contactKey),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        // Create the reply action and add the remote input.
        val action: NotificationCompat.Action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            replyLabel,
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        with(messageNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_DEFAULT
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setStyle(
                NotificationCompat.MessagingStyle(person)
                    .addMessage(message, System.currentTimeMillis(), person)
            )
            addAction(action)
            setWhen(System.currentTimeMillis())
            setShowWhen(true)
        }
        return messageNotificationBuilder.build()
    }

    lateinit var alertNotificationBuilder: NotificationCompat.Builder
    private fun createAlertNotification(
        contactKey: String,
        name: String,
        alert: String
    ): Notification {
        if (!::alertNotificationBuilder.isInitialized) {
            alertNotificationBuilder =
                commonBuilder(alertChannelId, createOpenMessageIntent(contactKey))
        }
        val person = Person.Builder().setName(name).build()
        with(alertNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_HIGH
            setCategory(Notification.CATEGORY_ALARM)
            setAutoCancel(true)
            setStyle(
                NotificationCompat.MessagingStyle(person)
                    .addMessage(alert, System.currentTimeMillis(), person)
            )
        }
        return alertNotificationBuilder.build()
    }

    lateinit var newNodeSeenNotificationBuilder: NotificationCompat.Builder
    private fun createNewNodeSeenNotification(name: String, message: String? = null): Notification {
        if (!::newNodeSeenNotificationBuilder.isInitialized) {
            newNodeSeenNotificationBuilder = commonBuilder(newNodeChannelId)
        }
        with(newNodeSeenNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_DEFAULT
            setCategory(Notification.CATEGORY_STATUS)
            setAutoCancel(true)
            setContentTitle("New Node Seen: $name")
            message?.let {
                setContentText(it)
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message),
                )
            }
            setWhen(System.currentTimeMillis())
            setShowWhen(true)
        }
        return newNodeSeenNotificationBuilder.build()
    }

    lateinit var lowBatteryRemoteNotificationBuilder: NotificationCompat.Builder
    lateinit var lowBatteryNotificationBuilder: NotificationCompat.Builder
    private fun createLowBatteryNotification(node: NodeEntity, isRemote: Boolean): Notification {
        val tempNotificationBuilder: NotificationCompat.Builder = if (isRemote) {
            if (!::lowBatteryRemoteNotificationBuilder.isInitialized) {
                lowBatteryRemoteNotificationBuilder = commonBuilder(lowBatteryChannelId)
            }
            lowBatteryRemoteNotificationBuilder
        } else {
            if (!::lowBatteryNotificationBuilder.isInitialized) {
                lowBatteryNotificationBuilder = commonBuilder(lowBatteryRemoteChannelId)
            }
            lowBatteryNotificationBuilder
        }
        with(tempNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_DEFAULT
            setCategory(Notification.CATEGORY_STATUS)
            setOngoing(true)
            setShowWhen(true)
            setOnlyAlertOnce(true)
            setWhen(System.currentTimeMillis())
            setProgress(MAX_BATTERY_LEVEL, node.deviceMetrics.batteryLevel, false)
            setContentTitle(
                context.getString(R.string.low_battery_title).format(
                    node.shortName
                )
            )
            val message = context.getString(R.string.low_battery_message).format(
                node.longName,
                node.deviceMetrics.batteryLevel
            )
            message.let {
                setContentText(it)
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(it),
                )
            }
        }
        if (isRemote) {
            lowBatteryRemoteNotificationBuilder = tempNotificationBuilder
            return lowBatteryRemoteNotificationBuilder.build()
        } else {
            lowBatteryNotificationBuilder = tempNotificationBuilder
            return lowBatteryNotificationBuilder.build()
        }
    }

    lateinit var clientNotificationBuilder: NotificationCompat.Builder

    private fun createClientNotification(name: String, message: String? = null): Notification {
        if (!::clientNotificationBuilder.isInitialized) {
            clientNotificationBuilder = commonBuilder(clientNotificationChannelId)
        }
        with(clientNotificationBuilder) {
            priority = NotificationCompat.PRIORITY_DEFAULT
            setCategory(Notification.CATEGORY_ERROR)
            setAutoCancel(true)
            setContentTitle(name)
            message?.let {
                setContentText(it)
                setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message),
                )
            }
        }
        return clientNotificationBuilder.build()
    }
}
