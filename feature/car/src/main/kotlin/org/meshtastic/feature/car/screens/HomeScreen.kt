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
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    }

    override fun onGetTemplate(): Template {
        val messagingTab =
            Tab.Builder()
                .setContentId(TAB_ID_MESSAGES)
                .setTitle(carContext.getString(R.string.car_tab_messages))
                .build()

        val nodesTab =
            Tab.Builder().setContentId(TAB_ID_NODES).setTitle(carContext.getString(R.string.car_tab_nodes)).build()

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
            state.conversations.forEach { conversation ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(conversation.displayName)
                        .addText(conversation.lastMessage)
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(
                                ConversationScreen(
                                    carContext = carContext,
                                    conversationName = conversation.displayName,
                                    messagesProvider = { emptyList() },
                                    onVoiceReply = {},
                                    onQuickReply = {},
                                    onReadAloud = {},
                                ),
                            )
                        }
                        .build(),
                )
            }
        }

        return ListTemplate.Builder().setSingleList(listBuilder.build()).build()
    }

    private fun buildNodeList(): Template {
        val state = stateCoordinator.nodeDashboardState.value
        val listBuilder = ItemList.Builder()

        if (state.nodes.isEmpty()) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.car_no_nodes))
        } else {
            state.nodes.forEach { node ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(node.longName)
                        .addText(formatNodeSubtitle(node))
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
                SignalQuality.EXCELLENT -> "Excellent"
                SignalQuality.GOOD -> "Good"
                SignalQuality.FAIR -> "Fair"
                SignalQuality.POOR -> "Poor"
                SignalQuality.UNKNOWN -> "Unknown"
            }
        val battery = node.batteryPercent?.let { " • $it%" } ?: ""
        val status = if (!node.isOnline) " • Offline" else ""
        return "$signal$battery$status"
    }

    companion object {
        private const val TAB_ID_MESSAGES = "messages"
        private const val TAB_ID_NODES = "nodes"
    }
}
