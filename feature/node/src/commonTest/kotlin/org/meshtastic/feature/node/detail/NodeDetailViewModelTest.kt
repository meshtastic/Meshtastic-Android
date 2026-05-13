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

import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.domain.usecase.session.EnsureRemoteAdminSessionUseCase
import org.meshtastic.core.domain.usecase.session.EnsureSessionResult
import org.meshtastic.core.domain.usecase.session.ObserveRemoteAdminSessionStatusUseCase
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.connect_radio_for_remote_admin
import org.meshtastic.core.resources.remote_admin_unreachable
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class NodeDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: NodeDetailViewModel
    private val nodeManagementActions: NodeManagementActions = mock()
    private val nodeRequestActions: NodeRequestActions = mock()
    private val serviceRepository: ServiceRepository = mock()
    private val getNodeDetailsUseCase: GetNodeDetailsUseCase = mock()
    private val ensureRemoteAdminSession: EnsureRemoteAdminSessionUseCase = mock()
    private val observeRemoteAdminSessionStatus: ObserveRemoteAdminSessionStatusUseCase = mock()
    private val snackbarManager = RecordingSnackbarManager()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { getNodeDetailsUseCase(any()) } returns emptyFlow()
        every { observeRemoteAdminSessionStatus(any()) } returns flowOf(SessionStatus.NoSession)
        snackbarManager.messages.clear()
        NodeDetailUiTextResolver.resolve = { text ->
            when (text) {
                is UiText.DynamicString -> text.value

                is UiText.Resource ->
                    when (text.res) {
                        Res.string.connect_radio_for_remote_admin -> "Connect to a radio to administer remote nodes."
                        Res.string.remote_admin_unreachable -> "Could not reach node — try again or move closer."
                        else -> error("Unexpected UiText resource in test: ${text.res}")
                    }
            }
        }

        viewModel = createViewModel(1234)
    }

    private fun createViewModel(nodeId: Int?) = NodeDetailViewModel(
        savedStateHandle = SavedStateHandle(if (nodeId != null) mapOf("destNum" to nodeId) else emptyMap()),
        nodeManagementActions = nodeManagementActions,
        nodeRequestActions = nodeRequestActions,
        serviceRepository = serviceRepository,
        getNodeDetailsUseCase = getNodeDetailsUseCase,
        ensureRemoteAdminSession = ensureRemoteAdminSession,
        observeRemoteAdminSessionStatus = observeRemoteAdminSessionStatus,
        snackbarManager = snackbarManager,
    )

    private class RecordingSnackbarManager : SnackbarManager() {
        val messages = mutableListOf<String>()

        override fun showSnackbar(
            message: String,
            actionLabel: String?,
            withDismissAction: Boolean,
            duration: SnackbarDuration,
            onAction: (() -> Unit)?,
        ) {
            messages += message
        }
    }

    @AfterTest
    fun tearDown() {
        NodeDetailUiTextResolver.resolve = { it.resolve() }
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `uiState emits updates from useCase`() = runTest(testDispatcher) {
        val node = Node(num = 1234, user = User(id = "!1234"))
        val stateFlow = MutableStateFlow(NodeDetailUiState(node = node))
        every { getNodeDetailsUseCase(1234) } returns stateFlow

        val vm = createViewModel(1234)

        vm.uiState.test {
            // State from useCase (delivered immediately due to UnconfinedTestDispatcher)
            val state = awaitItem()
            assertEquals(1234, state.node?.num)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handleNodeMenuAction delegates to nodeManagementActions for Mute`() = runTest(testDispatcher) {
        val node = Node(num = 1234, user = User(id = "!1234"))
        every { nodeManagementActions.requestMuteNode(any(), any()) } returns Unit

        viewModel.handleNodeMenuAction(NodeMenuAction.Mute(node))

        verify { nodeManagementActions.requestMuteNode(any(), node) }
    }

    @Test
    fun `handleNodeMenuAction delegates to nodeRequestActions for Traceroute`() = runTest(testDispatcher) {
        val node = Node(num = 1234, user = User(id = "!1234", long_name = "Test Node"))
        every { nodeRequestActions.requestTraceroute(any(), any(), any()) } returns Unit

        viewModel.handleNodeMenuAction(NodeMenuAction.TraceRoute(node))

        verify { nodeRequestActions.requestTraceroute(any(), 1234, "Test Node") }
    }

    @Test
    fun `openRemoteAdmin navigates to settings when session is already active`() = runTest(testDispatcher) {
        everySuspend { ensureRemoteAdminSession(1234) } returns EnsureSessionResult.AlreadyActive

        viewModel.navigationEvents.test {
            viewModel.openRemoteAdmin(1234)

            assertEquals(SettingsRoute.Settings(1234), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        verifySuspend { ensureRemoteAdminSession(1234) }
    }

    @Test
    fun `openRemoteAdmin shows disconnected snackbar when radio is disconnected`() = runTest(testDispatcher) {
        everySuspend { ensureRemoteAdminSession(1234) } returns EnsureSessionResult.Disconnected
        val expectedMessage = "Connect to a radio to administer remote nodes."

        viewModel.openRemoteAdmin(1234)
        runCurrent()

        assertEquals(listOf(expectedMessage), snackbarManager.messages)
        verifySuspend { ensureRemoteAdminSession(1234) }
    }

    @Test
    fun `openRemoteAdmin shows timeout snackbar when node is unreachable`() = runTest(testDispatcher) {
        everySuspend { ensureRemoteAdminSession(1234) } returns EnsureSessionResult.Timeout
        val expectedMessage = "Could not reach node — try again or move closer."

        viewModel.openRemoteAdmin(1234)
        runCurrent()

        assertEquals(listOf(expectedMessage), snackbarManager.messages)
        verifySuspend { ensureRemoteAdminSession(1234) }
    }
}
