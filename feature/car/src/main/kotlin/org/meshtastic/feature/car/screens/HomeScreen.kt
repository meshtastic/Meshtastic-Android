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
package org.meshtastic.feature.car.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.feature.car.R
import org.meshtastic.feature.car.model.NodeUi
import org.meshtastic.feature.car.model.SignalQuality
import org.meshtastic.feature.car.service.CarStateCoordinator

class HomeScreen(carContext: CarContext, private val stateCoordinator: CarStateCoordinator) : Screen(carContext) {

    private var selectedTabId: String = TAB_ID_MESSAGES
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    observeState()
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    scope.cancel()
                }
            },
        )
    }

    private fun observeState() {
        scope.launch { stateCoordinator.messagingState.collect { invalidate() } }
        scope.launch { stateCoordinator.nodeDashboardState.collect { invalidate() } }
        scope.launch { stateCoordinator.sessionState.collect { invalidate() } }
    }

    @Suppress("ReturnCount")
    override fun onGetTemplate(): Template {
        val connectionStatus = stateCoordinator.sessionState.value.connectionStatus
        if (connectionStatus == ConnectionState.Disconnected || connectionStatus == ConnectionState.DeviceSleep) {
            return buildDisconnectedTemplate()
        }
        val messaging = stateCoordinator.messagingState.value
        if (messaging.channels.isEmpty()) {
            return buildOnboardingTemplate()
        }
        val messagingTab =
            Tab.Builder()
                .setContentId(TAB_ID_MESSAGES)
                .setTitle(carContext.getString(R.string.car_tab_messages))
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_message)).build())
                .build()

        val nodesTab =
            Tab.Builder()
                .setContentId(TAB_ID_NODES)
                .setTitle(carContext.getString(R.string.car_tab_nodes))
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_nodes)).build())
                .build()

        return TabTemplate.Builder(
            object : TabTemplate.TabCallback {
                override fun onTabSelected(tabContentId: String) {
                    selectedTabId = tabContentId
                    invalidate()
                }
            },
        )
            .apply {
                setHeaderAction(Action.APP_ICON)
                addTab(messagingTab)
                addTab(nodesTab)
                setTabContents(getTabContents())
            }
            .build()
    }

    private fun getTabContents(): TabContents {
        val template =
            when (selectedTabId) {
                TAB_ID_MESSAGES -> buildMessagingList()
                TAB_ID_NODES -> buildNodeList()
                else -> buildMessagingList()
            }
        return TabContents.Builder(template).build()
    }

    private fun buildMessagingList(): Template {
        val state = stateCoordinator.messagingState.value
        val listBuilder = ItemList.Builder()

        if (state.conversations.isEmpty()) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.car_no_messages))
        } else {
            val personIcon =
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_person)).build()
            state.conversations.forEach { conversation ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(conversation.displayName)
                        .addText(conversation.lastMessage)
                        .setImage(personIcon)
                        .setBrowsable(true)
                        .setOnClickListener { openConversation(conversation.contactKey, conversation.displayName) }
                        .build(),
                )
            }
        }

        return ListTemplate.Builder().setSingleList(listBuilder.build()).build()
    }

    private fun openConversation(contactKey: String, displayName: String) {
        scope.launch {
            val messages = stateCoordinator.getMessagesFlow(contactKey).firstOrNull() ?: emptyList()
            stateCoordinator.cacheMessages(contactKey, messages)
            screenManager.push(
                ConversationScreen(
                    carContext = carContext,
                    conversationName = displayName,
                    messagesProvider = { messages },
                    onVoiceReply = { /* Voice input requires CarContext intent — deferred to DHU testing */ },
                    onQuickReply = { text -> stateCoordinator.sendMessage(contactKey, text) },
                    onReadAloud = { stateCoordinator.readMessagesAloud(contactKey) },
                ),
            )
        }
    }

    private fun buildNodeList(): Template {
        val state = stateCoordinator.nodeDashboardState.value
        val listBuilder = ItemList.Builder()

        if (state.nodes.isEmpty()) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.car_no_nodes))
        } else {
            val nodeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_nodes)).build()
            state.nodes.forEach { node ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(node.longName)
                        .addText(formatNodeSubtitle(node))
                        .setImage(nodeIcon)
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(
                                NodeDetailScreen(carContext = carContext, nodeProvider = { node }, onMessageClick = {}),
                            )
                        }
                        .build(),
                )
            }
        }

        return ListTemplate.Builder().setSingleList(listBuilder.build()).build()
    }

    private fun formatNodeSubtitle(node: NodeUi): String {
        val signal =
            when (node.signalQuality) {
                SignalQuality.EXCELLENT -> carContext.getString(R.string.car_signal_excellent)
                SignalQuality.GOOD -> carContext.getString(R.string.car_signal_good)
                SignalQuality.FAIR -> carContext.getString(R.string.car_signal_fair)
                SignalQuality.BAD -> carContext.getString(R.string.car_signal_bad)
                SignalQuality.NONE -> carContext.getString(R.string.car_signal_none)
            }
        val battery = node.batteryPercent?.let { " • $it%" } ?: ""
        val lastHeard =
            if (node.lastHeard != 0L) {
                " • ${DateFormatter.formatRelativeTime(node.lastHeard)}"
            } else {
                ""
            }
        val status = if (!node.isOnline) " • ${carContext.getString(R.string.car_status_offline)}" else ""
        return "$signal$battery$lastHeard$status"
    }

    private fun buildDisconnectedTemplate(): Template = PaneTemplate.Builder(
        Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_disconnected))
                    .addText(carContext.getString(R.string.car_reconnecting))
                    .setImage(
                        CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_warning))
                            .build(),
                    )
                    .build(),
            )
            .build(),
    )
        .setHeader(
            Header.Builder()
                .setTitle(carContext.getString(R.string.car_app_name))
                .setStartHeaderAction(Action.APP_ICON)
                .build(),
        )
        .build()

    private fun buildOnboardingTemplate(): Template = PaneTemplate.Builder(
        Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_onboarding_title))
                    .addText(carContext.getString(R.string.car_onboarding_text))
                    .setImage(
                        CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_meshtastic))
                            .build(),
                    )
                    .build(),
            )
            .build(),
    )
        .setHeader(
            Header.Builder()
                .setTitle(carContext.getString(R.string.car_app_name))
                .setStartHeaderAction(Action.APP_ICON)
                .build(),
        )
        .build()

    companion object {
        private const val TAB_ID_MESSAGES = "messages"
        private const val TAB_ID_NODES = "nodes"
    }
}
