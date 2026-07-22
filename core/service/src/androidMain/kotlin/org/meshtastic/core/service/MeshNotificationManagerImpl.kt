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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.ContentResolver.SCHEME_ANDROID_RESOURCE
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.SERVICE_NOTIFY_ID
import org.meshtastic.core.resources.R.drawable
import org.meshtastic.core.resources.R.raw
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channel
import org.meshtastic.core.resources.client_notification
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.local_stats_bad
import org.meshtastic.core.resources.local_stats_battery
import org.meshtastic.core.resources.local_stats_diagnostics_prefix
import org.meshtastic.core.resources.local_stats_dropped
import org.meshtastic.core.resources.local_stats_heap
import org.meshtastic.core.resources.local_stats_heap_value
import org.meshtastic.core.resources.local_stats_nodes
import org.meshtastic.core.resources.local_stats_noise
import org.meshtastic.core.resources.local_stats_relays
import org.meshtastic.core.resources.local_stats_traffic
import org.meshtastic.core.resources.local_stats_uptime
import org.meshtastic.core.resources.local_stats_utilization
import org.meshtastic.core.resources.low_battery_message
import org.meshtastic.core.resources.low_battery_title
import org.meshtastic.core.resources.mark_as_read
import org.meshtastic.core.resources.meshtastic_alerts_notifications
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.resources.meshtastic_broadcast_notifications
import org.meshtastic.core.resources.meshtastic_low_battery_notifications
import org.meshtastic.core.resources.meshtastic_low_battery_temporary_remote_notifications
import org.meshtastic.core.resources.meshtastic_messages_notifications
import org.meshtastic.core.resources.meshtastic_new_nodes_notifications
import org.meshtastic.core.resources.meshtastic_service_notifications
import org.meshtastic.core.resources.meshtastic_waypoints_notifications
import org.meshtastic.core.resources.new_node_seen
import org.meshtastic.core.resources.no_local_stats
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.reply
import org.meshtastic.core.resources.unknown_username
import org.meshtastic.core.resources.you
import org.meshtastic.core.service.MarkAsReadReceiver.Companion.MARK_AS_READ_ACTION
import org.meshtastic.core.service.ReactionReceiver.Companion.REACT_ACTION
import org.meshtastic.core.service.ReplyReceiver.Companion.KEY_TEXT_REPLY
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.Telemetry
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

/**
 * Manages the creation and display of all app notifications.
 *
 * This class centralizes notification logic, including channel creation, builder configuration, and displaying
 * notifications for various events like new messages, alerts, and service status changes.
 */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
@Single
class MeshNotificationManagerImpl(
    private val context: Context,
    private val packetRepository: Lazy<PacketRepository>,
    private val nodeRepository: Lazy<NodeRepository>,
    private val conversationShortcutPublisher: Lazy<ConversationShortcutPublisher>,
    private val radioConfigRepository: Lazy<RadioConfigRepository>,
) : MeshNotificationManager {

    private val notificationManager =
        checkNotNull(context.getSystemService<NotificationManager>()) { "NotificationManager not found" }

    companion object {
        const val MAX_BATTERY_LEVEL = 100

        // Meshtastic brand accent (Green 500, see .skills/design-standards) — used as the notification accent color
        // (small-icon tint) and the notification LED color.
        private val NOTIFICATION_COLOR = 0xFF67EA94.toInt()
        private const val MAX_HISTORY_MESSAGES = 10
        private const val MIN_CONTEXT_MESSAGES = 3
        private const val SNIPPET_LENGTH = 30
        private const val GROUP_KEY_MESSAGES = "org.meshtastic.app.GROUP_MESSAGES"
        private const val SUMMARY_ID = 1
        private const val STATS_UPDATE_MINUTES = 15
        private val STATS_UPDATE_INTERVAL = STATS_UPDATE_MINUTES.minutes
        private const val BULLET = "• "

        // Notification tags namespace the numeric IDs per notification type. Without them every type shares one
        // integer ID space, so unrelated notifications can clobber each other (e.g. a node whose num == 101 would
        // overwrite the foreground-service notification, or num == 1 the group summary). notify()/cancel() must use
        // the same (tag, id) pair.
        private const val TAG_MESSAGE = "message"
        private const val TAG_MESSAGE_SUMMARY = "message_summary"
        private const val TAG_WAYPOINT = "waypoint"
        private const val TAG_ALERT = "alert"
        private const val TAG_NEW_NODE = "new_node"
        private const val TAG_LOW_BATTERY = "low_battery"
        private const val TAG_CLIENT = "client"
    }

    /**
     * Caches generated avatar icons keyed by (person id + short name + colors) so a conversation rebuild does not
     * re-rasterize an avatar bitmap for every message on every update. Bounded by the number of distinct nodes seen;
     * entries are cheap and colors/names rarely change.
     */
    private val personIconCache = ConcurrentHashMap<String, IconCompat>()

    /** Circular, node-colored avatar holding the sender's full short name (e.g. "2c3d"), not just its first letter. */
    private fun cachedPersonIcon(key: String, shortName: String, backgroundColor: Int, foregroundColor: Int) =
        personIconCache.getOrPut("$key|$shortName|$backgroundColor|$foregroundColor") {
            PersonIconFactory.createLabel(shortName, backgroundColor, foregroundColor, rounded = false)
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
                NotificationChannels.SERVICE,
                Res.string.meshtastic_service_notifications,
                NotificationManager.IMPORTANCE_MIN,
            )

        object DirectMessage :
            NotificationType(
                NotificationChannels.MESSAGES,
                Res.string.meshtastic_messages_notifications,
                NotificationManager.IMPORTANCE_HIGH,
            )

        object BroadcastMessage :
            NotificationType(
                NotificationChannels.BROADCASTS,
                Res.string.meshtastic_broadcast_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object Waypoint :
            NotificationType(
                NotificationChannels.WAYPOINTS,
                Res.string.meshtastic_waypoints_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object Alert :
            NotificationType(
                NotificationChannels.ALERTS,
                Res.string.meshtastic_alerts_notifications,
                NotificationManager.IMPORTANCE_HIGH,
            )

        object NewNode :
            NotificationType(
                NotificationChannels.NEW_NODES,
                Res.string.meshtastic_new_nodes_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object LowBatteryLocal :
            NotificationType(
                NotificationChannels.LOW_BATTERY,
                Res.string.meshtastic_low_battery_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object LowBatteryRemote :
            NotificationType(
                NotificationChannels.LOW_BATTERY_REMOTE,
                Res.string.meshtastic_low_battery_temporary_remote_notifications,
                NotificationManager.IMPORTANCE_DEFAULT,
            )

        object Client :
            NotificationType(
                NotificationChannels.CLIENT,
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
        notificationManager.removeLegacyCategoryChannels()
        NotificationType.allTypes().forEach { type -> createNotificationChannel(type) }
    }

    private fun createNotificationChannel(type: NotificationType) {
        if (notificationManager.getNotificationChannel(type.channelId) != null) return

        val channelName = getString(type.channelNameRes)
        val channel =
            NotificationChannel(type.channelId, channelName, type.importance).apply {
                lightColor = NOTIFICATION_COLOR
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
                        val alertSoundUri =
                            "${SCHEME_ANDROID_RESOURCE}://${context.packageName}/${raw.meshtastic_alert}".toUri()
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

    private var cachedDeviceMetrics: DeviceMetrics? = null
    private var cachedLocalStats: LocalStats? = null
    private var nextStatsUpdateMillis: Long = 0
    private var cachedMessage: String? = null
    private var cachedServiceNotification: Notification? = null

    /**
     * Returns the last-built service state notification, or builds a default one if none exists. This is used by
     * [MeshService] for [android.app.Service.startForeground].
     */
    fun getServiceNotification(): Notification = cachedServiceNotification
        ?: createServiceStateNotification(
            name = getString(Res.string.meshtastic_app_name),
            message = null,
            nextUpdateAt = 0,
        )

    // region Public Notification Methods
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) {
        val summaryString =
            when (state) {
                is ConnectionState.Connected ->
                    getString(Res.string.meshtastic_app_name) + ": " + getString(Res.string.connected)

                is ConnectionState.Disconnected -> getString(Res.string.disconnected)

                is ConnectionState.DeviceSleep -> getString(Res.string.device_sleeping)

                is ConnectionState.Connecting -> getString(Res.string.connecting)
            }

        // Update caches if telemetry is provided
        telemetry?.let { t ->
            t.local_stats?.let { stats ->
                cachedLocalStats = stats
                nextStatsUpdateMillis = nowMillis + STATS_UPDATE_INTERVAL.inWholeMilliseconds
            }
            t.device_metrics?.let { metrics -> cachedDeviceMetrics = metrics }
        }

        // Seeding from database if caches are still null (e.g. on restart or reconnection)
        if (cachedLocalStats == null || cachedDeviceMetrics == null) {
            val repo = nodeRepository.value
            val myNodeNum = repo.myNodeInfo.value?.myNodeNum
            if (myNodeNum != null) {
                // Use .value instead of runBlocking { .first() } to avoid potential deadlock
                // if called on the same dispatcher the Flow's upstream coroutine needs.
                val nodes = repo.nodeDBbyNum.value
                nodes[myNodeNum]?.let { node ->
                    if (cachedDeviceMetrics == null) {
                        cachedDeviceMetrics = node.deviceMetrics
                    }
                    if (cachedLocalStats == null) {
                        // Fallback to DB stats if repository hasn't received any fresh ones yet
                        cachedLocalStats = repo.localStats.value.takeIf { it.uptime_seconds != 0 }
                    }
                }
            }
        }

        val stats = cachedLocalStats
        val metrics = cachedDeviceMetrics

        val message =
            when {
                stats != null -> stats.formatToString(metrics?.battery_level)
                metrics != null -> metrics.formatToString()
                else -> null
            }

        // Only update cachedMessage if we have something new, otherwise keep what we have.
        // Fallback to "No Stats Available" only if we truly have nothing.
        if (message != null) {
            cachedMessage = message
        } else if (cachedMessage == null) {
            cachedMessage = getString(Res.string.no_local_stats)
        }

        val notification =
            createServiceStateNotification(
                name = summaryString.orEmpty(),
                message = cachedMessage,
                nextUpdateAt = nextStatsUpdateMillis,
            )
        cachedServiceNotification = notification
        notificationManager.notify(SERVICE_NOTIFY_ID, notification)
    }

    override suspend fun updateMessageNotification(
        contactKey: String,
        name: String,
        message: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {
        showConversationNotification(contactKey, isBroadcast, channelName, conversationName = name, isSilent = isSilent)
    }

    override suspend fun updateReactionNotification(
        contactKey: String,
        name: String,
        emoji: String,
        isBroadcast: Boolean,
        channelName: String?,
        isSilent: Boolean,
    ) {
        showConversationNotification(contactKey, isBroadcast, channelName, conversationName = name, isSilent = isSilent)
    }

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ) {
        val notification = createWaypointNotification(name, message, waypointId, isSilent)
        notificationManager.notify(TAG_WAYPOINT, contactKey.hashCode(), notification)
    }

    private suspend fun showConversationNotification(
        contactKey: String,
        isBroadcast: Boolean,
        channelName: String?,
        conversationName: String,
        isSilent: Boolean = false,
    ) {
        // Publish (or refresh) a long-lived conversation shortcut before the notification references it, so Android can
        // rank the notification in the shade's Conversations section and expose it to Android Auto/Wear. A channel name
        // labels a broadcast conversation; a direct message is labelled by the other participant's name.
        conversationShortcutPublisher.value.ensureConversationShortcut(contactKey, channelName ?: conversationName)

        val ourNode = nodeRepository.value.ourNodeInfo.value
        val history =
            packetRepository.value
                .getMessagesFrom(contactKey, includeFiltered = false) { nodeId ->
                    if (nodeId == NodeAddress.ID_LOCAL) {
                        ourNode ?: nodeRepository.value.getNode(nodeId)
                    } else {
                        nodeRepository.value.getNode(nodeId.orEmpty())
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
        notificationManager.notify(TAG_MESSAGE, contactKey.hashCode(), notification)
        showGroupSummary()
    }

    private fun showGroupSummary(justCancelledId: Int? = null) {
        // Exclude the summary itself by its group-summary flag rather than by id, so a conversation whose
        // contactKey.hashCode() happens to equal SUMMARY_ID is still counted as an active conversation.
        // Also exclude a conversation we just cancelled: activeNotifications does not reflect a cancel() issued
        // moments earlier, so without this the summary would be rebuilt from stale state and orphaned in the shade
        // (and Android Auto) after the last message is dismissed via reply / mark-as-read.
        val activeNotifications =
            notificationManager.activeNotifications.filter {
                it.notification.group == GROUP_KEY_MESSAGES &&
                    (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                    !(it.tag == TAG_MESSAGE && it.id == justCancelledId)
            }

        // No conversations left — drop the summary too, otherwise it lingers in Android Auto after the
        // last message notification is cancelled (e.g. on reply / mark-as-read).
        if (activeNotifications.isEmpty()) {
            notificationManager.cancel(TAG_MESSAGE_SUMMARY, SUMMARY_ID)
            return
        }

        val ourNode = nodeRepository.value.ourNodeInfo.value
        val meName = ourNode?.user?.long_name ?: getString(Res.string.you)
        val me =
            Person.Builder()
                .setName(meName)
                .setKey(ourNode?.user?.id ?: NodeAddress.ID_LOCAL)
                .apply {
                    ourNode?.let {
                        setIcon(cachedPersonIcon(it.user.id, it.user.short_name, it.colors.second, it.colors.first))
                    }
                }
                .build()

        val messagingStyle =
            NotificationCompat.MessagingStyle(me)
                .setGroupConversation(true)
                .setConversationTitle(getString(Res.string.meshtastic_app_name))

        activeNotifications.forEach { sbn ->
            // Prefer the child's real MessagingStyle: its latest message carries the actual sender (Person, icon) and
            // timestamp, so the summary line reads "Hawk Ridge: …" rather than the conversation title.
            val latest =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
                    ?.messages
                    ?.lastOrNull()
            if (latest?.text != null) {
                val senderPerson = latest.person ?: Person.Builder().setName(getString(Res.string.you)).build()
                messagingStyle.addMessage(latest.text, latest.timestamp, senderPerson)
            } else {
                // Fallback for children without an extractable style: rebuild a generic line from the extras.
                val senderTitle = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
                val messageText = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)
                if (senderTitle != null && messageText != null) {
                    messagingStyle.addMessage(messageText, sbn.postTime, Person.Builder().setName(senderTitle).build())
                }
            }
        }

        val summaryNotification =
            commonBuilder(NotificationType.DirectMessage)
                .setSmallIcon(drawable.meshtastic_ic_notification)
                .setStyle(messagingStyle)
                .setGroup(GROUP_KEY_MESSAGES)
                .setGroupSummary(true)
                // Only the child conversation notifications alert; without this the summary (posted on a HIGH channel)
                // can buzz a second time for every message.
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true)
                .build()

        notificationManager.notify(TAG_MESSAGE_SUMMARY, SUMMARY_ID, summaryNotification)
    }

    override fun showAlertNotification(contactKey: String, name: String, alert: String) {
        val notification = createAlertNotification(contactKey, name, alert)
        // Use a consistent, unique ID for each alert source.
        notificationManager.notify(TAG_ALERT, name.hashCode(), notification)
    }

    override fun showNewNodeSeenNotification(node: Node) {
        val notification = createNewNodeSeenNotification(node.user.short_name, node.user.long_name, node.num)
        notificationManager.notify(TAG_NEW_NODE, node.num, notification)
    }

    override fun showOrUpdateLowBatteryNotification(node: Node, isRemote: Boolean) {
        val notification = createLowBatteryNotification(node, isRemote)
        notificationManager.notify(TAG_LOW_BATTERY, node.num, notification)
    }

    override fun showClientNotification(clientNotification: ClientNotification) {
        val notification =
            createClientNotification(getString(Res.string.client_notification), clientNotification.message)
        notificationManager.notify(TAG_CLIENT, clientNotification.toString().hashCode(), notification)
    }

    override fun cancelMessageNotification(contactKey: String) {
        val id = contactKey.hashCode()
        notificationManager.cancel(TAG_MESSAGE, id)
        // Rebuild (or clear) the group summary so it doesn't keep showing the dismissed conversation in Android Auto.
        // Pass the id we just cancelled so a stale activeNotifications snapshot doesn't keep the summary alive.
        showGroupSummary(justCancelledId = id)
    }

    /**
     * Re-posts the conversation silently after an inline reply, so the notification updates in place with the sent
     * reply appended (the read context becomes historic, the reply is the visible latest message) instead of
     * disappearing. Re-posting under the same (tag, id) is also what resolves the RemoteInput spinner.
     */
    override suspend fun refreshConversationAfterReply(contactKey: String) {
        val isBroadcast = contactKey.contains(NodeAddress.ID_BROADCAST)
        val conversationName: String
        var channelName: String? = null
        if (isBroadcast) {
            // Resolve the effective channel name (an empty primary shows its modem-preset name, e.g. "LongFast").
            val channelSet = radioConfigRepository.value.channelSetFlow.first()
            val lora = channelSet.lora_config ?: Channel.default.loraConfig
            val index = contactKey.substringBefore(NodeAddress.ID_BROADCAST).toIntOrNull()
            channelName = index?.let { channelSet.settings.getOrNull(it) }?.let { Channel(it, lora).name }
            // Never fall back to the raw contactKey for user-facing labels (privacy-first convention).
            conversationName = channelName ?: getString(Res.string.channel)
        } else {
            // DM contactKey is "<channelIndex><userId>" where userId starts at the '!'.
            val userId = "!" + contactKey.substringAfter("!", missingDelimiterValue = "")
            val peer = nodeRepository.value.nodeDBbyNum.value.values.find { it.user.id == userId }
            conversationName =
                peer?.user?.long_name?.takeIf { it.isNotBlank() } ?: getString(Res.string.unknown_username)
        }
        showConversationNotification(contactKey, isBroadcast, channelName, conversationName, isSilent = true)
    }

    override fun cancelLowBatteryNotification(node: Node) = notificationManager.cancel(TAG_LOW_BATTERY, node.num)

    override fun clearClientNotification(notification: ClientNotification) =
        notificationManager.cancel(TAG_CLIENT, notification.toString().hashCode())

    // endregion

    // region Notification Creation
    private fun createServiceStateNotification(name: String, message: String?, nextUpdateAt: Long?): Notification {
        val builder =
            commonBuilder(NotificationType.ServiceState)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                // Android 12+ may defer FGS notifications ~10s; show immediately so the user watching a
                // "Connecting…" state gets feedback right away.
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentTitle(name)
                .setShowWhen(true)

        message?.let {
            // First line of message is used for collapsed view, ensure it doesn't have a bullet
            builder.setContentText(it.substringBefore("\n").removePrefix(BULLET))
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }

        nextUpdateAt
            ?.takeIf { it > nowMillis }
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

        val ourNode = nodeRepository.value.ourNodeInfo.value
        val meName = ourNode?.user?.long_name ?: getString(Res.string.you)
        val me =
            Person.Builder()
                .setName(meName)
                .setKey(ourNode?.user?.id ?: NodeAddress.ID_LOCAL)
                .apply {
                    ourNode?.let {
                        setIcon(cachedPersonIcon(it.user.id, it.user.short_name, it.colors.second, it.colors.first))
                    }
                }
                .build()

        val style =
            NotificationCompat.MessagingStyle(me)
                .setGroupConversation(channelName != null)
                .setConversationTitle(channelName)

        // Already-read messages are added as *historic* context rather than as new content: MessagingStyle keeps them
        // available to accessibility services and Android Auto/Wear for conversation context without re-presenting them
        // as freshly-arrived messages. Unread messages (and reactions, which are themselves new events) are the actual
        // alerting content. When every message in the window is read (e.g. a reaction arrived on a read message) the
        // most recent one is kept as a normal message so the notification still has alerting content to show.
        val anyUnread = history.any { !it.read }
        val lastIndex = history.lastIndex
        history.forEachIndexed { index, msg ->
            // Use the node attached to the message directly to ensure correct identification
            val person =
                Person.Builder()
                    .setName(msg.node.user.long_name)
                    .setKey(msg.node.user.id)
                    // Favorite nodes feed the system's conversation-priority ranking (shade ordering, suggestions).
                    .setImportant(msg.node.isFavorite)
                    .setIcon(
                        cachedPersonIcon(
                            msg.node.user.id,
                            msg.node.user.short_name,
                            msg.node.colors.second,
                            msg.node.colors.first,
                        ),
                    )
                    .build()

            val text =
                msg.originalMessage?.let { original ->
                    "↩️ \"${original.node.user.short_name}: ${original.text.take(SNIPPET_LENGTH)}...\": ${msg.text}"
                } ?: msg.text

            if (msg.read && (anyUnread || index != lastIndex)) {
                style.addHistoricMessage(NotificationCompat.MessagingStyle.Message(text, msg.receivedTime, person))
            } else {
                style.addMessage(text, msg.receivedTime, person)
            }

            // Add reactions as separate "messages" in history if they exist
            msg.emojis.forEach { reaction ->
                val reactorNode = nodeRepository.value.getNode(reaction.user.id)
                val reactor =
                    Person.Builder()
                        .setName(reaction.user.long_name)
                        .setKey(reaction.user.id)
                        .setIcon(
                            cachedPersonIcon(
                                reaction.user.id,
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
            // Link to the conversation shortcut so this is treated as a Conversation notification (Android 11+).
            .setShortcutId(contactKey)
            .setLocusId(LocusIdCompat(contactKey))
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
        val style = NotificationCompat.MessagingStyle(person).addMessage(message, nowMillis, person)

        val builder =
            commonBuilder(NotificationType.Waypoint, createOpenWaypointIntent(waypointId))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setStyle(style)
                .setGroup(GROUP_KEY_MESSAGES)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setWhen(nowMillis)
                .setShowWhen(true)

        if (isSilent) {
            builder.setSilent(true)
        }

        return builder.build()
    }

    private fun createAlertNotification(contactKey: String, name: String, alert: String): Notification {
        val person = Person.Builder().setName(name).build()
        val style = NotificationCompat.MessagingStyle(person).addMessage(alert, nowMillis, person)

        return commonBuilder(NotificationType.Alert, createOpenMessageIntent(contactKey))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setStyle(style)
            .build()
    }

    private fun createNewNodeSeenNotification(name: String, message: String, nodeNum: Int): Notification {
        val title = getString(Res.string.new_node_seen, name)
        val builder =
            commonBuilder(NotificationType.NewNode, createOpenNodeDetailIntent(nodeNum))
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setWhen(nowMillis)
                .setShowWhen(true)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        return builder.build()
    }

    private fun createLowBatteryNotification(node: Node, isRemote: Boolean): Notification {
        val type = if (isRemote) NotificationType.LowBatteryRemote else NotificationType.LowBatteryLocal
        val title = getString(Res.string.low_battery_title, node.user.short_name)
        val batteryLevel = node.deviceMetrics.battery_level ?: 0
        val message = getString(Res.string.low_battery_message, node.user.long_name, batteryLevel)

        return commonBuilder(type, createOpenNodeDetailIntent(node.num))
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(MAX_BATTERY_LEVEL, batteryLevel, false)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setWhen(nowMillis)
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
        val intent =
            Intent(context, Class.forName("org.meshtastic.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createOpenMessageIntent(contactKey: String): PendingIntent {
        val deepLinkUri = "$DEEP_LINK_BASE_URI/messages/$contactKey".toUri()
        val deepLinkIntent =
            Intent(Intent.ACTION_VIEW, deepLinkUri, context, Class.forName("org.meshtastic.app.MainActivity")).apply {
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
            Intent(Intent.ACTION_VIEW, deepLinkUri, context, Class.forName("org.meshtastic.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(waypointId, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    private fun createOpenNodeDetailIntent(nodeNum: Int): PendingIntent {
        val deepLinkUri = "$DEEP_LINK_BASE_URI/nodes/$nodeNum".toUri()
        val deepLinkIntent =
            Intent(Intent.ACTION_VIEW, deepLinkUri, context, Class.forName("org.meshtastic.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            getPendingIntent(nodeNum, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
            // Required for Android Auto to drive reply hands-free without opening any UI.
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
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

        return NotificationCompat.Action.Builder(android.R.drawable.ic_menu_view, label, pendingIntent)
            // Required for Android Auto to mark a conversation read hands-free without opening any UI.
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
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
                putExtra(ReactionReceiver.EXTRA_REPLY_ID, packetId)
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
        val smallIcon = drawable.meshtastic_ic_notification

        return NotificationCompat.Builder(context, type.channelId)
            .setSmallIcon(smallIcon)
            .setColor(NOTIFICATION_COLOR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent ?: openAppIntent)
    }

    // endregion

    // region Extension Functions (Localized)

    private fun LocalStats.formatToString(batteryLevel: Int? = null): String {
        val parts = mutableListOf<String>()
        batteryLevel?.let {
            if (it > MAX_BATTERY_LEVEL) {
                parts.add(BULLET + getString(Res.string.powered))
            } else {
                parts.add(BULLET + getString(Res.string.local_stats_battery, it))
            }
        }
        parts.add(BULLET + getString(Res.string.local_stats_nodes, num_online_nodes, num_total_nodes))
        parts.add(BULLET + getString(Res.string.local_stats_uptime, formatUptime(uptime_seconds)))
        parts.add(
            BULLET +
                getString(
                    Res.string.local_stats_utilization,
                    NumberFormatter.format(channel_utilization.toDouble(), 2),
                    NumberFormatter.format(air_util_tx.toDouble(), 2),
                ),
        )

        if (heap_free_bytes > 0 || heap_total_bytes > 0) {
            parts.add(
                BULLET +
                    getString(Res.string.local_stats_heap) +
                    ": " +
                    getString(Res.string.local_stats_heap_value, heap_free_bytes, heap_total_bytes),
            )
        }

        // Traffic Stats
        if (num_packets_tx > 0 || num_packets_rx > 0) {
            parts.add(BULLET + getString(Res.string.local_stats_traffic, num_packets_tx, num_packets_rx, num_rx_dupe))
        }
        if (num_tx_relay > 0) {
            parts.add(BULLET + getString(Res.string.local_stats_relays, num_tx_relay, num_tx_relay_canceled))
        }

        // Diagnostic Fields
        val diagnosticParts = mutableListOf<String>()
        if (noise_floor != 0) diagnosticParts.add(getString(Res.string.local_stats_noise, noise_floor))
        if (num_packets_rx_bad > 0) {
            diagnosticParts.add(getString(Res.string.local_stats_bad, num_packets_rx_bad))
        }
        if (num_tx_dropped > 0) diagnosticParts.add(getString(Res.string.local_stats_dropped, num_tx_dropped))

        if (diagnosticParts.isNotEmpty()) {
            parts.add(
                BULLET + getString(Res.string.local_stats_diagnostics_prefix, diagnosticParts.joinToString(" | ")),
            )
        }

        return parts.joinToString("\n")
    }

    private fun DeviceMetrics.formatToString(): String {
        val parts = mutableListOf<String>()
        battery_level?.let { parts.add(BULLET + getString(Res.string.local_stats_battery, it)) }
        uptime_seconds?.let { parts.add(BULLET + getString(Res.string.local_stats_uptime, formatUptime(it))) }
        if (channel_utilization != null || air_util_tx != null) {
            parts.add(
                BULLET +
                    getString(
                        Res.string.local_stats_utilization,
                        NumberFormatter.format((channel_utilization ?: 0f).toDouble(), 2),
                        NumberFormatter.format((air_util_tx ?: 0f).toDouble(), 2),
                    ),
            )
        }
        return parts.joinToString("\n")
    }

    // endregion
}
