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
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioNodeSnapshot
import org.meshtastic.core.testing.FakeDeviceHardwareRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.feature.node.detail.NodeManagementActions
import org.meshtastic.feature.node.detail.NodeRequestActions
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
    private val connectionStateProvider: ConnectionStateProvider = mock(MockMode.autofill)
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val nodeFilterPreferences: NodeFilterPreferences = mock(MockMode.autofill)
    private val nodeManagementActions: NodeManagementActions = mock(MockMode.autofill)
    private val nodeRequestActions: NodeRequestActions = mock(MockMode.autofill)
    private val getFilteredNodesUseCase: GetFilteredNodesUseCase = mock(MockMode.autofill)
    private lateinit var connectionState: MutableStateFlow<ConnectionState>
    private lateinit var currentRadioNodeSnapshot: MutableStateFlow<RadioNodeSnapshot?>
    private lateinit var onlyOnline: MutableStateFlow<Boolean>
    private lateinit var onlyDirect: MutableStateFlow<Boolean>

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()
        radioInterfaceService = FakeRadioInterfaceService()
        connectionState = MutableStateFlow(ConnectionState.Disconnected)
        currentRadioNodeSnapshot = MutableStateFlow(null)
        onlyOnline = MutableStateFlow(false)
        onlyDirect = MutableStateFlow(false)

        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(org.meshtastic.proto.LocalConfig())
        every { radioConfigRepository.deviceProfileFlow } returns MutableStateFlow(org.meshtastic.proto.DeviceProfile())
        every { connectionStateProvider.connectionState } returns connectionState
        every { nodeManager.currentRadioNodeSnapshot } returns currentRadioNodeSnapshot

        every { nodeFilterPreferences.nodeSortOption } returns MutableStateFlow(NodeSortOption.LAST_HEARD)
        every { nodeFilterPreferences.includeUnknown } returns MutableStateFlow(true)
        every { nodeFilterPreferences.excludeInfrastructure } returns MutableStateFlow(false)
        every { nodeFilterPreferences.onlyOnline } returns onlyOnline
        every { nodeFilterPreferences.onlyDirect } returns onlyDirect
        every { nodeFilterPreferences.showIgnored } returns MutableStateFlow(false)
        every { nodeFilterPreferences.excludeMqtt } returns MutableStateFlow(false)

        every { getFilteredNodesUseCase(any(), any()) } returns MutableStateFlow(emptyList())

        viewModel = createViewModel()
    }

    private fun createViewModel() = NodeListViewModel(
        savedStateHandle = SavedStateHandle(),
        nodeRepository = nodeRepository,
        nodeManager = nodeManager,
        radioConfigRepository = radioConfigRepository,
        connectionStateProvider = connectionStateProvider,
        adminController = radioController,
        radioInterfaceService = radioInterfaceService,
        deviceHardwareRepository = FakeDeviceHardwareRepository(),
        nodeManagementActions = nodeManagementActions,
        nodeRequestActions = nodeRequestActions,
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
    fun `isActive is false for the default filter`() {
        assertEquals(false, NodeFilterState().isActive)
    }

    @Test
    fun `isActive is true when unknown nodes are opted out`() {
        // includeUnknown defaults to true, so a persisted false is a user-applied narrowing filter.
        assertEquals(true, NodeFilterState(includeUnknown = false).isActive)
    }

    @Test
    fun `connectionState reflects serviceRepository state`() = runTest {
        val stateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        every { connectionStateProvider.connectionState } returns stateFlow

        val vm = createViewModel()
        vm.connectionState.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())
            stateFlow.value = ConnectionState.Connected
            assertEquals(ConnectionState.Connected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completed snapshot limits online count while connected`() = runTest {
        nodeRepository.setNodes(
            listOf(Node(num = 1, lastHeard = Int.MAX_VALUE), Node(num = 2, lastHeard = Int.MAX_VALUE)),
        )
        connectionState.value = ConnectionState.Connected
        currentRadioNodeSnapshot.value = RadioNodeSnapshot(sessionGeneration = 7, nodeNums = setOf(1))

        val vm = createViewModel()

        vm.onlineNodeCount.test {
            var count = awaitItem()
            if (count != 1) count = awaitItem()
            assertEquals(1, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `snapshot does not narrow count while disconnected`() = runTest {
        nodeRepository.setNodes(
            listOf(Node(num = 1, lastHeard = Int.MAX_VALUE), Node(num = 2, lastHeard = Int.MAX_VALUE)),
        )
        currentRadioNodeSnapshot.value = RadioNodeSnapshot(sessionGeneration = 7, nodeNums = setOf(1))

        val vm = createViewModel()

        vm.onlineNodeCount.test {
            var count = awaitItem()
            if (count != 2) count = awaitItem()
            assertEquals(2, count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only online excludes retained-only nodes after completed snapshot`() = runTest {
        onlyOnline.value = true
        connectionState.value = ConnectionState.Connected
        currentRadioNodeSnapshot.value = RadioNodeSnapshot(sessionGeneration = 7, nodeNums = setOf(1))
        every { getFilteredNodesUseCase(any(), any()) } returns MutableStateFlow(listOf(Node(num = 1), Node(num = 2)))

        val vm = createViewModel()

        vm.nodeList.test {
            var nodeNums = awaitItem().map { it.num }
            if (nodeNums != listOf(1)) nodeNums = awaitItem().map { it.num }
            assertEquals(listOf(1), nodeNums)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only direct excludes retained-only nodes after completed snapshot`() = runTest {
        onlyDirect.value = true
        connectionState.value = ConnectionState.Connected
        currentRadioNodeSnapshot.value = RadioNodeSnapshot(sessionGeneration = 7, nodeNums = setOf(1))
        every { getFilteredNodesUseCase(any(), any()) } returns MutableStateFlow(listOf(Node(num = 1), Node(num = 2)))

        val vm = createViewModel()

        vm.nodeList.test {
            var nodeNums = awaitItem().map { it.num }
            if (nodeNums != listOf(1)) nodeNums = awaitItem().map { it.num }
            assertEquals(listOf(1), nodeNums)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
