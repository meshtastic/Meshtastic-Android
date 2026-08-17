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
package org.meshtastic.feature.node.detail

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NodeManagementActionsTest {

    private val nodeRepository = FakeNodeRepository()
    private val radioController = FakeRadioController()
    private val alertManager = mock<AlertManager>(MockMode.autofill)
    private val snackbarManager = RecordingSnackbarManager()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val resolveUiText = resolveNodeRequestFailureUiText
    private val analytics = mock<PlatformAnalytics>(MockMode.autofill)

    private val actions = actionsWith(radioController, alertManager)

    @BeforeTest
    fun setUp() {
        nodeRepository.setNodes(emptyList())
        snackbarManager.messages.clear()
    }

    private fun actionsWith(radio: RadioController, alerts: AlertManager) = NodeManagementActions(
        nodeRepository = nodeRepository,
        radioController = radio,
        alertManager = alerts,
        analytics = analytics,
        snackbarManager = snackbarManager,
        resolveUiText = resolveUiText,
    )

    @Test
    fun requestRemoveNode_shows_confirmation_alert() {
        val node = Node(num = 123, user = User(long_name = "Test Node"))

        actions.requestRemoveNode(testScope, node)

        verify {
            alertManager.showAlert(
                titleRes = any(),
                messageRes = any(),
                onConfirm = any(),
                onDismiss = any(),
                confirmText = any(),
                confirmTextRes = any(),
                dismissText = any(),
                dismissTextRes = any(),
                choices = any(),
            )
        }
    }

    @Test
    fun requestRemoveNode_invokes_onAfterRemove_when_user_confirms() {
        val realAlertManager = AlertManager()
        val actionsWithRealAlert = actionsWith(radioController, realAlertManager)
        val node = Node(num = 123, user = User(long_name = "Test Node"))
        var afterRemoveCalled = false

        actionsWithRealAlert.requestRemoveNode(testScope, node) { afterRemoveCalled = true }
        realAlertManager.currentAlert.value?.onConfirm?.invoke()
        testScope.runCurrent()

        assertTrue(afterRemoveCalled)
    }

    @Test
    fun requestRemoveNode_success_callback_failure_is_not_reported_as_radio_rejection() {
        val realAlertManager = AlertManager()
        val actionsWithRealAlert = actionsWith(radioController, realAlertManager)
        val node = Node(num = 123, user = User(long_name = "Test Node"))
        nodeRepository.setNodes(listOf(node))

        actionsWithRealAlert.requestRemoveNode(testScope, node) { throw PacketQueueRejectedException("callback") }
        realAlertManager.currentAlert.value?.onConfirm?.invoke()
        testScope.runCurrent()

        assertTrue(snackbarManager.messages.isEmpty())
    }

    @Test
    fun requestRemoveNode_queue_rejection_keeps_success_callback_pending_and_surfaces_feedback() {
        val rejectedRadio = mock<RadioController>()
        val realAlertManager = AlertManager()
        val rejectedActions = actionsWith(rejectedRadio, realAlertManager)
        val node = Node(num = 123, user = User(long_name = "Test Node"))
        var afterRemoveCalled = false
        nodeRepository.setNodes(listOf(node))
        every { rejectedRadio.generatePacketId() } returns 7
        everySuspend { rejectedRadio.removeByNodenum(7, 123) } throws PacketQueueRejectedException("Remove node")

        rejectedActions.requestRemoveNode(testScope, node) { afterRemoveCalled = true }
        realAlertManager.currentAlert.value?.onConfirm?.invoke()
        testScope.runCurrent()

        assertFalse(afterRemoveCalled)
        assertEquals(node, nodeRepository.nodeDBbyNum.value[123])
        assertEquals(listOf(NODE_REQUEST_SEND_FAILED_TEXT), snackbarManager.messages)
    }

    @Test
    fun requestRemoveNode_local_node_loss_keeps_success_callback_pending_and_surfaces_feedback() {
        val unavailableRadio = mock<RadioController>()
        val realAlertManager = AlertManager()
        val unavailableActions = actionsWith(unavailableRadio, realAlertManager)
        val node = Node(num = 123, user = User(long_name = "Test Node"))
        var afterRemoveCalled = false
        nodeRepository.setNodes(listOf(node))
        every { unavailableRadio.generatePacketId() } returns 7
        everySuspend { unavailableRadio.removeByNodenum(7, 123) } throws LocalNodeUnavailableException("Remove node")

        unavailableActions.requestRemoveNode(testScope, node) { afterRemoveCalled = true }
        realAlertManager.currentAlert.value?.onConfirm?.invoke()
        testScope.runCurrent()

        assertFalse(afterRemoveCalled)
        assertEquals(node, nodeRepository.nodeDBbyNum.value[123])
        assertEquals(listOf(NODE_REQUEST_SEND_FAILED_TEXT), snackbarManager.messages)
    }
}
