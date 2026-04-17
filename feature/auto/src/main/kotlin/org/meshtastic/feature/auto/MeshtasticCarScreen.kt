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
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.getChannel
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.PortNum

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
     * True until the first [collect] emission arrives from the repository flows, preventing a
     * flash of an empty/disconnected screen on Car API ≥ 5 hosts.
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
    }

    private fun startObserving() {
        // Observe the contact list (channels + DMs) with reactive unread counts.
        scope.launch {
            combine(
                nodeRepository.myId,
                packetRepository.getContacts(),
                radioConfigRepository.channelSetFlow,
            ) { myId, rawContacts, channelSet ->
                // Channel placeholders are always included so every configured channel is
                // visible even before any messages have been sent/received — mirroring the
                // behaviour of ContactsViewModel.contactList.
                val placeholders = buildChannelPlaceholders(channelSet)
                // Real DB entries take precedence over placeholders when present.
                val merged = rawContacts + (placeholders - rawContacts.keys)
                buildCarContacts(merged, myId, channelSet)
            }
                .distinctUntilChanged()
                .flatMapLatest { baseContacts ->
                    if (baseContacts.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        val unreadFlows =
                            baseContacts.map { contact ->
                                packetRepository.getUnreadCountFlow(contact.contactKey)
                                    .map { unread -> contact.copy(unreadCount = unread) }
                            }
                        combine(unreadFlows) { it.toList() }
                    }
                }
                .collect { updated ->
                    contacts = updated
                    isLoading = false
                    invalidate()
                }
        }

        // Connection state is observed separately since it only affects the Status tab.
        scope.launch {
            serviceRepository.connectionState.collect { state ->
                connectionState = state
                invalidate()
            }
        }

        // Favorite nodes — filter nodeDBbyNum to isFavorite, sort alphabetically.
        scope.launch {
            nodeRepository.nodeDBbyNum
                .map { db -> db.values.filter { it.isFavorite }.sortedBy { it.user.long_name.ifEmpty { it.user.short_name } } }
                .distinctUntilChanged()
                .collect { nodes ->
                    favorites = nodes
                    invalidate()
                }
        }
    }

    /** Returns a map of `"<ch>^all" → placeholder DataPacket` for every configured channel. */
    private fun buildChannelPlaceholders(channelSet: ChannelSet): Map<String, DataPacket> =
        (0 until channelSet.settings.size).associate { ch ->
            // dataType uses PortNum.TEXT_MESSAGE_APP (value 1) to match the placeholder
            // construction in ContactsViewModel and PacketRepository contact queries.
            "${ch}${DataPacket.ID_BROADCAST}" to
                DataPacket(bytes = null, dataType = PortNum.TEXT_MESSAGE_APP.value, time = 0L, channel = ch)
        }

    /**
     * Converts the merged DB + placeholder map into an ordered [CarContact] list.
     *
     * Channels (keys ending with [DataPacket.ID_BROADCAST]) appear first sorted by channel index.
     * DM conversations follow sorted by [CarContact.lastMessageTime] descending — matching the
     * ordering used by the phone's Contacts screen.
     */
    private fun buildCarContacts(
        merged: Map<String, DataPacket>,
        myId: String?,
        channelSet: ChannelSet,
    ): List<CarContact> {
        val all =
            merged.map { (contactKey, packet) ->
                val fromLocal = packet.from == DataPacket.ID_LOCAL || packet.from == myId
                val toBroadcast = packet.to == DataPacket.ID_BROADCAST
                val userId = if (fromLocal) packet.to else packet.from

                val displayName =
                    if (toBroadcast) {
                        channelSet.getChannel(packet.channel)?.name?.takeIf { it.isNotEmpty() }
                            ?: "Channel ${packet.channel}"
                    } else {
                        // userId can be null for malformed packets (e.g. both `from` and `to`
                        // are null). Fall back to a broadcast lookup which returns an "Unknown"
                        // user rather than crashing.
                        val user = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)
                        user.long_name.ifEmpty { user.short_name }.ifEmpty { "Unknown" }
                    }

                CarContact(
                    contactKey = contactKey,
                    displayName = displayName,
                    unreadCount = 0, // filled in reactively by flatMapLatest below
                    isBroadcast = toBroadcast,
                    channelIndex = packet.channel,
                    lastMessageTime = if (packet.time != 0L) packet.time else null,
                )
            }

        return all.filter { it.isBroadcast }.sortedBy { it.channelIndex } +
            all.filter { !it.isBroadcast }.sortedByDescending { it.lastMessageTime ?: 0L }
    }

    // ---- Template building ----

    override fun onGetTemplate(): Template {
        // MessageTemplate.setLoading() requires Car API 5+. On older hosts fall through
        // to the ListTemplate fallback (StateFlows emit their cached state near-instantly).
        if (isLoading && carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_5) {
            return MessageTemplate.Builder("Loading…")
                .setHeaderAction(Action.APP_ICON)
                .setLoading(true)
                .build()
        }

        // TabTemplate requires Car API level 6. Fall back to a combined ListTemplate
        // on older hosts so the app remains functional on all supported vehicles.
        if (carContext.carAppApiLevel < CarAppApiLevels.LEVEL_6) {
            return buildFallbackListTemplate()
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
                    .setTitle("Status")
                    .setIcon(carIcon(R.drawable.auto_ic_status))
                    .setContentId(TAB_STATUS)
                    .build(),
            )
            .addTab(
                Tab.Builder()
                    .setTitle("Favorites")
                    .setIcon(carIcon(R.drawable.auto_ic_favorites))
                    .setContentId(TAB_FAVORITES)
                    .build(),
            )
            .addTab(
                Tab.Builder()
                    .setTitle("Messages")
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
            .setTitle("Status")
            .setSingleList(ItemList.Builder().addItem(buildStatusRow()).build())
            .build()

    /**
     * Builds the Messages tab content: channels first (always present, even if empty), followed
     * by DM conversations sorted by most-recent message — identical to the phone's Contacts screen.
     */
    private fun buildMessagesTemplate(): ListTemplate {
        val items = ItemList.Builder()
        val capped = contacts.take(MAX_LIST_ITEMS)
        if (capped.isEmpty()) {
            items.setNoItemsMessage("No conversations")
        } else {
            capped.forEach { contact -> items.addItem(buildContactRow(contact)) }
        }
        return ListTemplate.Builder().setTitle("Messages").setSingleList(items.build()).build()
    }

    /**
     * Fallback for Car API level 1–5 hosts that do not support [TabTemplate].
     *
     * Shows a status row followed by the combined contact list (channels first, then DMs) in a
     * single [ListTemplate].
     */
    private fun buildFallbackListTemplate(): ListTemplate {
        val items = ItemList.Builder()
        items.addItem(buildStatusRow())
        contacts.take(MAX_LIST_ITEMS).forEach { contact -> items.addItem(buildContactRow(contact)) }
        return ListTemplate.Builder().setTitle("Meshtastic").setSingleList(items.build()).build()
    }

    private fun buildStatusRow(): Row {
        val statusText =
            when (connectionState) {
                is ConnectionState.Connected -> "Connected"
                is ConnectionState.Disconnected -> "Disconnected"
                is ConnectionState.DeviceSleep -> "Device Sleeping"
                is ConnectionState.Connecting -> "Connecting…"
            }
        val deviceName = nodeRepository.ourNodeInfo.value?.user?.long_name.orEmpty()
        return Row.Builder()
            .setTitle(statusText)
            .apply { if (deviceName.isNotEmpty()) addText(deviceName) }
            .setBrowsable(false)
            .build()
    }

    private fun buildContactRow(contact: CarContact): Row {
        val subtitle = if (contact.unreadCount > 0) "${contact.unreadCount} unread" else ""
        return Row.Builder()
            .setTitle(contact.displayName)
            .apply { if (subtitle.isNotEmpty()) addText(subtitle) }
            .setBrowsable(false)
            .build()
    }

    private fun carIcon(resId: Int) =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).setTint(CarColor.DEFAULT).build()

    // ---- Internal model ----

    /**
     * Lightweight projection of a conversation used exclusively within this screen.
     *
     * [isBroadcast] and [channelIndex] drive ordering (channels before DMs, channels sorted by
     * index). [lastMessageTime] drives DM ordering (most-recent first).
     */
    private data class CarContact(
        val contactKey: String,
        val displayName: String,
        val unreadCount: Int,
        val isBroadcast: Boolean,
        val channelIndex: Int,
        val lastMessageTime: Long?,
    )

    companion object {
        private const val TAB_STATUS = "status"
        private const val TAB_MESSAGES = "messages"

        /**
         * Car App Library enforces a per-[ListTemplate] item cap via
         * `ConstraintManager.CONTENT_LIMIT_TYPE_LIST`. 6 is the conservative floor across all
         * supported hosts.
         */
        private const val MAX_LIST_ITEMS = 6
    }
}

