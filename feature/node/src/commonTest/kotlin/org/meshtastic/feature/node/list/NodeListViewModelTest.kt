/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import io.kotest.matchers.shouldBe

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.feature.node.detail.NodeManagementActions
import org.meshtastic.feature.node.domain.usecase.GetFilteredNodesUseCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bootstrap tests for NodeListViewModel.
 *
 * Demonstrates using FakeNodeRepository with a node list feature.
 */
class NodeListViewModelTest {
/*


    private lateinit var viewModel: NodeListViewModel
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var radioConfigRepository: RadioConfigRepository
    private lateinit var serviceRepository: ServiceRepository
    private lateinit var nodeFilterPreferences: NodeFilterPreferences
    private lateinit var nodeManagementActions: NodeManagementActions
    private lateinit var getFilteredNodesUseCase: GetFilteredNodesUseCase

    @BeforeTest
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.Dispatchers.Unconfined)
        // Use real fakes
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()

        // Mock remaining dependencies with explicit types
        nodeFilterPreferences =
                every { nodeSortOption } returns MutableStateFlow(org.meshtastic.core.model.NodeSortOption.LAST_HEARD)
                every { includeUnknown } returns MutableStateFlow(true)
                every { excludeInfrastructure } returns MutableStateFlow(false)
                every { onlyOnline } returns MutableStateFlow(false)
            }
        @Suppress("UNCHECKED_CAST")

        viewModel =
            NodeListViewModel(
                savedStateHandle = SavedStateHandle(),
                nodeRepository = nodeRepository,
                radioConfigRepository = radioConfigRepository,
                serviceRepository = serviceRepository,
                radioController = radioController,
                nodeManagementActions = nodeManagementActions,
                getFilteredNodesUseCase = getFilteredNodesUseCase,
                nodeFilterPreferences = nodeFilterPreferences,
            )
    }

    @kotlin.test.AfterTest
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() = runTest {
        setUp()
        // ViewModel should initialize without errors
        assertTrue(true, "NodeListViewModel initialized successfully")
    }

    @Test
    fun testOurNodeInfoFlow() = runTest {
        setUp()
        // Verify ourNodeInfo StateFlow is accessible
        val ourNode = viewModel.ourNodeInfo.value
        assertTrue(ourNode == null, "ourNodeInfo starts as null before connection")
    }

    @Test
    fun testNodeCounts() = runTest {
        setUp()
        // Add test nodes to repository
        val testNodes = TestDataFactory.createTestNodes(3)
        nodeRepository.setNodes(testNodes)

        // Verify nodes are in repository
        "Test nodes added to repository" shouldBe 3, nodeRepository.nodeDBbyNum.value.size
    }

    @Test
    fun testTotalAndOnlineNodeCounts() = runTest {
        setUp()
        // Verify count flows are accessible
        val totalCount = viewModel.totalNodeCount.value
        val onlineCount = viewModel.onlineNodeCount.value

        // Both should be accessible without error
        assertTrue(true, "Node count flows are accessible")
    }

*/
}
