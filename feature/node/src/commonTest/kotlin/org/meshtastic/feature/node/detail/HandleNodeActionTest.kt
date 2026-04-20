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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.feature.node.component.NodeMenuAction
import org.meshtastic.feature.node.domain.usecase.GetNodeDetailsUseCase
import org.meshtastic.feature.node.model.NodeDetailAction
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class HandleNodeActionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val nodeManagementActions: NodeManagementActions = mock()
    private val nodeRequestActions: NodeRequestActions = mock()
    private val serviceRepository: ServiceRepository = mock()
    private val getNodeDetailsUseCase: GetNodeDetailsUseCase = mock()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getNodeDetailsUseCase(any()) } returns emptyFlow()
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

    private fun createViewModel() = NodeDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("destNum" to 1234)),
        nodeManagementActions = nodeManagementActions,
        nodeRequestActions = nodeRequestActions,
        serviceRepository = serviceRepository,
        getNodeDetailsUseCase = getNodeDetailsUseCase,
    )
}
