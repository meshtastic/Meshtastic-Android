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

    private val actions =
        NodeManagementActions(
            nodeRepository = nodeRepository,
            radioController = radioController,
            alertManager = alertManager,
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
        val actionsWithRealAlert =
            NodeManagementActions(
                nodeRepository = nodeRepository,
                radioController = radioController,
                alertManager = realAlertManager,
                snackbarManager = snackbarManager,
                resolveUiText = resolveUiText,
            )
        val node = Node(num = 123, user = User(long_name = "Test Node"))
        var afterRemoveCalled = false

        actionsWithRealAlert.requestRemoveNode(testScope, node) { afterRemoveCalled = true }
        realAlertManager.currentAlert.value?.onConfirm?.invoke()
        testScope.runCurrent()

        assertTrue(afterRemoveCalled)
    }

    @BeforeTest
    fun setUp() {
        snackbarManager.messages.clear()
    }

    @Test
    fun requestRemoveNode_queue_rejection_keeps_success_callback_pending_and_surfaces_feedback() {
        val rejectedRadio = mock<RadioController>()
        val realAlertManager = AlertManager()
        val rejectedActions =
            NodeManagementActions(
                nodeRepository = nodeRepository,
                radioController = rejectedRadio,
                alertManager = realAlertManager,
                snackbarManager = snackbarManager,
                resolveUiText = resolveUiText,
            )
        val node = Node(num = 123, user = User(long_name = "Test Node"))
        var afterRemoveCalled = false
        every { rejectedRadio.generatePacketId() } returns 7
        everySuspend { rejectedRadio.removeByNodenum(7, 123) } throws PacketQueueRejectedException("Remove node")

        rejectedActions.requestRemoveNode(testScope, node) { afterRemoveCalled = true }
        realAlertManager.currentAlert.value?.onConfirm?.invoke()
        testScope.runCurrent()

        assertFalse(afterRemoveCalled)
        assertEquals(listOf("Couldn't send request. Try again."), snackbarManager.messages)
    }

    @Test
    fun requestRemoveNode_local_node_loss_keeps_success_callback_pending_and_surfaces_feedback() {
        val unavailableRadio = mock<RadioController>()
        val realAlertManager = AlertManager()
        val unavailableActions =
            NodeManagementActions(
                nodeRepository = nodeRepository,
                radioController = unavailableRadio,
                alertManager = realAlertManager,
                snackbarManager = snackbarManager,
                resolveUiText = resolveUiText,
            )
        val node = Node(num = 123, user = User(long_name = "Test Node"))
        var afterRemoveCalled = false
        every { unavailableRadio.generatePacketId() } returns 7
        everySuspend { unavailableRadio.removeByNodenum(7, 123) } throws LocalNodeUnavailableException("Remove node")

        unavailableActions.requestRemoveNode(testScope, node) { afterRemoveCalled = true }
        realAlertManager.currentAlert.value?.onConfirm?.invoke()
        testScope.runCurrent()

        assertFalse(afterRemoveCalled)
        assertEquals(listOf("Couldn't send request. Try again."), snackbarManager.messages)
    }
}
