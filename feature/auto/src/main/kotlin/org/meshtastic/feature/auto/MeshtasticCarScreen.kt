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
import kotlinx.coroutines.Job
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
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.ChannelSettings

/**
 * Root screen displayed in Android Auto.
 *
 * Renders a three-tab UI mirroring the iOS CarPlay tab-based navigation:
 * - **Status** — Connection state and device name
 * - **Favorites** — Favourited mesh nodes with unread message counts
 * - **Channels** — Active channels with unread message counts
 *
 * `TabTemplate` requires Car API level 6. On hosts running Car API level 1–5 the
 * screen falls back to a single [ListTemplate] showing the same data (status row +
 * favourites + channels) without tab chrome. The manifest declares
 * `minCarApiLevel=1` so the app remains usable on all supported vehicles.
 *
 * When the user taps a [MessagingStyle][androidx.core.app.NotificationCompat.MessagingStyle]
 * notification in the Android Auto notification shade, the host calls
 * [MeshtasticCarSession.onNewIntent] with the conversation deep-link URI.
 * The session delegates to [selectContactKey] so the correct tab is pre-selected
 * before [onGetTemplate] fires.
 */
class MeshtasticCarScreen(carContext: CarContext) :
    Screen(carContext),
    KoinComponent,
    DefaultLifecycleObserver {

    private val nodeRepository: NodeRepository by inject()
    private val radioConfigRepository: RadioConfigRepository by inject()
    private val packetRepository: PacketRepository by inject()
    private val serviceRepository: ServiceRepository by inject()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observeJob: Job? = null

    private var activeTabId = TAB_STATUS
    private var connectionState: ConnectionState = ConnectionState.Disconnected
    private var favoriteNodes: List<Node> = emptyList()
    private var channels: List<Pair<Int, ChannelSettings>> = emptyList()
    private var unreadCounts: Map<String, Int> = emptyMap()

    /**
     * True until the first [collect] emission arrives from the repository flows.
     * While loading, [onGetTemplate] returns a spinner [MessageTemplate] instead of
     * an empty/disconnected screen.
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
        observeJob?.cancel()
        observeJob =
            scope.launch {
                // serviceRepository.connectionState is a StateFlow — distinctUntilChanged is a no-op on it.
                val stateFlow = serviceRepository.connectionState

                val favoritesFlow =
                    nodeRepository.nodeDBbyNum
                        .map { nodes ->
                            val myNum = nodeRepository.myNodeInfo.value?.myNodeNum
                            nodes.values
                                .filter { it.isFavorite && !it.isIgnored && it.num != myNum }
                                .sortedBy { it.user.long_name }
                        }
                        .distinctUntilChanged()

                val channelsFlow =
                    radioConfigRepository.channelSetFlow
                        .map { cs ->
                            cs.settings.mapIndexedNotNull { index, settings ->
                                if (index == 0 || settings.name.isNotEmpty()) index to settings else null
                            }
                        }
                        .distinctUntilChanged()

                combine(stateFlow, favoritesFlow, channelsFlow) { state, favorites, chs ->
                    Triple(state, favorites, chs)
                }
                    .flatMapLatest { (state, favorites, chs) ->
                        val contactKeys =
                            favorites.map { "0${it.user.id}" } + chs.map { (i, _) -> "${i}${DataPacket.ID_BROADCAST}" }

                        if (contactKeys.isEmpty()) {
                            flowOf(Triple(state, favorites, chs) to emptyMap())
                        } else {
                            val unreadFlows =
                                contactKeys.map { key ->
                                    packetRepository.getUnreadCountFlow(key).map { count -> key to count }
                                }
                            combine(unreadFlows) { pairs -> Triple(state, favorites, chs) to pairs.toMap() }
                        }
                    }
                    .collect { (triple, counts) ->
                        val (state, favorites, chs) = triple
                        connectionState = state
                        favoriteNodes = favorites
                        channels = chs
                        unreadCounts = counts
                        isLoading = false
                        invalidate()
                    }
            }
    }

    override fun onGetTemplate(): Template {
        // MessageTemplate.setLoading() requires Car API 5+. On older hosts fall through
        // to the ListTemplate fallback immediately (StateFlows emit their cached state
        // near-instantly so the transient empty state is barely visible).
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
                TAB_CHANNELS -> TabContents.Builder(buildChannelsTemplate()).build()
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
                    .setTitle("Channels")
                    .setIcon(carIcon(R.drawable.auto_ic_channels))
                    .setContentId(TAB_CHANNELS)
                    .build(),
            )
            .setTabContents(activeContent)
            .setActiveTabContentId(activeTabId)
            .build()
    }

    /**
     * Called by [MeshtasticCarSession.onNewIntent] when the user taps a conversation
     * notification in the Android Auto notification shade.
     *
     * Selects the [TAB_FAVORITES] tab if [contactKey] looks like a DM (starts with a
     * channel digit followed by a node ID), or [TAB_CHANNELS] if it is a broadcast
     * conversation. Triggers a template refresh so the correct tab is highlighted.
     */
    fun selectContactKey(contactKey: String) {
        activeTabId = if (contactKey.endsWith(DataPacket.ID_BROADCAST)) TAB_CHANNELS else TAB_FAVORITES
        invalidate()
    }

    /**
     * Fallback template for Car API level 1–5 hosts that do not support [TabTemplate].
     *
     * Shows a single [ListTemplate] with the status row followed by all favourites
     * and all channels — the same data as the tab UI but in a combined list.
     */
    private fun buildFallbackListTemplate(): ListTemplate {
        val items = ItemList.Builder()

        // Status row
        val statusText =
            when (connectionState) {
                is ConnectionState.Connected -> "Connected"
                is ConnectionState.Disconnected -> "Disconnected"
                is ConnectionState.DeviceSleep -> "Device Sleeping"
                is ConnectionState.Connecting -> "Connecting…"
            }
        val deviceName = nodeRepository.ourNodeInfo.value?.user?.long_name.orEmpty()
        items.addItem(
            Row.Builder()
                .setTitle(statusText)
                .apply { if (deviceName.isNotEmpty()) addText(deviceName) }
                .setBrowsable(false)
                .build(),
        )

        // Favourite nodes
        favoriteNodes.take(MAX_LIST_ITEMS).forEach { node ->
            items.addItem(buildFavoriteNodeRow(node))
        }

        // Channels
        channels.take(MAX_LIST_ITEMS).forEach { (index, settings) ->
            items.addItem(buildChannelRow(index, settings))
        }

        return ListTemplate.Builder()
            .setTitle("Meshtastic")
            .setSingleList(items.build())
            .build()
    }

    private fun carIcon(resId: Int) =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).setTint(CarColor.DEFAULT).build()

    private fun buildStatusTemplate(): ListTemplate {
        val statusText =
            when (connectionState) {
                is ConnectionState.Connected -> "Connected"
                is ConnectionState.Disconnected -> "Disconnected"
                is ConnectionState.DeviceSleep -> "Device Sleeping"
                is ConnectionState.Connecting -> "Connecting..."
            }

        val deviceName = nodeRepository.ourNodeInfo.value?.user?.long_name ?: ""
        val subtitle = if (deviceName.isNotEmpty()) deviceName else null

        val row =
            Row.Builder()
                .setTitle(statusText)
                .apply { if (subtitle != null) addText(subtitle) }
                .setBrowsable(false)
                .build()

        return ListTemplate.Builder().setTitle("Status").setSingleList(ItemList.Builder().addItem(row).build()).build()
    }

    private fun buildFavoritesTemplate(): ListTemplate {
        val items = ItemList.Builder()
        if (favoriteNodes.isEmpty()) {
            items.setNoItemsMessage("No favorite contacts")
        } else {
            favoriteNodes.take(MAX_LIST_ITEMS).forEach { node ->
                items.addItem(buildFavoriteNodeRow(node))
            }
        }

        return ListTemplate.Builder().setTitle("Favorites").setSingleList(items.build()).build()
    }

    private fun buildChannelsTemplate(): ListTemplate {
        val items = ItemList.Builder()
        if (channels.isEmpty()) {
            items.setNoItemsMessage("No active channels")
        } else {
            channels.take(MAX_LIST_ITEMS).forEach { (index, settings) ->
                items.addItem(buildChannelRow(index, settings))
            }
        }

        return ListTemplate.Builder().setTitle("Channels").setSingleList(items.build()).build()
    }

    private fun buildChannelRow(index: Int, channelSettings: ChannelSettings): Row {
        val contactKey = "${index}${DataPacket.ID_BROADCAST}"
        val unread = unreadCounts[contactKey] ?: 0
        val channelName = channelSettings.name.ifEmpty { "Primary Channel" }
        val subtitle = if (unread > 0) "$unread unread" else ""
        return Row.Builder()
            .setTitle(channelName)
            .apply { if (subtitle.isNotEmpty()) addText(subtitle) }
            .setBrowsable(false)
            .build()
    }

    private fun buildFavoriteNodeRow(node: Node): Row {
        val contactKey = "0${node.user.id}"
        val unread = unreadCounts[contactKey] ?: 0
        val name = node.user.long_name.ifEmpty { node.user.short_name }.ifEmpty { "Unknown" }
        val subtitle = buildString {
            append(node.user.short_name)
            if (node.hopsAway >= 0) append(" · ${node.hopsAway} hops")
            if (unread > 0) append(" · $unread unread")
        }
        return Row.Builder().setTitle(name).addText(subtitle).setBrowsable(false).build()
    }

    companion object {
        private const val TAB_STATUS = "status"
        private const val TAB_FAVORITES = "favorites"
        private const val TAB_CHANNELS = "channels"

        /**
         * Android Auto enforces a per-[ListTemplate] item cap via [androidx.car.app.constraints.ConstraintManager]'s
         * `CONTENT_LIMIT_TYPE_LIST`. 6 is the conservative floor across supported hosts.
         */
        private const val MAX_LIST_ITEMS = 6
    }
}
