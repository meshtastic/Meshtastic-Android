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

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.domain.usecase.session.EnsureRemoteAdminSessionUseCase
import org.meshtastic.core.domain.usecase.session.ObserveRemoteAdminSessionStatusUseCase
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.QueryController
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private val TEST_KEY = ByteArray(32) { 0x2B.toByte() }.toByteString()

@OptIn(ExperimentalCoroutinesApi::class)
class HandleNodeActionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val nodeManagementActions: NodeManagementActions = mock()
    private val nodeRequestActions: NodeRequestActions = mock()
    private val queryController: QueryController = mock()
    private val getNodeDetailsUseCase: GetNodeDetailsUseCase = mock()
    private val packetRepository: PacketRepository = mock()
    private val ensureRemoteAdminSession: EnsureRemoteAdminSessionUseCase = mock()
    private val observeRemoteAdminSessionStatus: ObserveRemoteAdminSessionStatusUseCase = mock()
    private val snackbarManager: SnackbarManager = mock()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getNodeDetailsUseCase(any()) } returns emptyFlow()
        every { packetRepository.getContacts() } returns flowOf(emptyMap())
        every { observeRemoteAdminSessionStatus(any()) } returns flowOf(SessionStatus.NoSession)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `remove action delegates to viewModel and does not navigate up immediately`() = runTest(testDispatcher) {
        val node = Node(num = 1234, user = User(id = "!1234"))
        every { nodeManagementActions.requestRemoveNode(any(), any(), any()) } returns Unit
        val viewModel = createViewModel()
        var navigateUpCalled = false

        handleNodeAction(
            action = NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.Remove(node)),
            uiState = NodeDetailUiState(),
            navigateToMessages = {},
            onNavigateUp = { navigateUpCalled = true },
            onNavigate = {},
            viewModel = viewModel,
        )

        verify { nodeManagementActions.requestRemoveNode(any(), node, any()) }
        assertFalse(navigateUpCalled)
    }

    @Test
    fun `direct message to a keyless node with a thread opens that thread`() = runTest(testDispatcher) {
        // The message action is only visible for a keyless node because a thread exists. getDirectMessageRoute
        // would fall back to node.channel and open a different, empty conversation.
        val node = Node(num = 1234, user = User(id = "!000004d2"))
        val viewModel = createViewModel()
        var route: String? = null

        handleNodeAction(
            action = NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.DirectMessage(node)),
            uiState = NodeDetailUiState(existingContactKey = "8!000004d2"),
            navigateToMessages = { route = it },
            onNavigateUp = {},
            onNavigate = {},
            viewModel = viewModel,
        )

        assertEquals("8!000004d2", route)
    }

    @Test
    fun `direct message to a keyed node uses the computed route rather than an old thread`() = runTest(testDispatcher) {
        val node = Node(num = 1234, user = User(id = "!000004d2", public_key = TEST_KEY))
        val ourNode = Node(num = 9999, user = User(id = "!0000270f", public_key = TEST_KEY))
        val viewModel = createViewModel()
        var route: String? = null

        handleNodeAction(
            action = NodeDetailAction.HandleNodeMenuAction(NodeMenuAction.DirectMessage(node)),
            uiState = NodeDetailUiState(ourNode = ourNode, existingContactKey = "0!000004d2"),
            navigateToMessages = { route = it },
            onNavigateUp = {},
            onNavigate = {},
            viewModel = viewModel,
        )

        assertEquals("8!000004d2", route)
    }

    private fun createViewModel() = NodeDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("destNum" to 1234)),
        nodeManagementActions = nodeManagementActions,
        nodeRequestActions = nodeRequestActions,
        queryController = queryController,
        getNodeDetailsUseCase = getNodeDetailsUseCase,
        packetRepository = packetRepository,
        ensureRemoteAdminSession = ensureRemoteAdminSession,
        observeRemoteAdminSessionStatus = observeRemoteAdminSessionStatus,
        snackbarManager = snackbarManager,
    )
}
