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

import androidx.car.app.CarAppApiLevels
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository

/**
 * Root screen displayed in Android Auto.
 *
 * Renders a three-tab UI:
 * - **Status** — Connection state and device name.
 * - **Favorites** — All nodes the user has starred, with online/hop status shown as a subtitle.
 * - **Messages** — All conversations: active channels displayed first as permanent placeholders
 *   (always visible even when empty, sorted by channel index), followed by DM conversations
 *   sorted by most-recent message descending. This is the same ordering used by
 *   [org.meshtastic.feature.messaging.ui.contact.ContactsViewModel].
 *
 * Pure business-logic (contact ordering, row text, favourites sorting) is separated into
 * [CarScreenDataBuilder], which is free of Car App Library dependencies and is unit-tested
 * independently.
 *
 * `TabTemplate` requires Car API level 6. On hosts running Car API level 1–5 the screen falls
 * back to a single [ListTemplate] that includes a status row, favorite-node rows, and the
 * contact list.
 *
 * When the user taps a [MessagingStyle][androidx.core.app.NotificationCompat.MessagingStyle]
 * notification in the Android Auto notification shade the host calls
 * [MeshtasticCarSession.onNewIntent] which delegates to [selectContactKey] to switch to the
 * Messages tab.
 */
class MeshtasticCarScreen(carContext: CarContext) :
    Screen(carContext),
    KoinComponent,
    DefaultLifecycleObserver {

    private val nodeRepository: NodeRepository by inject()
    private val packetRepository: PacketRepository by inject()
    private val radioConfigRepository: RadioConfigRepository by inject()
    private val serviceRepository: ServiceRepository by inject()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Per-host list item cap, retrieved once on first template render via
     * [ConstraintManager.getContentLimit]. Replaces a hardcoded constant so that
     * hosts that allow more than the minimum 5 items are fully utilised.
     */
    private val listContentLimit: Int by lazy {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }

    private var activeTabId = TAB_STATUS
    private var connectionState: ConnectionState = ConnectionState.Disconnected

    /**
     * Favorite nodes sorted alphabetically by long name. Updated reactively from
     * [NodeRepository.nodeDBbyNum] whenever the user stars or un-stars a node.
     */
    private var favorites: List<Node> = emptyList()

    /**
     * Ordered contact list for the Messages tab: channel entries first (sorted by channel index,
     * always present as placeholders even when no messages exist), then DM conversations sorted
     * by most-recent message descending — identical ordering to the phone's Contacts screen.
     */
    private var contacts: List<CarContact> = emptyList()

    /**
     * True until the first combined emission from all repository flows arrives.
     *
     * On Car API ≥ 5 this prevents a flash of empty content before data loads by showing a
     * [MessageTemplate] loading spinner. On older API levels the loading spinner is unavailable
     * so [isLoading] starts as `false` and the fallback [ListTemplate] handles the empty state
     * with [ItemList.Builder.setNoItemsMessage].
     */
    private var isLoading = carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_5

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        startObserving()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        scope.cancel()
    }

    private fun startObserving() {
        // Build the contacts sub-flow independently so it can feed the outer combine below.
        val contactsFlow = combine(
            nodeRepository.myId,
            packetRepository.getContacts(),
            radioConfigRepository.channelSetFlow,
        ) { myId, rawContacts, channelSet ->
            // Channel placeholders are always included so every configured channel is
            // visible even before any messages have been sent/received.
            val placeholders = CarScreenDataBuilder.buildChannelPlaceholders(channelSet)
            // Real DB entries take precedence over placeholders when present.
            val merged = rawContacts + (placeholders - rawContacts.keys)
            CarScreenDataBuilder.buildCarContacts(
                merged, myId, channelSet,
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
                    val unreadFlows = baseContacts.map { contact ->
                        packetRepository.getUnreadCountFlow(contact.contactKey)
                            .map { unread -> contact.copy(unreadCount = unread) }
                    }
                    combine(unreadFlows) { it.toList() }
                }
            }

        val favoritesFlow = nodeRepository.nodeDBbyNum
            .map { db -> CarScreenDataBuilder.sortFavorites(db.values) }
            .distinctUntilChanged()

        // All three data sources feed a single combined collector so that each batch of
        // repository changes produces exactly one invalidate() call, avoiding unnecessary
        // template rebuilds and staying well within the host's update-rate budget.
        scope.launch {
            combine(
                serviceRepository.connectionState,
                favoritesFlow,
                contactsFlow,
            ) { connState, favs, ctcts -> Triple(connState, favs, ctcts) }
                .collect { (connState, favs, ctcts) ->
                    connectionState = connState
                    favorites = favs
                    contacts = ctcts
                    isLoading = false
                    invalidate()
                }
        }
    }

    // ---- Template building ----

    override fun onGetTemplate(): Template {
        // MessageTemplate.setLoading() requires Car API 5+. isLoading is only ever true
        // on ≥5 hosts (see initialisation above), so no additional level check is needed here.
        if (isLoading) {
            return MessageTemplate.Builder(carContext.getString(R.string.auto_loading))
                .setHeaderAction(Action.APP_ICON)
                .setLoading(true)
                .build()
        }

        // TabTemplate requires Car API level 6. Fall back to a combined ListTemplate
        // on older hosts so the app remains functional on all supported vehicles.
        if (carContext.carAppApiLevel < CarAppApiLevels.LEVEL_6) {
            return buildFallbackListTemplate()
        }

        val tabCallback = object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) {
                activeTabId = tabContentId
                invalidate()
            }
        }

        val activeContent = when (activeTabId) {
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
     * Called by [MeshtasticCarSession.onNewIntent] when the user taps a conversation notification
     * in the Android Auto notification shade. Switches to [TAB_MESSAGES] regardless of whether
     * the originating contact is a channel broadcast or a DM, because both appear in the same tab.
     *
     * The [contactKey] parameter is accepted for API symmetry with the session and may be used in
     * the future to scroll the Messages list to the tapped conversation.
     */
    fun selectContactKey(@Suppress("UNUSED_PARAMETER") contactKey: String) {
        activeTabId = TAB_MESSAGES
        invalidate()
    }

    // ---- Individual template builders ----

    private fun buildStatusTemplate(): ListTemplate =
        ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.auto_tab_status))
            .setSingleList(ItemList.Builder().addItem(buildStatusRow()).build())
            .build()

    /**
     * Builds the Favorites tab: one row per starred node, mirroring the key status info shown
     * by [org.meshtastic.feature.node.component.NodeItem] on the phone.
     *
     * - **Title**: node's long name (short name fallback).
     * - **Text 1**: `"Online · Direct"` / `"Online · N hops"` / `"Offline · Xh ago"` —
     *   mirrors the signal row and last-heard chip in NodeItem.
     * - **Text 2**: battery percentage and short name — mirrors the battery row and node chip.
     */
    private fun buildFavoritesTemplate(): ListTemplate =
        buildListTemplate(
            carContext.getString(R.string.auto_tab_favorites),
            favorites,
            carContext.getString(R.string.auto_no_favorites),
        ) { buildFavoriteNodeRow(it) }

    /**
     * Builds the Messages tab content: channels first (always present, even if empty), followed
     * by DM conversations sorted by most-recent message — identical to the phone's Contacts screen.
     */
    private fun buildMessagesTemplate(): ListTemplate =
        buildListTemplate(
            carContext.getString(R.string.auto_tab_messages),
            contacts,
            carContext.getString(R.string.auto_no_conversations),
        ) { buildContactRow(it) }

    /**
     * Fallback for Car API level 1–5 hosts that do not support [TabTemplate].
     *
     * Shows a status row, then favorite-node rows, then conversation rows, all capped at
     * [listContentLimit] total — matching the three-tab content in a single list.
     *
     * The remaining slots after status are split evenly: half for favorites, half for messages.
     * This prevents a long favorites list from crowding out all conversation entries.
     */
    private fun buildFallbackListTemplate(): ListTemplate {
        val items = ItemList.Builder()
        var remaining = listContentLimit
        items.addItem(buildStatusRow())
        remaining--
        // Give each section at most half the remaining space so neither dominates.
        val halfRemaining = remaining / 2
        favorites.take(halfRemaining).forEach { node ->
            items.addItem(buildFavoriteNodeRow(node))
            remaining--
        }
        contacts.take(remaining).forEach { contact ->
            items.addItem(buildContactRow(contact))
        }
        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.auto_fallback_title))
            .setSingleList(items.build())
            .build()
    }

    private fun buildStatusRow(): Row {
        val statusText = when (connectionState) {
            is ConnectionState.Connected -> carContext.getString(R.string.auto_status_connected)
            is ConnectionState.Disconnected -> carContext.getString(R.string.auto_status_disconnected)
            is ConnectionState.DeviceSleep -> carContext.getString(R.string.auto_status_sleeping)
            is ConnectionState.Connecting -> carContext.getString(R.string.auto_status_connecting)
        }
        val deviceName = nodeRepository.ourNodeInfo.value?.user?.long_name.orEmpty()
        return Row.Builder()
            .setTitle(statusText)
            .addTextIfNotEmpty(deviceName)
            .setBrowsable(false)
            .build()
    }

    /**
     * Builds a single favorite-node row.
     *
     * Mirrors the content of [org.meshtastic.feature.node.component.NodeItem]:
     * - Title  → `long_name` (prominent, matches NodeItem header text)
     * - Text 1 → online/offline + hop distance (matches NodeItem signal row)
     * - Text 2 → battery level + short name chip equivalent (matches NodeItem battery row)
     */
    private fun buildFavoriteNodeRow(node: Node): Row {
        val name = node.user.long_name.ifEmpty { node.user.short_name }
            .ifEmpty { carContext.getString(R.string.auto_unknown) }
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
     * - **Title**  → channel or DM display name (matches the bodyLarge name in ContactHeader).
     * - **Text 1** → last message preview with sender prefix for received DMs, or "No messages
     *   yet" for empty channel placeholders (matches ChatMetadata's message text).
     * - **Text 2** → `"N unread"` when there are unread messages, or the last-message timestamp
     *   when there are none (matches the unread badge and date in ContactHeader/ChatMetadata).
     */
    private fun buildContactRow(contact: CarContact): Row =
        Row.Builder()
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

    private fun carIcon(resId: Int) =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).setTint(CarColor.DEFAULT).build()

    /** Adds [text] as a new text line only when it is non-empty, avoiding blank Car UI rows. */
    private fun Row.Builder.addTextIfNotEmpty(text: String): Row.Builder =
        apply { if (text.isNotEmpty()) addText(text) }

    /**
     * DRY helper: builds a [ListTemplate] from a list of items, capping at [listContentLimit]
     * (the per-host limit reported by [ConstraintManager]).
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

    companion object {
        private const val TAB_STATUS = "status"
        private const val TAB_FAVORITES = "favorites"
        private const val TAB_MESSAGES = "messages"
    }
}
