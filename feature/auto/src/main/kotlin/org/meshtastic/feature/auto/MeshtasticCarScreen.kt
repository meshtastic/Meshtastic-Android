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
package org.meshtastic.feature.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.messaging.model.CarMessage
import androidx.car.app.messaging.model.ConversationCallback
import androidx.car.app.messaging.model.ConversationItem
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase

/**
 * Root screen displayed in Android Auto.
 *
 * Renders a three-tab UI:
 * - **Status** — Connection state, device name, and local device stats (battery, channel/air utilization, online nodes,
 *   uptime, traffic) — the same key metrics surfaced by the home-screen Local Stats widget.
 * - **Favorites** — All nodes the user has starred, with online/hop status shown as a subtitle.
 * - **Messages** — All conversations rendered as [ConversationItem]s (Car API 7+), providing built-in play, reply, and
 *   mark-as-read affordances. Channels are displayed first as permanent placeholders (always visible even when empty,
 *   sorted by channel index), followed by DM conversations sorted by most-recent message descending. This is the same
 *   ordering used by [org.meshtastic.feature.messaging.ui.contact.ContactsViewModel].
 *
 * Pure business-logic (contact ordering, row text, favourites sorting) is separated into [CarScreenDataBuilder], which
 * is free of Car App Library dependencies and is unit-tested independently.
 *
 * The `minCarApiLevel` is set to 7 in the manifest, ensuring [TabTemplate] (API 6) and [ConversationItem] (API 7) are
 * always available. Hosts running older API levels still provide the notification-based messaging experience.
 *
 * When the user taps a [MessagingStyle][androidx.core.app.NotificationCompat.MessagingStyle] notification in the
 * Android Auto notification shade the host calls [MeshtasticCarSession.onNewIntent] which delegates to
 * [selectMessagesTab] to switch to the Messages tab.
 */
@Suppress("TooManyFunctions")
class MeshtasticCarScreen(carContext: CarContext) :
    Screen(carContext),
    KoinComponent,
    DefaultLifecycleObserver {

    private val nodeRepository: NodeRepository by inject()
    private val packetRepository: PacketRepository by inject()
    private val radioConfigRepository: RadioConfigRepository by inject()
    private val serviceRepository: ServiceRepository by inject()
    private val dispatchers: CoroutineDispatchers by inject()
    private val sendMessageUseCase: SendMessageUseCase by inject()
    private val meshServiceNotifications: MeshServiceNotifications by inject()

    private val scope by lazy { CoroutineScope(dispatchers.main + SupervisorJob()) }

    /** Durable scope for reply/mark-as-read callbacks that must survive screen invalidations. */
    private val callbackScope by lazy { CoroutineScope(dispatchers.io + SupervisorJob()) }

    /**
     * Per-host list item cap, retrieved once on first template render via [ConstraintManager.getContentLimit]. Replaces
     * a hardcoded constant so that hosts that allow more than the minimum 5 items are fully utilised.
     */
    private val listContentLimit: Int by lazy {
        carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }

    private var activeTabId = TAB_STATUS
    private var connectionState: ConnectionState = ConnectionState.Disconnected

    /**
     * Local device statistics for the Status tab — battery, utilization, nodes, uptime. Mirrors the key metrics shown
     * by the home-screen Local Stats widget.
     */
    private var localStats: CarLocalStats = CarLocalStats()

    /**
     * Favorite nodes sorted alphabetically by long name. Updated reactively from [NodeRepository.nodeDBbyNum] whenever
     * the user stars or un-stars a node.
     */
    private var favorites: List<Node> = emptyList()

    /**
     * Ordered contact list for the Messages tab: channel entries first (sorted by channel index, always present as
     * placeholders even when no messages exist), then DM conversations sorted by most-recent message descending —
     * identical ordering to the phone's Contacts screen.
     */
    private var contacts: List<CarContact> = emptyList()

    /**
     * True until the first combined emission from all repository flows arrives.
     *
     * Car API 7+ supports [MessageTemplate.setLoading] so we show a loading spinner instead of a flash of empty content
     * before data loads.
     */
    private var isLoading = true

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        startObserving()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        scope.cancel()
        callbackScope.cancel()
    }

    @Suppress("LongMethod")
    private fun startObserving() {
        // Build the contacts sub-flow independently so it can feed the outer combine below.
        val contactsFlow =
            combine(nodeRepository.myId, packetRepository.getContacts(), radioConfigRepository.channelSetFlow) {
                    myId,
                    rawContacts,
                    channelSet,
                ->
                // Channel placeholders are always included so every configured channel is
                // visible even before any messages have been sent/received.
                val placeholders = CarScreenDataBuilder.buildChannelPlaceholders(channelSet)
                // Real DB entries take precedence over placeholders when present.
                val merged = rawContacts + (placeholders - rawContacts.keys)
                CarScreenDataBuilder.buildCarContacts(
                    merged,
                    myId,
                    channelSet,
                    resolveUser = { userId -> nodeRepository.getUser(userId) },
                    channelLabel = { carContext.getString(R.string.auto_channel_number, it) },
                    unknownLabel = carContext.getString(R.string.auto_unknown),
                )
            }
                .distinctUntilChanged()
                .flatMapLatest { baseContacts ->
                    if (baseContacts.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        val unreadFlows =
                            baseContacts.map { contact ->
                                packetRepository.getUnreadCountFlow(contact.contactKey).map { unread ->
                                    contact.copy(unreadCount = unread)
                                }
                            }
                        combine(unreadFlows) { it.toList() }
                    }
                }

        val favoritesFlow =
            nodeRepository.nodeDBbyNum
                .map { db -> CarScreenDataBuilder.sortFavorites(db.values) }
                .distinctUntilChanged()

        val localStatsFlow =
            combine(nodeRepository.ourNodeInfo, nodeRepository.localStats, nodeRepository.nodeDBbyNum) {
                    ourNode,
                    stats,
                    nodeDb,
                ->
                CarScreenDataBuilder.buildLocalStats(ourNode, stats, nodeDb.values)
            }
                .distinctUntilChanged()

        // All data sources feed a single combined collector so that each batch of
        // repository changes produces exactly one invalidate() call, avoiding unnecessary
        // template rebuilds and staying well within the host's update-rate budget.
        scope.launch {
            combine(serviceRepository.connectionState, favoritesFlow, contactsFlow, localStatsFlow) {
                    connState,
                    favs,
                    ctcts,
                    stats,
                ->
                CombinedState(connState, favs, ctcts, stats)
            }
                .collect { state ->
                    connectionState = state.connectionState
                    favorites = state.favorites
                    contacts = state.contacts
                    localStats = state.localStats
                    isLoading = false
                    invalidate()
                }
        }
    }

    // ---- Template building ----

    override fun onGetTemplate(): Template {
        if (isLoading) {
            return MessageTemplate.Builder(carContext.getString(R.string.auto_loading))
                .setHeaderAction(Action.APP_ICON)
                .setLoading(true)
                .build()
        }

        val tabCallback =
            object : TabTemplate.TabCallback {
                override fun onTabSelected(tabContentId: String) {
                    activeTabId = tabContentId
                    invalidate()
                }
            }

        val activeContent =
            when (activeTabId) {
                TAB_FAVORITES -> TabContents.Builder(buildFavoritesTemplate()).build()
                TAB_MESSAGES -> TabContents.Builder(buildMessagesTemplate()).build()
                else -> TabContents.Builder(buildStatusTemplate()).build()
            }

        return TabTemplate.Builder(tabCallback)
            .setHeaderAction(Action.APP_ICON)
            .addTab(
                Tab.Builder()
                    .setTitle(carContext.getString(R.string.auto_tab_status))
                    .setIcon(carIcon(R.drawable.auto_ic_status))
                    .setContentId(TAB_STATUS)
                    .build(),
            )
            .addTab(
                Tab.Builder()
                    .setTitle(carContext.getString(R.string.auto_tab_favorites))
                    .setIcon(carIcon(R.drawable.auto_ic_favorites))
                    .setContentId(TAB_FAVORITES)
                    .build(),
            )
            .addTab(
                Tab.Builder()
                    .setTitle(carContext.getString(R.string.auto_tab_messages))
                    .setIcon(carIcon(R.drawable.auto_ic_channels))
                    .setContentId(TAB_MESSAGES)
                    .build(),
            )
            .setTabContents(activeContent)
            .setActiveTabContentId(activeTabId)
            .build()
    }

    /**
     * Called by [MeshtasticCarSession.onNewIntent] when the user taps a conversation notification in the Android Auto
     * notification shade. Switches to [TAB_MESSAGES] — channels and DMs both live in the same tab, so no per-key
     * handling is required.
     *
     * `androidx.car.app.model.ListTemplate` does not currently expose a programmatic scroll API, so we cannot focus a
     * specific conversation row. If/when a scroll API is added, the contactKey can be threaded through
     * `MeshtasticCarSession.onNewIntent`.
     */
    fun selectMessagesTab() {
        activeTabId = TAB_MESSAGES
        invalidate()
    }

    // ---- Individual template builders ----

    /**
     * Builds the Status tab: connection state + local device stats mirroring the home-screen Local Stats widget
     * (battery, channel/air utilization, node counts, uptime, traffic).
     */
    private fun buildStatusTemplate(): ListTemplate {
        val items = ItemList.Builder()
        items.addItem(buildStatusRow())
        buildLocalStatsRows().forEach { items.addItem(it) }
        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.auto_tab_status))
            .setSingleList(items.build())
            .build()
    }

    /**
     * Builds the Favorites tab: one row per starred node, mirroring the key status info shown by
     * [org.meshtastic.feature.node.component.NodeItem] on the phone.
     * - **Title**: node's long name (short name fallback).
     * - **Text 1**: `"Online · Direct"` / `"Online · N hops"` / `"Offline · Xh ago"` — mirrors the signal row and
     *   last-heard chip in NodeItem.
     * - **Text 2**: battery percentage and short name — mirrors the battery row and node chip.
     */
    private fun buildFavoritesTemplate(): ListTemplate = buildListTemplate(
        carContext.getString(R.string.auto_tab_favorites),
        favorites,
        carContext.getString(R.string.auto_no_favorites),
    ) {
        buildFavoriteNodeRow(it)
    }

    /**
     * Builds the Messages tab content using [ConversationItem]s for conversations that have at least one message, and
     * plain [Row]s for empty channel placeholders. This provides built-in play, reply, and mark-as-read affordances for
     * active conversations.
     */
    private fun buildMessagesTemplate(): ListTemplate {
        val listBuilder = ItemList.Builder()
        val capped = contacts.take(listContentLimit)
        if (capped.isEmpty()) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.auto_no_conversations))
        } else {
            val selfPerson = buildSelfPerson()
            capped.forEach { contact ->
                if (contact.lastMessageRawText != null) {
                    listBuilder.addItem(buildConversationItem(contact, selfPerson))
                } else {
                    listBuilder.addItem(buildContactRow(contact))
                }
            }
        }
        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.auto_tab_messages))
            .setSingleList(listBuilder.build())
            .build()
    }

    private fun buildStatusRow(): Row {
        val statusText =
            when (connectionState) {
                is ConnectionState.Connected -> carContext.getString(R.string.auto_status_connected)
                is ConnectionState.Disconnected -> carContext.getString(R.string.auto_status_disconnected)
                is ConnectionState.DeviceSleep -> carContext.getString(R.string.auto_status_sleeping)
                is ConnectionState.Connecting -> carContext.getString(R.string.auto_status_connecting)
            }
        val deviceName = nodeRepository.ourNodeInfo.value?.user?.long_name.orEmpty()
        return Row.Builder().setTitle(statusText).addTextIfNotEmpty(deviceName).setBrowsable(false).build()
    }

    /**
     * Builds rows for local device statistics — the same key metrics the home-screen widget surfaces: battery,
     * channel/air utilization, online nodes, uptime, and packet traffic.
     *
     * Only shown when connected ([CarLocalStats.hasBattery] is a proxy for "device metrics received"). Returns an empty
     * list when disconnected.
     */
    @Suppress("MagicNumber")
    private fun buildLocalStatsRows(): List<Row> {
        val s = localStats
        if (!s.hasBattery && s.totalNodes == 0) return emptyList()
        val rows = mutableListOf<Row>()

        if (s.hasBattery) {
            val batteryValue =
                if (s.batteryLevel > 100) {
                    carContext.getString(R.string.auto_stats_powered)
                } else {
                    "${s.batteryLevel}%"
                }
            rows +=
                Row.Builder()
                    .setTitle(carContext.getString(R.string.auto_stats_battery, batteryValue))
                    .addText(
                        carContext.getString(
                            R.string.auto_stats_channel_util,
                            "%.1f%%".format(s.channelUtilization),
                            "%.1f%%".format(s.airUtilization),
                        ),
                    )
                    .setBrowsable(false)
                    .build()
        }

        rows +=
            Row.Builder()
                .setTitle(carContext.getString(R.string.auto_stats_nodes, s.onlineNodes, s.totalNodes))
                .addTextIfNotEmpty(
                    if (s.uptimeSeconds > 0) {
                        carContext.getString(R.string.auto_stats_uptime, formatUptime(s.uptimeSeconds))
                    } else {
                        ""
                    },
                )
                .setBrowsable(false)
                .build()

        if (s.numPacketsTx > 0 || s.numPacketsRx > 0) {
            rows +=
                Row.Builder()
                    .setTitle(
                        carContext.getString(R.string.auto_stats_traffic, s.numPacketsTx, s.numPacketsRx, s.numRxDupe),
                    )
                    .setBrowsable(false)
                    .build()
        }

        return rows
    }

    /**
     * Builds a single favorite-node row.
     *
     * Mirrors the content of [org.meshtastic.feature.node.component.NodeItem]:
     * - Title → `long_name` (prominent, matches NodeItem header text)
     * - Text 1 → online/offline + hop distance (matches NodeItem signal row)
     * - Text 2 → battery level + short name chip equivalent (matches NodeItem battery row)
     */
    private fun buildFavoriteNodeRow(node: Node): Row {
        val name =
            node.user.long_name.ifEmpty { node.user.short_name }.ifEmpty { carContext.getString(R.string.auto_unknown) }
        return Row.Builder()
            .setTitle(name)
            .addText(
                CarScreenDataBuilder.nodeStatusText(
                    node,
                    labelOnline = carContext.getString(R.string.auto_node_online),
                    labelOffline = carContext.getString(R.string.auto_node_offline),
                    labelDirect = carContext.getString(R.string.auto_node_direct),
                    labelHops = { carContext.getString(R.string.auto_node_hops, it) },
                ),
            )
            .addTextIfNotEmpty(CarScreenDataBuilder.nodeDetailText(node))
            .setBrowsable(false)
            .build()
    }

    /**
     * Builds a single conversation row.
     *
     * Mirrors [org.meshtastic.feature.messaging.ui.contact.ContactItem]:
     * - **Title** → channel or DM display name (matches the bodyLarge name in ContactHeader).
     * - **Text 1** → last message preview with sender prefix for received DMs, or "No messages yet" for empty channel
     *   placeholders (matches ChatMetadata's message text).
     * - **Text 2** → `"N unread"` when there are unread messages, or the last-message timestamp when there are none
     *   (matches the unread badge and date in ContactHeader/ChatMetadata).
     */
    private fun buildContactRow(contact: CarContact): Row = Row.Builder()
        .setTitle(contact.displayName)
        .addText(
            CarScreenDataBuilder.contactPreviewText(
                contact,
                noMessagesLabel = carContext.getString(R.string.auto_no_messages),
            ),
        )
        .addTextIfNotEmpty(
            CarScreenDataBuilder.contactSecondaryText(
                contact,
                unreadLabel = { carContext.getString(R.string.auto_unread_count, it) },
            ),
        )
        .setBrowsable(false)
        .build()

    /**
     * Builds a [ConversationItem] for a contact that has at least one message.
     *
     * The item links to the conversation's shortcut ID (matching [ConversationShortcutManager]) so the host can
     * associate notifications with the template entry.
     */
    private fun buildConversationItem(contact: CarContact, selfPerson: Person): ConversationItem {
        val senderPerson =
            if (contact.lastMessageFromSelf) {
                selfPerson
            } else {
                Person.Builder().setName(contact.lastMessageSenderName ?: contact.displayName).build()
            }

        val carMessage =
            CarMessage.Builder()
                .setSender(senderPerson)
                .setBody(CarText.Builder(contact.lastMessageRawText ?: "").build())
                .setReceivedTimeEpochMillis(contact.lastMessageTime ?: 0L)
                .setRead(contact.unreadCount == 0)
                .build()

        val messages = listOf(carMessage)
        val callback = buildConversationCallback(contact.contactKey)

        return ConversationItem.Builder(
            /* id = */
            contact.contactKey,
            /* title = */
            CarText.Builder(contact.displayName).build(),
            /* self = */
            selfPerson,
            /* messages = */
            messages,
            /* conversationCallback = */
            callback,
        )
            .setGroupConversation(contact.isBroadcast)
            .build()
    }

    private fun buildSelfPerson(): Person {
        val ourName = nodeRepository.ourNodeInfo.value?.user?.long_name ?: "Me"
        return Person.Builder().setName(ourName).build()
    }

    /**
     * Creates a [ConversationCallback] for the given contact that handles reply and mark-as-read actions. Uses
     * [callbackScope] (IO-backed, durable) so that in-flight replies are not cancelled by screen invalidations.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun buildConversationCallback(contactKey: String): ConversationCallback = object : ConversationCallback {
        override fun onMarkAsRead() {
            callbackScope.launch {
                try {
                    meshServiceNotifications.markConversationRead(contactKey)
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to mark conversation $contactKey as read" }
                }
            }
        }

        override fun onTextReply(replyText: String) {
            callbackScope.launch {
                try {
                    sendMessageUseCase(replyText, contactKey)
                    meshServiceNotifications.appendOutgoingMessage(contactKey, replyText)
                    meshServiceNotifications.markConversationRead(contactKey)
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to send reply to $contactKey" }
                }
            }
        }
    }

    private fun carIcon(resId: Int) =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).setTint(CarColor.DEFAULT).build()

    /** Adds [text] as a new text line only when it is non-empty, avoiding blank Car UI rows. */
    private fun Row.Builder.addTextIfNotEmpty(text: String): Row.Builder = apply {
        if (text.isNotEmpty()) addText(text)
    }

    /**
     * DRY helper: builds a [ListTemplate] from a list of items, capping at [listContentLimit] (the per-host limit
     * reported by [ConstraintManager]).
     *
     * Shows [noItemsMessage] when the list is empty.
     */
    private fun <T> buildListTemplate(
        title: String,
        items: List<T>,
        noItemsMessage: String,
        buildRow: (T) -> Row,
    ): ListTemplate {
        val listBuilder = ItemList.Builder()
        val capped = items.take(listContentLimit)
        if (capped.isEmpty()) {
            listBuilder.setNoItemsMessage(noItemsMessage)
        } else {
            capped.forEach { listBuilder.addItem(buildRow(it)) }
        }
        return ListTemplate.Builder().setTitle(title).setSingleList(listBuilder.build()).build()
    }

    private data class CombinedState(
        val connectionState: ConnectionState,
        val favorites: List<Node>,
        val contacts: List<CarContact>,
        val localStats: CarLocalStats,
    )

    companion object {
        private const val TAB_STATUS = "status"
        private const val TAB_FAVORITES = "favorites"
        private const val TAB_MESSAGES = "messages"
    }
}
