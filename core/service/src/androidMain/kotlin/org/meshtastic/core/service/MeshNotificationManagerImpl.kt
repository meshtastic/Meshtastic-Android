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
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.LocusIdCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.state.RadioOperation
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.noiseFloorOrNull
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.repository.FirmwareUpdateProgress
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.SERVICE_NOTIFY_ID
import org.meshtastic.core.repository.notificationId
import org.meshtastic.core.resources.R.drawable
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channel
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.device_sleeping
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.discovery_scan_in_progress
import org.meshtastic.core.resources.firmware_update_available
import org.meshtastic.core.resources.firmware_update_in_progress
import org.meshtastic.core.resources.firmware_update_notification_android
import org.meshtastic.core.resources.formatDurationSuspend
import org.meshtastic.core.resources.getStringSuspend
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
import org.meshtastic.core.resources.mesh_beacon_notification_body
import org.meshtastic.core.resources.mesh_beacon_notification_title
import org.meshtastic.core.resources.meshtastic_app_name
import org.meshtastic.core.resources.no_local_stats
import org.meshtastic.core.resources.notification_reaction_to
import org.meshtastic.core.resources.powered
import org.meshtastic.core.resources.reply
import org.meshtastic.core.resources.unknown_username
import org.meshtastic.core.resources.you
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.Telemetry
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.meshtastic.core.repository.Notification as MeshNotification

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
    private val radioOperationLock: RadioOperationLock,
    private val firmwareUpdateStatusRepository: FirmwareUpdateStatusRepository,
    private val scope: ServiceScope,
) : MeshNotificationManager {

    private val notificationManager =
        checkNotNull(context.getSystemService<NotificationManager>()) { "NotificationManager not found" }

    companion object {
        const val MAX_BATTERY_LEVEL = 100

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

        /**
         * Fully-qualified name of the bubble host, as a string to avoid a module dependency back onto `:androidApp`.
         */
        private const val BUBBLE_ACTIVITY_CLASS = "org.meshtastic.app.BubbleActivity"

        /** Roughly a phone's lower half — enough conversation to reply in without covering what is underneath. */
        private const val BUBBLE_DESIRED_HEIGHT_DP = 600
        private const val TAG_MESSAGE_SUMMARY = "message_summary"
        private const val TAG_WAYPOINT = "waypoint"
        private const val TAG_REACTION = "reaction"
        private const val TAG_ALERT = "alert"
        private const val TAG_NEW_NODE = "new_node"
        private const val TAG_LOW_BATTERY = "low_battery"
        private const val TAG_CLIENT = "client"
        private const val TAG_MESH_BEACON = "mesh_beacon"
        private const val TAG_FIRMWARE_UPDATE = "firmware_update"
        private const val TAG_RECONNECT_BLOCKED = "reconnect_blocked"
        private const val RECONNECT_BLOCKED_ID = 1
        private val CHANNEL_LABEL_TIMEOUT = 2.seconds
        private const val MAIN_ACTIVITY_CLASS = "org.meshtastic.app.MainActivity"
        private const val THUMBS_UP = "👍"
    }

    private data class ServiceNotificationSnapshot(
        val state: ConnectionState,
        val localStats: LocalStats?,
        val deviceMetrics: DeviceMetrics?,
        val previousMessage: String?,
        val nextUpdateAt: Long,
        val activeOperations: Set<RadioOperation> = emptySet(),
        val firmwareProgress: FirmwareUpdateProgress? = null,
    )

    /** [message] is the stats text to fall back on later; a firmware-progress render keeps the previous one. */
    private data class RenderedServiceNotification(val notification: Notification, val message: String?)

    /**
     * Caches generated avatar icons keyed by (person id + short name + colors) so a conversation rebuild does not
     * re-rasterize an avatar bitmap for every message on every update. Bounded by the number of distinct nodes seen;
     * entries are cheap and colors/names rarely change.
     */
    private val personIconCache = ConcurrentHashMap<String, IconCompat>()

    /** Adaptive variant, for notification bubbles, which the system masks to its own shape. */
    private fun cachedBubblePersonIcon(key: String, shortName: String, backgroundColor: Int, foregroundColor: Int) =
        personIconCache.getOrPut("adaptive|$key|$shortName|$backgroundColor|$foregroundColor") {
            PersonIconFactory.createAdaptive(shortName, backgroundColor, foregroundColor)
        }

    /** Circular, node-colored avatar holding the sender's full short name (e.g. "2c3d"), not just its first letter. */
    private fun cachedPersonIcon(key: String, shortName: String, backgroundColor: Int, foregroundColor: Int) =
        personIconCache.getOrPut("$key|$shortName|$backgroundColor|$foregroundColor") {
            PersonIconFactory.createLabel(shortName, backgroundColor, foregroundColor, rounded = false)
        }

    override fun clearNotifications() = synchronized(lowBatteryLock) {
        lowBatteryEpisodes.clear()
        notificationManager.cancelAll()
    }

    /**
     * Guarantees the foreground-service channel synchronously, because the orchestrator posts the service notification
     * right after this returns, then creates the rest off the calling thread. Every post awaits [ensureChannels].
     */
    override fun initChannels() {
        notificationManager.removeLegacyCategoryChannels()
        ensureServiceChannel()
        scope.launch { ensureChannels() }
    }

    /** Creates the service channel under the app label if it is missing; [ensureChannels] later gives it its name. */
    private fun ensureServiceChannel() {
        if (notificationManager.getNotificationChannel(NotificationChannelSpec.Service.id) != null) return
        // The platform throws for a channel whose group does not exist yet, and this runs before ensureChannels.
        notificationManager.createNotificationChannelGroup(
            NotificationChannelSpec.Service.group.toGroup(applicationLabel),
        )
        notificationManager.createNotificationChannel(
            NotificationChannelSpec.Service.toChannel(context, name = applicationLabel, description = ""),
        )
    }

    private val channelsMutex = Mutex()
    private var channelsReady = false

    private class ChannelLabels(
        val groups: Map<NotificationChannelGroupSpec, String>,
        val names: Map<NotificationChannelSpec, String>,
        val descriptions: Map<NotificationChannelSpec, String>,
    )

    private suspend fun resolveChannelLabels() = ChannelLabels(
        groups = NotificationChannelGroupSpec.entries.associateWith { getStringSuspend(it.nameRes) },
        names = NotificationChannelSpec.entries.associateWith { getStringSuspend(it.nameRes) },
        descriptions = NotificationChannelSpec.entries.associateWith { getStringSuspend(it.descriptionRes) },
    )

    /**
     * Creates every channel group and channel, once per process. Re-creating an existing channel only refreshes its
     * name, description and (if it had none) group, so this also carries a locale change and the grouping onto installs
     * whose channels predate them.
     *
     * Label loading is bounded: a boot broadcast posts through here with seconds to live, and a channel under the app
     * label beats no notification. Such a pass does not count as done, so the next post puts the real labels on.
     */
    internal suspend fun ensureChannels() = channelsMutex.withLock {
        if (channelsReady) return@withLock
        val labels = withTimeoutOrNull(CHANNEL_LABEL_TIMEOUT) { safeCatching { resolveChannelLabels() }.getOrNull() }
        val groups =
            NotificationChannelGroupSpec.entries.map { it.toGroup(labels?.groups?.get(it) ?: applicationLabel) }
        notificationManager.createNotificationChannelGroups(groups)
        val channels =
            NotificationChannelSpec.entries.map { spec ->
                spec.toChannel(
                    context,
                    name = labels?.names?.get(spec) ?: applicationLabel,
                    description = labels?.descriptions?.get(spec).orEmpty(),
                )
            }
        notificationManager.createNotificationChannels(channels)
        channelsReady = labels != null
    }

    /** Whether a post on [spec] would reach the user: app notifications on, and the channel not blocked. */
    private fun canPost(spec: NotificationChannelSpec): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            notificationManager.getNotificationChannel(spec.id)?.importance != NotificationManager.IMPORTANCE_NONE

    private val serviceNotificationLock = Any()
    private val lowBatteryLock = Any()

    /** Each node in a low-battery episode, mapped to that episode's identity; guarded by [lowBatteryLock]. */
    private val lowBatteryEpisodes = mutableMapOf<Int, Any>()
    private val applicationLabel: String by lazy {
        context.applicationInfo.loadLabel(context.packageManager).toString().ifBlank { context.packageName }
    }
    private var cachedDeviceMetrics: DeviceMetrics? = null
    private var cachedLocalStats: LocalStats? = null
    private var nextStatsUpdateMillis: Long = 0
    private var cachedMessage: String? = null
    private var cachedServiceNotification: Notification? = null
    private val serviceNotificationSnapshots =
        MutableSharedFlow<ServiceNotificationSnapshot>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch { serviceNotificationSnapshots.collectLatest(::renderAndPostServiceNotification) }
        // A long operation changes the notification without any connection-state change to piggyback on — a
        // firmware update in particular deselects the device, so the state alone would read "Disconnected".
        scope.launch {
            radioOperationLock.holders.collect { holders ->
                synchronized(serviceNotificationLock) {
                    val last = serviceNotificationSnapshots.replayCache.lastOrNull() ?: return@synchronized
                    serviceNotificationSnapshots.tryEmit(last.copy(activeOperations = holders.values.toSet()))
                }
            }
        }
        scope.launch {
            firmwareUpdateStatusRepository.progress.collect { progress ->
                synchronized(serviceNotificationLock) {
                    val last = serviceNotificationSnapshots.replayCache.lastOrNull() ?: return@synchronized
                    serviceNotificationSnapshots.tryEmit(last.copy(firmwareProgress = progress))
                }
            }
        }
    }

    /**
     * Returns the last-built service state notification, or an immediately available application-label fallback. This
     * method is used by [MeshService] for [android.app.Service.startForeground], so it must not wait for Compose
     * Multiplatform resource loading.
     */
    fun getServiceNotification(): Notification {
        ensureServiceChannel()
        val cached = synchronized(serviceNotificationLock) { cachedServiceNotification }
        return cached ?: createServiceStateNotification(name = applicationLabel, message = null, nextUpdateAt = 0)
    }

    // region Public Notification Methods
    override fun updateServiceStateNotification(state: ConnectionState, telemetry: Telemetry?) {
        synchronized(serviceNotificationLock) {
            serviceNotificationSnapshots.tryEmit(captureServiceNotificationSnapshot(state, telemetry))
        }
    }

    /**
     * Renders service notifications in one ordered collector. [collectLatest] cancels obsolete resource work and does
     * not start the next renderer until the previous block has completed, so Binder notification posts cannot overtake
     * one another and leave stale text visible.
     */
    private suspend fun renderAndPostServiceNotification(snapshot: ServiceNotificationSnapshot) {
        safeCatching {
            val rendered = renderServiceNotification(snapshot)
            postServiceNotification(rendered.notification, rendered.message)
        }
            .onFailure { ex ->
                Logger.e(ex) { "Failed to render service notification resources" }
                val fallback =
                    createServiceStateNotification(
                        name = applicationLabel,
                        message = snapshot.previousMessage,
                        nextUpdateAt = snapshot.nextUpdateAt,
                    )
                safeCatching { postServiceNotification(fallback, snapshot.previousMessage) }
                    .onFailure { Logger.e(it) { "Failed to post fallback service notification" } }
            }
    }

    /** Updates the foreground fallback cache before posting the same notification through Android's Binder API. */
    private fun postServiceNotification(notification: Notification, message: String?) {
        synchronized(serviceNotificationLock) {
            cachedMessage = message
            cachedServiceNotification = notification
        }
        notificationManager.notify(SERVICE_NOTIFY_ID, notification)
    }

    private fun captureServiceNotificationSnapshot(
        state: ConnectionState,
        telemetry: Telemetry?,
    ): ServiceNotificationSnapshot {
        telemetry?.let { currentTelemetry ->
            currentTelemetry.local_stats?.let { stats ->
                cachedLocalStats = stats
                nextStatsUpdateMillis = nowMillis + STATS_UPDATE_INTERVAL.inWholeMilliseconds
            }
            currentTelemetry.device_metrics?.let { metrics -> cachedDeviceMetrics = metrics }
        }

        // Seed from database if the in-memory telemetry cache is empty after a process or transport restart.
        if (cachedLocalStats == null || cachedDeviceMetrics == null) {
            val repo = nodeRepository.value
            val myNodeNum = repo.myNodeInfo.value?.myNodeNum
            if (cachedDeviceMetrics == null && myNodeNum != null) {
                cachedDeviceMetrics = repo.nodeDBbyNum.value[myNodeNum]?.deviceMetrics
            }
            if (cachedLocalStats == null) {
                cachedLocalStats = repo.localStats.value.takeIf { it.uptime_seconds != 0 }
            }
        }

        return ServiceNotificationSnapshot(
            state = state,
            localStats = cachedLocalStats,
            deviceMetrics = cachedDeviceMetrics,
            previousMessage = cachedMessage,
            nextUpdateAt = nextStatsUpdateMillis,
            activeOperations = radioOperationLock.activeOperations,
            firmwareProgress = firmwareUpdateStatusRepository.progress.value,
        )
    }

    private suspend fun renderServiceNotification(snapshot: ServiceNotificationSnapshot): RenderedServiceNotification {
        snapshot.firmwareProgress?.let { progress ->
            val notification =
                createFirmwareProgressNotification(
                    title = getStringSuspend(Res.string.firmware_update_in_progress),
                    text = progress.message.resolve(),
                    percent = progress.percent,
                )
            return RenderedServiceNotification(notification, message = snapshot.previousMessage)
        }
        // A held operation outranks the connection state. During a firmware update the device is deliberately
        // deselected, so the state alone would report "Disconnected" over a flash that is running perfectly.
        val title =
            snapshot.activeOperations.notificationTitleResource()?.let { getStringSuspend(it) }
                ?: when (snapshot.state) {
                    is ConnectionState.Connected ->
                        getStringSuspend(Res.string.meshtastic_app_name) + ": " + getStringSuspend(Res.string.connected)

                    is ConnectionState.Disconnected -> getStringSuspend(Res.string.disconnected)

                    is ConnectionState.DeviceSleep -> getStringSuspend(Res.string.device_sleeping)

                    is ConnectionState.Connecting -> getStringSuspend(Res.string.connecting)
                }

        val freshMessage =
            when {
                snapshot.localStats != null ->
                    snapshot.localStats.formatToStringSuspend(snapshot.deviceMetrics?.battery_level)

                snapshot.deviceMetrics != null -> snapshot.deviceMetrics.formatToStringSuspend()

                else -> null
            }
        val message = freshMessage ?: snapshot.previousMessage ?: getStringSuspend(Res.string.no_local_stats)
        return RenderedServiceNotification(
            notification = createServiceStateNotification(title, message, snapshot.nextUpdateAt),
            message = message,
        )
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
        ensureChannels()
        val builder =
            commonBuilder(NotificationChannelSpec.Reactions, createOpenMessageIntent(contactKey))
                .setContentTitle(name)
                .setContentText(emoji)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
        if (isSilent) builder.setSilent(true)
        notificationManager.notify(TAG_REACTION, contactKey.hashCode(), builder.build())
    }

    override suspend fun updateWaypointNotification(
        contactKey: String,
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ) {
        ensureChannels()
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
        ensureChannels()
        // Publish (or refresh) a long-lived conversation shortcut before the notification references it, so Android can
        // rank the notification in the shade's Conversations section and expose it to Android Auto/Wear. A channel name
        // labels a broadcast conversation; a direct message is labelled by the other participant's name.
        // Both names come from packet metadata and can be empty, so prefer the first non-blank one.
        conversationShortcutPublisher.value.ensureConversationShortcut(
            contactKey,
            channelName?.takeIf { it.isNotBlank() } ?: conversationName,
        )

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

    private suspend fun showGroupSummary(justCancelledId: Int? = null) {
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

        // InboxStyle, not MessagingStyle: the summary has no reply or mark-as-read actions, and Android Auto takes a
        // MessagingStyle notification for a conversation it can answer.
        val you = getStringSuspend(Res.string.you)
        val lines = activeNotifications.mapNotNull { sbn ->
            // Prefer the child's real MessagingStyle: its latest message carries the actual sender, so the line
            // reads "Hawk Ridge: …" rather than the conversation title.
            val latest =
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
                    ?.messages
                    ?.lastOrNull()
            val sender = latest?.person?.name ?: latest?.let { you }
            val text = latest?.text
            if (sender != null && text != null) {
                "$sender: $text"
            } else {
                val senderTitle = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)
                val messageText = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)
                if (senderTitle != null && messageText != null) "$senderTitle: $messageText" else null
            }
        }
        val appName = getStringSuspend(Res.string.meshtastic_app_name)
        val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(appName)
        lines.forEach { inboxStyle.addLine(it) }

        val summaryNotification =
            commonBuilder(NotificationChannelSpec.DirectMessages)
                .setSmallIcon(drawable.meshtastic_ic_notification)
                .setContentTitle(appName)
                .setContentText(lines.lastOrNull())
                .setStyle(inboxStyle)
                .setGroup(GROUP_KEY_MESSAGES)
                .setGroupSummary(true)
                // Only the child conversation notifications alert; without this the summary (posted on a HIGH channel)
                // can buzz a second time for every message.
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true)
                .build()

        notificationManager.notify(TAG_MESSAGE_SUMMARY, SUMMARY_ID, summaryNotification)
    }

    override suspend fun showAlertNotification(contactKey: String, name: String, alert: String) {
        ensureChannels()
        notificationManager.notify(TAG_ALERT, contactKey.hashCode(), createAlertNotification(contactKey, name, alert))
    }

    override suspend fun showMeshBeaconNotification(offer: MeshBeaconOffer) {
        ensureChannels()
        val title = getStringSuspend(Res.string.mesh_beacon_notification_title)
        val message = offer.message.ifBlank { getStringSuspend(Res.string.mesh_beacon_notification_body) }
        val notification =
            commonBuilder(NotificationChannelSpec.MeshBeacon, createDeepLinkIntent("discovery", offer.fromNodeNum))
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .build()
        notificationManager.notify(TAG_MESH_BEACON, offer.fromNodeNum, notification)
    }

    override suspend fun showNewNodeSeenNotification(node: Node, title: String) {
        ensureChannels()
        notificationManager.notify(TAG_NEW_NODE, node.num, createNewNodeSeenNotification(title, node))
    }

    override fun cancelNewNodeNotification(nodeNum: Int) = notificationManager.cancel(TAG_NEW_NODE, nodeNum)

    /**
     * Recovery and device switches end an episode from separate work, so a post that was still resolving its text when
     * its episode ended must not land afterwards: it posts only while the episode it started in is current.
     */
    override suspend fun notifyLowBattery(node: Node, isRemote: Boolean) {
        val (episode, firstReading) =
            synchronized(lowBatteryLock) {
                lowBatteryEpisodes[node.num]?.let { it to false }
                    ?: Any().also { lowBatteryEpisodes[node.num] = it }.let { it to true }
            }
        var posted = false
        try {
            ensureChannels()
            val notification = createLowBatteryNotification(node, isRemote)
            beforeLowBatteryPost?.invoke()
            synchronized(lowBatteryLock) {
                if (lowBatteryEpisodes[node.num] !== episode) return
                val showing =
                    notificationManager.activeNotifications.any { it.tag == TAG_LOW_BATTERY && it.id == node.num }
                if (firstReading || showing) notificationManager.notify(TAG_LOW_BATTERY, node.num, notification)
                posted = true
            }
        } finally {
            // A first reading that failed before posting ends its own episode, so the next low reading tries again.
            if (firstReading && !posted) synchronized(lowBatteryLock) { lowBatteryEpisodes.remove(node.num, episode) }
        }
    }

    /** Test seam run between building a low-battery notification and posting it, to race a recovery in. */
    internal var beforeLowBatteryPost: (() -> Unit)? = null

    override suspend fun showClientNotification(
        clientNotification: ClientNotification,
        title: String,
        severity: MeshNotification.Type,
    ) {
        ensureChannels()
        val message = clientNotification.message
        val warning = severity == MeshNotification.Type.Warning || severity == MeshNotification.Type.Error
        val notification =
            commonBuilder(NotificationChannelSpec.Client)
                .setCategory(if (warning) Notification.CATEGORY_ERROR else Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                // The firmware repeats this advisory on every position request; the tray slot is stable across
                // repeats (see notificationId), so only the first one should sound.
                .setOnlyAlertOnce(clientNotification.isProtectedPositionAdvisory())
                .build()
        notificationManager.notify(TAG_CLIENT, clientNotification.notificationId(), notification)
    }

    override fun clearClientNotification(clientNotification: ClientNotification) =
        notificationManager.cancel(TAG_CLIENT, clientNotification.notificationId())

    override fun suppressClientNotificationModal(clientNotification: ClientNotification): Boolean =
        clientNotification.isProtectedPositionAdvisory()

    override suspend fun showFirmwareUpdateNotification(notice: FirmwareUpdateNotice): Boolean {
        ensureChannels()
        if (!canPost(NotificationChannelSpec.DeviceStatus)) return false
        val message =
            getStringSuspend(
                Res.string.firmware_update_notification_android,
                notice.currentVersion,
                notice.stableVersion,
            )
        val id = notice.notificationKey.hashCode()
        val notification =
            commonBuilder(NotificationChannelSpec.DeviceStatus, createDeepLinkIntent("firmware/update", id))
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentTitle(getStringSuspend(Res.string.firmware_update_available))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .build()
        notificationManager.notify(TAG_FIRMWARE_UPDATE, id, notification)
        return true
    }

    override suspend fun showReconnectBlockedNotification(title: String, message: String): Boolean {
        ensureChannels()
        if (!canPost(NotificationChannelSpec.DeviceStatus)) return false
        val notification =
            commonBuilder(
                NotificationChannelSpec.DeviceStatus,
                createDeepLinkIntent("connections", RECONNECT_BLOCKED_ID),
            )
                .setCategory(Notification.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .build()
        notificationManager.notify(TAG_RECONNECT_BLOCKED, RECONNECT_BLOCKED_ID, notification)
        return true
    }

    override suspend fun cancelMessageNotification(contactKey: String) {
        val id = contactKey.hashCode()
        notificationManager.cancel(TAG_MESSAGE, id)
        notificationManager.cancel(TAG_REACTION, id)
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
            conversationName = channelName ?: getStringSuspend(Res.string.channel)
        } else {
            // DM contactKey is "<channelIndex><userId>" where userId starts at the '!'.
            val userId = "!" + contactKey.substringAfter("!", missingDelimiterValue = "")
            val peer = nodeRepository.value.nodeDBbyNum.value.values.find { it.user.id == userId }
            conversationName =
                peer?.user?.long_name?.takeIf { it.isNotBlank() } ?: getStringSuspend(Res.string.unknown_username)
        }
        showConversationNotification(contactKey, isBroadcast, channelName, conversationName, isSilent = true)
    }

    override fun cancelLowBatteryNotification(nodeNum: Int) = synchronized(lowBatteryLock) {
        lowBatteryEpisodes.remove(nodeNum)
        notificationManager.cancel(TAG_LOW_BATTERY, nodeNum)
    }

    // endregion

    // region Notification Creation
    private fun createServiceStateNotification(name: String, message: String?, nextUpdateAt: Long?): Notification {
        val builder =
            commonBuilder(NotificationChannelSpec.Service)
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

    /**
     * A flash is a start-to-end journey the user may background, so the service notification asks to be promoted to a
     * Live Update while one runs. The platform grants it only on a channel above MIN and with
     * POST_PROMOTED_NOTIFICATIONS; elsewhere this is an ordinary ongoing progress notification.
     */
    private fun createFirmwareProgressNotification(title: String, text: String, percent: Int?): Notification {
        val style = NotificationCompat.ProgressStyle().setProgressIndeterminate(percent == null)
        percent?.let { style.setProgress(it) }
        return commonBuilder(NotificationChannelSpec.Service)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setRequestPromotedOngoing(true)
            .apply { percent?.let { setShortCriticalText(MetricFormatter.percent(it)) } }
            .build()
    }

    @Suppress("LongMethod")
    private suspend fun createConversationNotification(
        contactKey: String,
        isBroadcast: Boolean,
        channelName: String?,
        history: List<Message>,
        isSilent: Boolean = false,
    ): Notification {
        val type = if (isBroadcast) NotificationChannelSpec.Broadcasts else NotificationChannelSpec.DirectMessages
        val builder = commonBuilder(type, createOpenMessageIntent(contactKey))

        if (isSilent) {
            builder.setSilent(true)
        }

        val ourNode = nodeRepository.value.ourNodeInfo.value
        val meName = ourNode?.user?.long_name ?: getStringSuspend(Res.string.you)
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
                    "↩️ \"${original.node.user.short_name}: ${snippet(original.text)}\": ${msg.text}"
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
                    getStringSuspend(Res.string.notification_reaction_to, reaction.emoji, snippet(msg.text)),
                    reaction.timestamp,
                    reactor,
                )
            }
        }
        val lastMessage = history.last()
        // The bubble wears the other party's avatar, not ours — a bubble is recognised by who is in it. After a reply
        // the newest message is our own, so look back for the newest one someone else sent.
        val bubbleNode = history.lastOrNull { !it.fromLocal }?.node ?: lastMessage.node
        val bubbleIcon =
            cachedBubblePersonIcon(
                bubbleNode.user.id,
                bubbleNode.user.short_name,
                bubbleNode.colors.second,
                bubbleNode.colors.first,
            )

        builder
            .setCategory(Notification.CATEGORY_MESSAGE)
            // Link to the conversation shortcut so this is treated as a Conversation notification (Android 11+).
            .setShortcutId(contactKey)
            .setLocusId(LocusIdCompat(contactKey))
            // MessagingStyle carries per-message Persons, but conversation and bubble eligibility is judged on the
            // notification's own person list, so the counterpart is added here too.
            .addPerson(
                Person.Builder()
                    .setName(bubbleNode.user.long_name.ifEmpty { bubbleNode.user.short_name })
                    .setKey(bubbleNode.user.id)
                    .setIcon(bubbleIcon)
                    .build(),
            )
            .setBubbleMetadata(createBubbleMetadata(contactKey, bubbleIcon))
            .apply {
                // Android Auto shows the large icon as the conversation image; a channel keeps the car's default.
                if (!isBroadcast) {
                    val avatar =
                        cachedPersonIcon(
                            bubbleNode.user.id,
                            bubbleNode.user.short_name,
                            bubbleNode.colors.second,
                            bubbleNode.colors.first,
                        )
                    setLargeIcon(avatar.toIcon(context))
                }
            }
            .setAutoCancel(true)
            .setStyle(style)
            .setGroup(GROUP_KEY_MESSAGES)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setWhen(lastMessage.receivedTime)
            .setShowWhen(true)
            .addAction(createReplyAction(contactKey))
            .addAction(createMarkAsReadAction(contactKey))
            .addAction(createReactionAction(contactKey = contactKey, packetId = lastMessage.packetId))

        return builder.build()
    }

    private fun createWaypointNotification(
        name: String,
        message: String,
        waypointId: Int,
        isSilent: Boolean,
    ): Notification {
        // Not MessagingStyle: only a conversation that can be replied to may look like one to Android Auto.
        val builder =
            commonBuilder(
                NotificationChannelSpec.Waypoints,
                createDeepLinkIntent("map?waypointId=$waypointId", waypointId),
            )
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentTitle(name)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setWhen(nowMillis)
                .setShowWhen(true)

        if (isSilent) {
            builder.setSilent(true)
        }

        return builder.build()
    }

    private fun createAlertNotification(contactKey: String, name: String, alert: String): Notification =
        commonBuilder(NotificationChannelSpec.Alerts, createOpenMessageIntent(contactKey))
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentTitle(name)
            .setContentText(alert)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert))
            .build()

    private fun createNewNodeSeenNotification(title: String, node: Node): Notification {
        val message = node.user.long_name
        return commonBuilder(NotificationChannelSpec.NewNodes, createOpenNodeDetailIntent(node.num))
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentTitle(title)
            .setWhen(nowMillis)
            .setShowWhen(true)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()
    }

    private suspend fun createLowBatteryNotification(node: Node, isRemote: Boolean): Notification {
        val type = if (isRemote) NotificationChannelSpec.LowBatteryRemote else NotificationChannelSpec.LowBattery
        val title = getStringSuspend(Res.string.low_battery_title, node.user.short_name)
        val batteryLevel = node.deviceMetrics.battery_level ?: 0
        val message = getStringSuspend(Res.string.low_battery_message, node.user.long_name, batteryLevel)

        // Not ongoing: an ongoing notification never bridges to a watch, and recovery cancels this one anyway.
        return commonBuilder(type, createOpenNodeDetailIntent(node.num))
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setProgress(MAX_BATTERY_LEVEL, batteryLevel, false)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setWhen(nowMillis)
            .setShowWhen(true)
            .build()
    }

    // endregion

    // region Helper/Builder Methods
    private val openAppIntent: PendingIntent by lazy {
        val intent =
            Intent(context, Class.forName(MAIN_ACTIVITY_CLASS)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Opens `meshtastic://meshtastic/[path]` in MainActivity, whose deep-link router synthesizes the backstack. Each
     * link is its own PendingIntent because the URI takes part in intent equality; [requestCode] only has to be stable.
     */
    private fun createDeepLinkIntent(path: String, requestCode: Int): PendingIntent {
        val deepLinkIntent =
            Intent(Intent.ACTION_VIEW, "$DEEP_LINK_BASE_URI/$path".toUri(), context, Class.forName(MAIN_ACTIVITY_CLASS))
                .apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(deepLinkIntent)
            checkNotNull(
                getPendingIntent(requestCode, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
    }

    private fun createOpenMessageIntent(contactKey: String) =
        createDeepLinkIntent("messages/$contactKey", contactKey.hashCode())

    private fun createOpenNodeDetailIntent(nodeNum: Int) = createDeepLinkIntent("nodes/$nodeNum", nodeNum)

    /**
     * Bubble target: [org.meshtastic.app.BubbleActivity], not the launcher activity. A bubble's activity must be
     * resizeable, embeddable and document-launched, and making the launcher activity document-launched would change how
     * the whole app behaves in recents.
     *
     * The icon is the same per-node avatar the conversation notification already builds, so a bubble is recognisable as
     * that conversation rather than as the app.
     */
    private fun createBubbleMetadata(contactKey: String, icon: IconCompat): NotificationCompat.BubbleMetadata {
        val deepLinkUri = "$DEEP_LINK_BASE_URI/messages/$contactKey".toUri()
        // Two halves of one rule. The intent carries no task flags, because the platform is the thing that applies
        // FLAG_ACTIVITY_NEW_DOCUMENT and FLAG_ACTIVITY_MULTIPLE_TASK for a bubble — setting them here instead
        // launches outside the bubble and collapses it. And the PendingIntent must be MUTABLE precisely so the
        // platform can add them; this is the documented exception to preferring FLAG_IMMUTABLE everywhere else.
        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri, context, Class.forName(BUBBLE_ACTIVITY_CLASS))
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                contactKey.hashCode(),
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.BubbleMetadata.Builder(pendingIntent, icon)
            .setDesiredHeight(BUBBLE_DESIRED_HEIGHT_DP)
            // Never auto-expand or suppress the shade entry: the user opts a conversation into bubbling, the app does
            // not decide to take over their screen because a packet arrived.
            .setAutoExpandBubble(false)
            .setSuppressNotification(false)
            .build()
    }

    /**
     * An explicit intent for [ConversationActionService]. The conversation rides in the data URI, which takes part in
     * PendingIntent identity, so two conversations never share one even if their request codes collide.
     */
    private fun conversationActionIntent(action: String, contactKey: String, packetId: Int? = null): Intent {
        val data =
            "$DEEP_LINK_BASE_URI/messages/$contactKey".toUri().buildUpon().apply {
                packetId?.let { appendQueryParameter("packet", it.toString()) }
            }
        return Intent(action, data.build(), context, ConversationActionService::class.java)
            .putExtra(ConversationActionService.EXTRA_CONTACT_KEY, contactKey)
    }

    private suspend fun createReplyAction(contactKey: String): NotificationCompat.Action {
        val replyLabel = getStringSuspend(Res.string.reply)
        val remoteInput = RemoteInput.Builder(ConversationActionService.KEY_TEXT_REPLY).setLabel(replyLabel).build()

        // Mutable so Android Auto and Wear can fill in the RemoteInput text; the intent is explicit, which a mutable
        // PendingIntent must be.
        val replyPendingIntent =
            PendingIntent.getService(
                context,
                contactKey.hashCode(),
                conversationActionIntent(ConversationActionService.ACTION_REPLY, contactKey),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, replyLabel, replyPendingIntent)
            .addRemoteInput(remoteInput)
            // Required for Android Auto to drive reply hands-free without opening any UI.
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            // Offers Smart Reply suggestions when the notification is bridged to a Wear OS watch.
            .setAllowGeneratedReplies(true)
            .build()
    }

    private suspend fun createMarkAsReadAction(contactKey: String): NotificationCompat.Action {
        val label = getStringSuspend(Res.string.mark_as_read)
        val pendingIntent =
            PendingIntent.getService(
                context,
                contactKey.hashCode(),
                conversationActionIntent(ConversationActionService.ACTION_MARK_AS_READ, contactKey),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Action.Builder(android.R.drawable.ic_menu_view, label, pendingIntent)
            // Required for Android Auto to mark a conversation read hands-free without opening any UI.
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun createReactionAction(contactKey: String, packetId: Int): NotificationCompat.Action {
        val label = THUMBS_UP
        val intent =
            conversationActionIntent(ConversationActionService.ACTION_REACT, contactKey, packetId)
                .putExtra(ConversationActionService.EXTRA_REPLY_ID, packetId)
                .putExtra(ConversationActionService.EXTRA_EMOJI, THUMBS_UP)
        val pendingIntent =
            PendingIntent.getService(
                context,
                packetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Action.Builder(android.R.drawable.ic_menu_add, label, pendingIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_UP)
            .setShowsUserInterface(false)
            .build()
    }

    private fun snippet(text: String): String =
        if (text.length > SNIPPET_LENGTH) text.take(SNIPPET_LENGTH).trimEnd() + "…" else text

    private fun commonBuilder(
        type: NotificationChannelSpec,
        contentIntent: PendingIntent? = null,
    ): NotificationCompat.Builder {
        val smallIcon = drawable.meshtastic_ic_notification

        return NotificationCompat.Builder(context, type.id)
            .setSmallIcon(smallIcon)
            .setColor(NOTIFICATION_COLOR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent ?: openAppIntent)
    }

    // endregion

    // region Extension Functions (Localized)

    private suspend fun LocalStats.formatToStringSuspend(batteryLevel: Int? = null): String {
        val parts = mutableListOf<String>()
        batteryLevel?.let {
            if (it > MAX_BATTERY_LEVEL) {
                parts.add(BULLET + getStringSuspend(Res.string.powered))
            } else {
                parts.add(BULLET + getStringSuspend(Res.string.local_stats_battery, it))
            }
        }
        parts.add(BULLET + getStringSuspend(Res.string.local_stats_nodes, num_online_nodes, num_total_nodes))
        val uptime = formatDurationSuspend(uptime_seconds.toLong())
        parts.add(BULLET + getStringSuspend(Res.string.local_stats_uptime, uptime))
        parts.add(
            BULLET +
                getStringSuspend(
                    Res.string.local_stats_utilization,
                    NumberFormatter.format(channel_utilization.toDouble(), 2),
                    NumberFormatter.format(air_util_tx.toDouble(), 2),
                ),
        )

        if (heap_free_bytes > 0 || heap_total_bytes > 0) {
            parts.add(
                BULLET +
                    getStringSuspend(Res.string.local_stats_heap) +
                    ": " +
                    getStringSuspend(Res.string.local_stats_heap_value, heap_free_bytes, heap_total_bytes),
            )
        }

        // Traffic Stats
        if (num_packets_tx > 0 || num_packets_rx > 0) {
            parts.add(
                BULLET + getStringSuspend(Res.string.local_stats_traffic, num_packets_tx, num_packets_rx, num_rx_dupe),
            )
        }
        if (num_tx_relay > 0) {
            parts.add(BULLET + getStringSuspend(Res.string.local_stats_relays, num_tx_relay, num_tx_relay_canceled))
        }

        // Diagnostic Fields
        val diagnosticParts = mutableListOf<String>()
        val noiseFloor = noiseFloorOrNull
        if (noiseFloor != null) diagnosticParts.add(getStringSuspend(Res.string.local_stats_noise, noiseFloor))
        if (num_packets_rx_bad > 0) {
            diagnosticParts.add(getStringSuspend(Res.string.local_stats_bad, num_packets_rx_bad))
        }
        if (num_tx_dropped > 0) diagnosticParts.add(getStringSuspend(Res.string.local_stats_dropped, num_tx_dropped))

        if (diagnosticParts.isNotEmpty()) {
            parts.add(
                BULLET +
                    getStringSuspend(Res.string.local_stats_diagnostics_prefix, diagnosticParts.joinToString(" | ")),
            )
        }

        return parts.joinToString("\n")
    }

    private suspend fun DeviceMetrics.formatToStringSuspend(): String {
        val parts = mutableListOf<String>()
        battery_level?.let { parts.add(BULLET + getStringSuspend(Res.string.local_stats_battery, it)) }
        uptime_seconds?.let {
            parts.add(BULLET + getStringSuspend(Res.string.local_stats_uptime, formatDurationSuspend(it.toLong())))
        }
        if (channel_utilization != null || air_util_tx != null) {
            parts.add(
                BULLET +
                    getStringSuspend(
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

/**
 * The notification title for the most significant operation in flight, or null when none is.
 *
 * Firmware work outranks a discovery scan: it is the one the user must not interrupt.
 */
internal fun Set<RadioOperation>.notificationTitleResource(): StringResource? = when {
    isEmpty() -> null

    contains(RadioOperation.FirmwareUpdate) || contains(RadioOperation.FirmwareMaintenance) ->
        Res.string.firmware_update_in_progress

    else -> Res.string.discovery_scan_in_progress
}
