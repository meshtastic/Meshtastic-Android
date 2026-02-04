/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import android.content.ContentResolver.SCHEME_ANDROID_RESOURCE
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.getSystemService
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R.raw
import com.geeksville.mesh.service.MarkAsReadReceiver.Companion.MARK_AS_READ_ACTION
import com.geeksville.mesh.service.ReactionReceiver.Companion.REACT_ACTION
import com.geeksville.mesh.service.ReplyReceiver.Companion.KEY_TEXT_REPLY
import com.meshtastic.core.strings.getString
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.model.Message
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.SERVICE_NOTIFY_ID
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.client_notification
import org.meshtastic.core.strings.low_battery_message
import org.meshtastic.core.strings.low_battery_title
import org.meshtastic.core.strings.mark_as_read
import org.meshtastic.core.strings.meshtastic_alerts_notifications
import org.meshtastic.core.strings.meshtastic_app_name
import org.meshtastic.core.strings.meshtastic_broadcast_notifications
import org.meshtastic.core.strings.meshtastic_low_battery_notifications
import org.meshtastic.core.strings.meshtastic_low_battery_temporary_remote_notifications
import org.meshtastic.core.strings.meshtastic_messages_notifications
import org.meshtastic.core.strings.meshtastic_new_nodes_notifications
import org.meshtastic.core.strings.meshtastic_service_notifications
import org.meshtastic.core.strings.meshtastic_waypoints_notifications
import org.meshtastic.core.strings.new_node_seen
import org.meshtastic.core.strings.no_local_stats
import org.meshtastic.core.strings.reply
import org.meshtastic.core.strings.you
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.Telemetry
import javax.inject.Inject

/**
 * Manages the creation and display of all app notifications.
 *
 * This class centralizes notification logic, including channel creation, builder configuration, and displaying
 * notifications for various events like new messages, alerts, and service status changes.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class MeshServiceNotificationsImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val packetRepository: Lazy<PacketRepository>,
    private val nodeRepository: Lazy<NodeRepository>,
) : MeshServiceNotifications {

    private val notificationManager = context.getSystemService<NotificationManager>()!!

    companion object {
        private const val FIFTEEN_MINUTES_IN_MILLIS = 15L * 60 * 1000
        const val MAX_BATTERY_LEVEL = 100
        private val NOTIFICATION_LIGHT_COLOR = Color.BLUE
        private const val MAX_HISTORY_MESSAGES = 10
        private const val MIN_CONTEXT_MESSAGES = 3
        private const val SNIPPET_LENGTH = 30
        private const val GROUP_KEY_MESSAGES = "com.geeksville.mesh.GROUP_MESSAGES"
        private const val SUMMARY_ID = 1
        private const val PERSON_ICON_SIZE = 128
        private const val PERSON_ICON_TEXT_SIZE_RATIO = 0.5f
    }

    /**
     * Sealed class to define the properties of each notification channel. This centralizes channel configuration and
     * makes it type-safe.
     */
    private sealed class NotificationType(
        val channelId: String,
        val channelNameRes: StringResource,
        val importance: Int,
    ) {
        object ServiceState :
            NotificationType(
                "my_service",
                Res.string.meshtastic_service_notifications,
                NotificationManager.IMPORTANCE_MIN,
            )

        object DirectMessage :
            NotificationType(
                "my_messages",
                Res.string.meshtastic_messages_notifications,
                NotificationManager.IMPORTANCE_HIGH,
            )

        object BroadcastMessage :
            NotificationType(
                "my_broadcasts",
                Res.string.meshtastic_broadcast_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object Waypoint :
            NotificationType(
                "my_waypoints",
                Res.string.meshtastic_waypoints_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object Alert :
            NotificationType(
                "my_alerts",
                Res.string.meshtastic_alerts_notifications,
                NotificationManager.IMPORTANCE_HIGH,
            )

        object NewNode :
            NotificationType(
                "new_nodes",
                Res.string.meshtastic_new_nodes_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object LowBatteryLocal :
            NotificationType(
                "low_battery",
                Res.string.meshtastic_low_battery_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object LowBatteryRemote :
            NotificationType(
                "low_battery_remote",
                Res.string.meshtastic_low_battery_temporary_remote_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object Client :
            NotificationType(
                "client_notifications",
                Res.string.client_notification,
                NotificationManager.IMPORTANCE_HIGH,
            )

        companion object {
            // A list of all types for easy initialization.
            fun allTypes() = listOf(
                ServiceState,
                DirectMessage,
                BroadcastMessage,
                Waypoint,
                Alert,
                NewNode,
                LowBatteryLocal,
                LowBatteryRemote,
                Client,
            )
        }
    }

    override fun clearNotifications() {
        notificationManager.cancelAll()
    }

    /**
     * Creates all necessary notification channels on devices running Android O or newer. This should be called once
     * when the service is created.
     */
    override fun initChannels() {
        NotificationType.allTypes().forEach { type -> createNotificationChannel(type) }
    }

    private fun createNotificationChannel(type: NotificationType) {
        if (notificationManager.getNotificationChannel(type.channelId) != null) return

        val channelName = getString(type.channelNameRes)
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
                    NotificationType.Waypoint,
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
                        val alertSoundUri = "${SCHEME_ANDROID_RESOURCE}://${context.packageName}/${raw.alert}".toUri()
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

    var cachedTelemetry: Telemetry? = null
    var cachedLocalStats: LocalStats? = null
    var nextStatsUpdateMillis: Long = 0
    var cachedMessage: String? = null

    // region Public Notification Methods
    override fun updateServiceStateNotification(summaryString: String?, telemetry: Telemetry?): Notification {
        val hasLocalStats = telemetry?.local_stats != null
        val hasDeviceMetrics = telemetry?.device_metrics != null
        val message =
            when {
                hasLocalStats -> {
                    val localStatsMessage = telemetry?.local_stats?.formatToString()
                    cachedTelemetry = telemetry
                    nextStatsUpdateMillis = System.currentTimeMillis() + FIFTEEN_MINUTES_IN_MILLIS
                    localStatsMessage
                }
                cachedTelemetry == null && hasDeviceMetrics -> {
                    val deviceMetricsMessage = telemetry?.device_metrics?.formatToString()
                    if (cachedLocalStats == null) {
                        cachedTelemetry = telemetry
                    }
                    nextStatsUpdateMillis = System.currentTimeMillis()
                    deviceMetricsMessage
                }
                else -> null
            }

        cachedMessage = message ?: cachedMessage ?: getString(Res.string.no_local_stats)

        val notification =
            createServiceStateNotification(
                name = summaryString.orEmpty(),
                message = cachedMessage,
                nextUpdateAt = nextStatsUpdateMillis,
            )
        notificationManager.notify(SERVICE_NOTIFY_ID, notification)
        return notification
    }

    override suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {
        showConversationNotification(contactKey, isBroadcast, channelName, isSilent = isSilent)
    }

    override suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {
        showConversationNotification(contactKey, isBroadcast, channelName, isSilent = isSilent)
    }

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ) {
        val notification = createWaypointNotification(name, message, waypointId, isSilent)
        notificationManager.notify(contactKey.hashCode(), notification)
    }

    private suspend fun showConversationNotification(
        contactKey: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean = false,
    ) {
        val ourNode = nodeRepository.get().ourNodeInfo.value
        val history =
            packetRepository
                .get()
                .getMessagesFrom(contactKey, includeFiltered = false) { nodeId ->
                    if (nodeId == DataPacket.ID_LOCAL) {
                        ourNode ?: nodeRepository.get().getNode(nodeId)
                    } else {
                        nodeRepository.get().getNode(nodeId ?: "")
                    }
                }
                .first()

        val unread = history.filter { !it.read }
        val displayHistory =
            if (unread.size < MIN_CONTEXT_MESSAGES) {
                history.take(MIN_CONTEXT_MESSAGES).reversed()
            } else {
                unread.take(MAX_HISTORY_MESSAGES).reversed()
            }

        if (displayHistory.isEmpty()) return

        val notification =
            createConversationNotification(
                contactKey = contactKey,
                isBroadcast = isBroadcast,
                channelName = channelName,
                history = displayHistory,
                isSilent = isSilent,
            )
        notificationManager.notify(contactKey.hashCode(), notification)
        showGroupSummary()
    }

    private fun showGroupSummary() {
        val activeNotifications =
            notificationManager.activeNotifications.filter {
                it.id != SUMMARY_ID && it.notification.group == GROUP_KEY_MESSAGES
            }

        val ourNode = nodeRepository.get().ourNodeInfo.value
        val meName = ourNode?.user?.long_name ?: getString(Res.string.you)
        val me =
            Person.Builder()
                .setName(meName)
                .setKey(ourNode?.user?.id ?: DataPacket.ID_LOCAL)
                .apply { ourNode?.let { setIcon(createPersonIcon(meName, it.colors.second, it.colors.first)) } }
                .build()

        val messagingStyle =
            NotificationCompat.MessagingStyle(me)
                .setGroupConversation(true)
                .setConversationTitle(getString(Res.string.meshtastic_app_name))

        activeNotifications.forEach { sbn ->
            val senderTitle = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
            val messageText = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)
            val postTime = sbn.postTime

            if (senderTitle != null && messageText != null) {
                // For the summary, we're creating a generic Person for the sender from the active notification's title.
                // We don't have the original Person object or its colors/ID, so we're just using the name.
                val senderPerson = Person.Builder().setName(senderTitle).build()
                messagingStyle.addMessage(messageText, postTime, senderPerson)
            }
        }

        val summaryNotification =
            commonBuilder(NotificationType.DirectMessage)
                .setSmallIcon(com.geeksville.mesh.R.drawable.app_icon)
                .setStyle(messagingStyle)
                .setGroup(GROUP_KEY_MESSAGES)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

        notificationManager.notify(SUMMARY_ID, summaryNotification)
    }

    override fun showAlertNotification(contactKey: String, name: String, alert: String) {
        val notification = createAlertNotification(contactKey, name, alert)
        // Use a consistent, unique ID for each alert source.
        notificationManager.notify(name.hashCode(), notification)
    }

    override fun showNewNodeSeenNotification(node: NodeEntity) {
        val notification = createNewNodeSeenNotification(node.user.short_name, node.user.long_name)
        notificationManager.notify(node.num, notification)
    }

    override fun showOrUpdateLowBatteryNotification(node: NodeEntity, isRemote: Boolean) {
        val notification = createLowBatteryNotification(node, isRemote)
        notificationManager.notify(node.num, notification)
    }

    override fun showClientNotification(clientNotification: ClientNotification) {
        val notification =
            createClientNotification(getString(Res.string.client_notification), clientNotification.message)
        notificationManager.notify(clientNotification.toString().hashCode(), notification)
    }

    override fun cancelMessageNotification(contactKey: String) = notificationManager.cancel(contactKey.hashCode())

    override fun cancelLowBatteryNotification(node: NodeEntity) = notificationManager.cancel(node.num)

    override fun clearClientNotification(notification: ClientNotification) =
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

        nextUpdateAt
            ?.takeIf { it > System.currentTimeMillis() }
            ?.let {
                builder.setWhen(it)
                builder.setUsesChronometer(true)
                builder.setChronometerCountDown(true)
            }

        return builder.build()
    }

    @Suppress("LongMethod")
    private fun createConversationNotification(
        contactKey: String,
        isBroadcast: Boolean,
        channelName: String?,
        history: List<Message>,
        isSilent: Boolean = false,
    ): Notification {
        val type = if (isBroadcast) NotificationType.BroadcastMessage else NotificationType.DirectMessage
        val builder = commonBuilder(type, createOpenMessageIntent(contactKey))

        if (isSilent) {
            builder.setSilent(true)
        }

        val ourNode = nodeRepository.get().ourNodeInfo.value
        val meName = ourNode?.user?.long_name ?: getString(Res.string.you)
        val me =
            Person.Builder()
                .setName(meName)
                .setKey(ourNode?.user?.id ?: DataPacket.ID_LOCAL)
                .apply { ourNode?.let { setIcon(createPersonIcon(meName, it.colors.second, it.colors.first)) } }
                .build()

        val style =
            NotificationCompat.MessagingStyle(me)
                .setGroupConversation(channelName != null)
                .setConversationTitle(channelName)

        history.forEach { msg ->
            // Use the node attached to the message directly to ensure correct identification
            val person =
                Person.Builder()
                    .setName(msg.node.user.long_name)
                    .setKey(msg.node.user.id)
                    .setIcon(createPersonIcon(msg.node.user.short_name, msg.node.colors.second, msg.node.colors.first))
                    .build()

            val text =
                msg.originalMessage?.let { original ->
                    "↩️ \"${original.node.user.short_name}: ${original.text.take(SNIPPET_LENGTH)}...\": ${msg.text}"
                } ?: msg.text

            style.addMessage(text, msg.receivedTime, person)

            // Add reactions as separate "messages" in history if they exist
            msg.emojis.forEach { reaction ->
                val reactorNode = nodeRepository.get().getNode(reaction.user.id)
                val reactor =
                    Person.Builder()
                        .setName(reaction.user.long_name)
                        .setKey(reaction.user.id)
                        .setIcon(
                            createPersonIcon(
                                reaction.user.short_name,
                                reactorNode.colors.second,
                                reactorNode.colors.first,
                            ),
                        )
                        .build()
                style.addMessage(
                    "${reaction.emoji} to \"${msg.text.take(SNIPPET_LENGTH)}...\"",
                    reaction.timestamp,
                    reactor,
                )
            }
        }
        val lastMessage = history.last()

        builder
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setStyle(style)
            .setGroup(GROUP_KEY_MESSAGES)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(lastMessage.receivedTime)
            .setShowWhen(true)
            .addAction(createReplyAction(contactKey))
            .addAction(createMarkAsReadAction(contactKey))
            .addAction(
                createReactionAction(
                    contactKey = contactKey,
                    packetId = lastMessage.packetId,
                    toId = lastMessage.node.user.id,
                    channelIndex = lastMessage.node.channel,
                ),
            )

        return builder.build()
    }

    private fun createWaypointNotification(
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ): Notification {
        val person = Person.Builder().setName(name).build()
        val style = NotificationCompat.MessagingStyle(person).addMessage(message, System.currentTimeMillis(), person)

        val builder =
            commonBuilder(NotificationType.Waypoint, createOpenWaypointIntent(waypointId))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setStyle(style)
                .setGroup(GROUP_KEY_MESSAGES)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)

        if (isSilent) {
            builder.setSilent(true)
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

    private fun createNewNodeSeenNotification(name: String, message: String): Notification {
        val title = getString(Res.string.new_node_seen).format(name)
        val builder =
            commonBuilder(NotificationType.NewNode)
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        return builder.build()
    }

    private fun createLowBatteryNotification(node: NodeEntity, isRemote: Boolean): Notification {
        val type = if (isRemote) NotificationType.LowBatteryRemote else NotificationType.LowBatteryLocal
        val title = getString(Res.string.low_battery_title).format(node.shortName)
        val batteryLevel = node.deviceTelemetry?.device_metrics?.battery_level ?: 0
        val message = getString(Res.string.low_battery_message).format(node.longName, batteryLevel)

        return commonBuilder(type)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(MAX_BATTERY_LEVEL, batteryLevel, false)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .build()
    }

    private fun createClientNotification(name: String, message: String): Notification =
        commonBuilder(NotificationType.Client)
            .setCategory(Notification.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentTitle(name)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
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

    private fun createOpenWaypointIntent(waypointId: Int): PendingIntent {
        val deepLinkUri = "$DEEP_LINK_BASE_URI/map?waypointId=$waypointId".toUri()
        val deepLinkIntent =
            Intent(Intent.ACTION_VIEW, deepLinkUri, context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(waypointId, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    private fun createReplyAction(contactKey: String): NotificationCompat.Action {
        val replyLabel = getString(Res.string.reply)
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

    private fun createMarkAsReadAction(contactKey: String): NotificationCompat.Action {
        val label = getString(Res.string.mark_as_read)
        val intent =
            Intent(context, MarkAsReadReceiver::class.java).apply {
                action = MARK_AS_READ_ACTION
                putExtra(MarkAsReadReceiver.CONTACT_KEY, contactKey)
            }
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                contactKey.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Action.Builder(android.R.drawable.ic_menu_view, label, pendingIntent).build()
    }

    private fun createReactionAction(
        contactKey: String,
        packetId: Int,
        toId: String,
        channelIndex: Int,
    ): NotificationCompat.Action {
        val label = "👍"
        val intent =
            Intent(context, ReactionReceiver::class.java).apply {
                action = REACT_ACTION
                putExtra(ReactionReceiver.EXTRA_CONTACT_KEY, contactKey)
                putExtra(ReactionReceiver.EXTRA_PACKET_ID, packetId)
                putExtra(ReactionReceiver.EXTRA_TO_ID, toId)
                putExtra(ReactionReceiver.EXTRA_CHANNEL_INDEX, channelIndex)
                putExtra(ReactionReceiver.EXTRA_EMOJI, "👍")
            }
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                packetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Action.Builder(android.R.drawable.ic_menu_add, label, pendingIntent).build()
    }

    private fun commonBuilder(
        type: NotificationType,
        contentIntent: PendingIntent? = null,
    ): NotificationCompat.Builder {
        val smallIcon = com.geeksville.mesh.R.drawable.app_icon

        return NotificationCompat.Builder(context, type.channelId)
            .setSmallIcon(smallIcon)
            .setColor(NOTIFICATION_LIGHT_COLOR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent ?: openAppIntent)
    }

    private fun createPersonIcon(name: String, backgroundColor: Int, foregroundColor: Int): IconCompat {
        val bitmap = createBitmap(PERSON_ICON_SIZE, PERSON_ICON_SIZE)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw background circle
        paint.color = backgroundColor
        canvas.drawCircle(PERSON_ICON_SIZE / 2f, PERSON_ICON_SIZE / 2f, PERSON_ICON_SIZE / 2f, paint)

        // Draw initials
        paint.color = foregroundColor
        paint.textSize = PERSON_ICON_SIZE * PERSON_ICON_TEXT_SIZE_RATIO
        paint.textAlign = Paint.Align.CENTER
        val initial =
            if (name.isNotEmpty()) {
                val codePoint = name.codePointAt(0)
                String(Character.toChars(codePoint)).uppercase()
            } else {
                "?"
            }
        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(initial, xPos, yPos, paint)

        return IconCompat.createWithBitmap(bitmap)
    }
    // endregion
}

// Extension function to format LocalStats into a readable string.
private fun LocalStats.formatToString(): String {
    val parts = mutableListOf<String>()
    parts.add("Uptime: ${formatUptime(uptime_seconds)}")
    parts.add("ChUtil: %.2f%%".format(channel_utilization))
    parts.add("AirUtilTX: %.2f%%".format(air_util_tx))
    return parts.joinToString("\n")
}

private fun DeviceMetrics.formatToString(): String {
    val parts = mutableListOf<String>()
    battery_level?.let { parts.add("Battery Level: $it") }
    uptime_seconds?.let { parts.add("Uptime: ${formatUptime(it)}") }
    channel_utilization?.let { parts.add("ChUtil: %.2f%%".format(it)) }
    air_util_tx?.let { parts.add("AirUtilTX: %.2f%%".format(it)) }
    return parts.joinToString("\n")
}
