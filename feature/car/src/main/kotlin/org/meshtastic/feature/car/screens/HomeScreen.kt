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

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.messaging.model.CarMessage
import androidx.car.app.messaging.model.ConversationCallback
import androidx.car.app.messaging.model.ConversationItem
import androidx.car.app.model.Action
import androidx.car.app.model.Alert
import androidx.car.app.model.AlertCallback
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
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
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.nodeColorsFromNum
import org.meshtastic.feature.car.R
import org.meshtastic.feature.car.alerts.EmergencyHandler
import org.meshtastic.feature.car.model.ConversationUi
import org.meshtastic.feature.car.service.CarStateCoordinator
import org.meshtastic.feature.car.util.CarScreenDataBuilder
import org.meshtastic.feature.car.util.NodeSubtitleFormatter
import java.util.Locale

@Suppress("TooManyFunctions")
class HomeScreen(
    carContext: CarContext,
    private val stateCoordinator: CarStateCoordinator,
    private val emergencyHandler: EmergencyHandler,
) : Screen(carContext) {

    private var selectedTabId: String = TAB_ID_MESSAGES
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var previousConnectionState: ConnectionState = ConnectionState.Disconnected

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
        scope.launch { stateCoordinator.localStatsState.collect { invalidate() } }
        scope.launch {
            stateCoordinator.sessionState.collect { state ->
                val newState = state.connectionStatus
                if (previousConnectionState == ConnectionState.Disconnected && newState == ConnectionState.Connected) {
                    CarToast.makeText(carContext, carContext.getString(R.string.car_reconnected), CarToast.LENGTH_SHORT)
                        .show()
                }
                previousConnectionState = newState
                invalidate()
            }
        }
        scope.launch {
            emergencyHandler.latestAlert.collect { alert ->
                if (alert != null && alert.isActive) {
                    showEmergencyAlert(alert.nodeNum, alert.nodeName, alert.message)
                }
            }
        }
    }

    private fun showEmergencyAlert(nodeNum: Int, nodeName: String, message: String) {
        if (carContext.carAppApiLevel < MIN_ALERT_CAR_API_LEVEL) {
            Logger.w(tag = "HomeScreen") { "Alert API unavailable on car API ${carContext.carAppApiLevel}" }
            return
        }

        val alert =
            Alert.Builder(
                nodeNum,
                CarText.create(carContext.getString(R.string.car_emergency_from, nodeName)),
                ALERT_DURATION_MS.toLong(),
            )
                .setSubtitle(CarText.create(message))
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.car_dismiss))
                        .setOnClickListener { emergencyHandler.dismissAlert(nodeNum) }
                        .build(),
                )
                .setCallback(
                    object : AlertCallback {
                        override fun onCancel(reason: Int) {
                            emergencyHandler.dismissAlert(nodeNum)
                        }

                        override fun onDismiss() {
                            emergencyHandler.dismissAlert(nodeNum)
                        }
                    },
                )
                .build()

        carContext.getCarService(AppManager::class.java).showAlert(alert)
    }

    // Any exception thrown out of onGetTemplate() crashes the Android Auto host ("content not able to
    // load") and fails Play's Auto review. Degrade to a safe template instead and log for diagnosis.
    // NOTE: this guards exceptions in OUR build path; a host-side template-contract rejection (e.g. the
    // messaging surface must be a ListTemplate/SectionedItemTemplate of ConversationItems, not this
    // TabTemplate dashboard) would not throw here — confirm against the Play pre-launch stack before
    // promoting templated builds. See -PenableCarTemplates in androidApp/build.gradle.kts.
    @Suppress("TooGenericExceptionCaught")
    override fun onGetTemplate(): Template = try {
        buildHomeTemplate()
    } catch (e: Exception) {
        Logger.e(tag = "HomeScreen", throwable = e) { "onGetTemplate failed; showing fallback template" }
        buildDisconnectedTemplate()
    }

    @Suppress("ReturnCount")
    private fun buildHomeTemplate(): Template {
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

        val statusTab =
            Tab.Builder()
                .setContentId(TAB_ID_STATUS)
                .setTitle(carContext.getString(R.string.car_tab_status))
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_status)).build())
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
                addTab(statusTab)
                setActiveTabContentId(selectedTabId)
                setTabContents(getTabContents())
            }
            .build()
    }

    private fun getTabContents(): TabContents {
        val template =
            when (selectedTabId) {
                TAB_ID_MESSAGES -> buildMessagingList()
                TAB_ID_NODES -> buildNodeList()
                TAB_ID_STATUS -> buildStatusList()
                else -> buildMessagingList()
            }
        return TabContents.Builder(template).build()
    }

    private fun buildMessagingList(): Template {
        val state = stateCoordinator.messagingState.value
        val listBuilder = ItemList.Builder()

        val validConversations = state.conversations.filter { it.lastMessage.isNotEmpty() }
        if (validConversations.isEmpty()) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.car_no_messages))
        } else {
            val selfPerson = buildSelfPerson()
            validConversations.take(CarScreenDataBuilder.MAX_CONVERSATIONS).forEach { conversation ->
                listBuilder.addItem(buildConversationItem(conversation, selfPerson))
            }
        }

        return ListTemplate.Builder().setSingleList(listBuilder.build()).build()
    }

    private fun buildSelfPerson(): Person {
        val myName = stateCoordinator.sessionState.value.meshName ?: "Me"
        return Person.Builder().setName(myName).setKey("self").build()
    }

    private fun buildConversationItem(conversation: ConversationUi, selfPerson: Person): ConversationItem {
        val senderPerson = Person.Builder().setName(conversation.displayName).setKey(conversation.contactKey).build()

        val messages = buildCarMessages(conversation, senderPerson)

        val callback =
            object : ConversationCallback {
                override fun onMarkAsRead() {
                    stateCoordinator.markAsRead(conversation.contactKey)
                }

                override fun onTextReply(replyText: String) {
                    stateCoordinator.sendMessage(conversation.contactKey, replyText)
                }
            }

        // car-app 1.7 deprecated the no-arg Builder + individual setters in favor of the
        // required-args constructor (id, title, self, messages, conversationCallback).
        return ConversationItem.Builder(
            conversation.contactKey,
            CarText.create(conversation.displayName),
            selfPerson,
            messages,
            callback,
        )
            .setGroupConversation(conversation.contactKey.contains(NodeAddress.ID_BROADCAST))
            .build()
    }

    private fun buildCarMessages(conversation: ConversationUi, senderPerson: Person): List<CarMessage> = listOf(
        CarMessage.Builder()
            .setSender(senderPerson)
            .setBody(CarText.create(conversation.lastMessage))
            .setReceivedTimeEpochMillis(conversation.lastMessageTime)
            .setRead(conversation.unreadCount == 0)
            .build(),
    )

    private fun buildNodeList(): Template {
        val state = stateCoordinator.nodeDashboardState.value
        val listBuilder = ItemList.Builder()

        if (state.nodes.isEmpty()) {
            listBuilder.setNoItemsMessage(carContext.getString(R.string.car_no_nodes))
        } else {
            val baseIcon = IconCompat.createWithResource(carContext, R.drawable.ic_car_nodes)
            state.nodes.forEach { node ->
                val (_, nodeColor) = nodeColorsFromNum(node.nodeNum)
                val tintedIcon = CarIcon.Builder(baseIcon).setTint(CarColor.createCustom(nodeColor, nodeColor)).build()
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(node.longName)
                        .addText(NodeSubtitleFormatter.format(carContext, node))
                        .setImage(tintedIcon, Row.IMAGE_TYPE_ICON)
                        .setBrowsable(true)
                        .setOnClickListener {
                            screenManager.push(
                                NodeDetailScreen(
                                    carContext = carContext,
                                    nodeProvider = { node },
                                    onMessageClick = { contactKey ->
                                        screenManager.pop()
                                        selectedTabId = TAB_ID_MESSAGES
                                        stateCoordinator.ensureDmConversation(
                                            contactKey,
                                            node.longName,
                                            carContext.getString(R.string.car_new_conversation),
                                        )
                                        invalidate()
                                    },
                                ),
                            )
                        }
                        .build(),
                )
            }
        }

        return ListTemplate.Builder().setSingleList(listBuilder.build()).build()
    }

    private fun buildStatusList(): Template {
        val stats = stateCoordinator.localStatsState.value
        val listBuilder = ItemList.Builder()

        if (stats.hasBattery) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_stat_battery))
                    .addText("${stats.batteryLevel}%")
                    .build(),
            )
        }

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_stat_channel_util))
                .addText(String.format(Locale.getDefault(), "%.1f%%", stats.channelUtilization))
                .build(),
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_stat_air_util))
                .addText(String.format(Locale.getDefault(), "%.1f%%", stats.airUtilization))
                .build(),
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_stat_nodes))
                .addText("${stats.onlineNodes} / ${stats.totalNodes}")
                .build(),
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_stat_uptime))
                .addText(CarScreenDataBuilder.formatUptime(stats.uptimeSeconds))
                .build(),
        )

        listBuilder.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_stat_packets))
                .addText("TX: ${stats.numPacketsTx} / RX: ${stats.numPacketsRx}")
                .build(),
        )

        return ListTemplate.Builder().setSingleList(listBuilder.build()).build()
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
        private const val TAB_ID_STATUS = "status"
        private const val MIN_ALERT_CAR_API_LEVEL = 8
        private const val ALERT_DURATION_MS = 10_000
    }
}
