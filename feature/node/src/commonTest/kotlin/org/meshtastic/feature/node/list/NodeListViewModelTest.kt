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
package org.meshtastic.feature.node.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.feature.node.detail.NodeManagementActions
import org.meshtastic.feature.node.domain.usecase.GetFilteredNodesUseCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NodeListViewModelTest {

    private lateinit var viewModel: NodeListViewModel
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var radioInterfaceService: FakeRadioInterfaceService
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val nodeFilterPreferences: NodeFilterPreferences = mock(MockMode.autofill)
    private val nodeManagementActions: NodeManagementActions = mock(MockMode.autofill)
    private val getFilteredNodesUseCase: GetFilteredNodesUseCase = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()
        radioInterfaceService = FakeRadioInterfaceService()

        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(org.meshtastic.proto.LocalConfig())
        every { radioConfigRepository.deviceProfileFlow } returns MutableStateFlow(org.meshtastic.proto.DeviceProfile())
        every { serviceRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)

        every { nodeFilterPreferences.nodeSortOption } returns MutableStateFlow(NodeSortOption.LAST_HEARD)
        every { nodeFilterPreferences.includeUnknown } returns MutableStateFlow(true)
        every { nodeFilterPreferences.excludeInfrastructure } returns MutableStateFlow(false)
        every { nodeFilterPreferences.onlyOnline } returns MutableStateFlow(false)
        every { nodeFilterPreferences.onlyDirect } returns MutableStateFlow(false)
        every { nodeFilterPreferences.showIgnored } returns MutableStateFlow(false)
        every { nodeFilterPreferences.excludeMqtt } returns MutableStateFlow(false)

        every { getFilteredNodesUseCase(any(), any()) } returns MutableStateFlow(emptyList())

        viewModel = createViewModel()
    }

    private fun createViewModel() = NodeListViewModel(
        savedStateHandle = SavedStateHandle(),
        nodeRepository = nodeRepository,
        radioConfigRepository = radioConfigRepository,
        serviceRepository = serviceRepository,
        radioController = radioController,
        radioInterfaceService = radioInterfaceService,
        nodeManagementActions = nodeManagementActions,
        getFilteredNodesUseCase = getFilteredNodesUseCase,
        nodeFilterPreferences = nodeFilterPreferences,
    )

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `nodeList emits updates when repository changes`() = runTest {
        val nodesFlow = MutableStateFlow<List<Node>>(emptyList())
        every { getFilteredNodesUseCase(any(), any()) } returns nodesFlow

        val vm = createViewModel()
        vm.nodeList.test {
            // Initial value from stateIn
            assertEquals(emptyList(), awaitItem())

            // Trigger update
            val testNodes = TestDataFactory.createTestNodes(3)
            nodesFlow.value = testNodes

            assertEquals(3, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connectionState reflects serviceRepository state`() = runTest {
        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        every { serviceRepository.connectionState } returns stateFlow

        val vm = createViewModel()
        vm.connectionState.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())
            stateFlow.value = ConnectionState.Connected
            assertEquals(ConnectionState.Connected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
