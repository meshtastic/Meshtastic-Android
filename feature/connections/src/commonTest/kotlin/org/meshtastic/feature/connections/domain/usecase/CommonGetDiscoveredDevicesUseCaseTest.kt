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
package org.meshtastic.feature.connections.domain.usecase

import app.cash.turbine.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for [CommonGetDiscoveredDevicesUseCase] covering TCP device discovery and node matching. */
class CommonGetDiscoveredDevicesUseCaseTest {
    private lateinit var useCase: CommonGetDiscoveredDevicesUseCase
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var recentAddressesDataSource: RecentAddressesDataSource
    private lateinit var databaseManager: DatabaseManager
    private val recentAddressesFlow = MutableStateFlow<List<RecentAddress>>(emptyList())
    private val resolvedServicesFlow = MutableStateFlow<List<DiscoveredService>>(emptyList())

    private fun setUp() {
        nodeRepository = FakeNodeRepository()
        recentAddressesDataSource = mock { every { recentAddresses } returns recentAddressesFlow }
        databaseManager = mock { every { hasDatabaseFor(any()) } returns false }

        useCase =
            CommonGetDiscoveredDevicesUseCase(
                recentAddressesDataSource = recentAddressesDataSource,
                nodeRepository = nodeRepository,
                databaseManager = databaseManager,
            )
    }

    @Test
    fun testEmptyRecentAddresses() = runTest {
        setUp()
        useCase.invoke(showMock = false, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            assertTrue(result.recentTcpDevices.isEmpty(), "No recent TCP devices when empty")
            assertTrue(result.usbDevices.isEmpty(), "No USB devices when showMock=false")
            assertTrue(result.discoveredTcpDevices.isEmpty(), "No discovered TCP in common use case")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testRecentAddressesAreSortedByName() = runTest {
        setUp()
        recentAddressesFlow.value =
            listOf(RecentAddress("t192.168.1.100", "Zebra_Node"), RecentAddress("t192.168.1.101", "Alpha_Node"))

        useCase.invoke(showMock = false, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            result.recentTcpDevices.size shouldBe 2
            result.recentTcpDevices[0].name shouldBe "Alpha_Node"
            result.recentTcpDevices[1].name shouldBe "Zebra_Node"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testShowMockAddsDemo() = runTest {
        setUp()
        useCase.invoke(showMock = true, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            result.usbDevices.size shouldBe 1
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testHideMockNoDemo() = runTest {
        setUp()
        useCase.invoke(showMock = false, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            assertTrue(result.usbDevices.isEmpty(), "No mock device when showMock=false")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testNodeMatchingWithSuffix() = runTest {
        setUp()
        val testNode = TestDataFactory.createTestNode(num = 1, userId = "!test1234", longName = "Test Node")
        nodeRepository.setNodes(listOf(testNode))

        databaseManager = mock { every { hasDatabaseFor("tMeshtastic_1234") } returns true }
        useCase =
            CommonGetDiscoveredDevicesUseCase(
                recentAddressesDataSource = recentAddressesDataSource,
                nodeRepository = nodeRepository,
                databaseManager = databaseManager,
            )

        recentAddressesFlow.value = listOf(RecentAddress("tMeshtastic_1234", "Meshtastic_1234"))

        useCase.invoke(showMock = false, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            result.recentTcpDevices.size shouldBe 1
            assertNotNull(result.recentTcpDevices[0].node, "Node should be matched by suffix")
            result.recentTcpDevices[0].node?.user?.id shouldBe testNode.user.id
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testNodeNotMatchedWhenNoDatabaseExists() = runTest {
        setUp()
        val testNode = TestDataFactory.createTestNode(num = 1, userId = "!test1234")
        nodeRepository.setNodes(listOf(testNode))

        recentAddressesFlow.value = listOf(RecentAddress("tMeshtastic_1234", "Meshtastic_1234"))

        useCase.invoke(showMock = false, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            result.recentTcpDevices.size shouldBe 1
            assertNull(result.recentTcpDevices[0].node, "Node should not be matched when no database")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testReactiveNodeUpdates() = runTest {
        setUp()
        recentAddressesFlow.value = listOf(RecentAddress("t192.168.1.100", "Node_A"))

        useCase.invoke(showMock = false, resolvedList = resolvedServicesFlow).test {
            val firstResult = awaitItem()
            firstResult.recentTcpDevices.size shouldBe 1

            // Add a node to the repository — flow should re-emit
            nodeRepository.setNodes(TestDataFactory.createTestNodes(2))
            val secondResult = awaitItem()
            secondResult.recentTcpDevices.size shouldBe 1
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testDiscoveredTcpDevices() = runTest {
        setUp()
        resolvedServicesFlow.value =
            listOf(
                DiscoveredService(
                    name = "Meshtastic_1234",
                    hostAddress = "192.168.1.50",
                    port = 4403,
                    txt = mapOf("id" to "!1234".encodeToByteArray(), "shortname" to "Mesh".encodeToByteArray()),
                ),
            )

        useCase.invoke(showMock = false, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            result.discoveredTcpDevices.size shouldBe 1
            result.discoveredTcpDevices[0].name shouldBe "Mesh_1234"
            result.discoveredTcpDevices[0].fullAddress shouldBe "t192.168.1.50"

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testDiscoveredTcpDeviceMatchesNode() = runTest {
        setUp()
        val testNode = TestDataFactory.createTestNode(num = 1, userId = "!1234", longName = "Mesh")
        nodeRepository.setNodes(listOf(testNode))

        databaseManager = mock { every { hasDatabaseFor("t192.168.1.50") } returns true }
        useCase =
            CommonGetDiscoveredDevicesUseCase(
                recentAddressesDataSource = recentAddressesDataSource,
                nodeRepository = nodeRepository,
                databaseManager = databaseManager,
            )

        resolvedServicesFlow.value =
            listOf(
                DiscoveredService(
                    name = "Meshtastic_1234",
                    hostAddress = "192.168.1.50",
                    port = 4403,
                    txt = mapOf("id" to "!1234".encodeToByteArray(), "shortname" to "Mesh".encodeToByteArray()),
                ),
            )

        useCase.invoke(showMock = false, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            result.discoveredTcpDevices.size shouldBe 1
            assertNotNull(result.discoveredTcpDevices[0].node)
            result.discoveredTcpDevices[0].node?.user?.id shouldBe "!1234"

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testEmptyResolvedListReturnsNoDiscoveredDevices() = runTest {
        setUp()
        recentAddressesFlow.value = listOf(RecentAddress("t192.168.1.100", "Recent_Node"))

        useCase.invoke(showMock = false, resolvedList = flowOf(emptyList())).test {
            val result = awaitItem()
            assertTrue(result.discoveredTcpDevices.isEmpty(), "No NSD devices when resolvedList is empty")
            result.recentTcpDevices.size shouldBe 1
            result.recentTcpDevices[0].name shouldBe "Recent_Node"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testEmptyResolvedListIncludesMock() = runTest {
        setUp()
        useCase.invoke(showMock = true, resolvedList = flowOf(emptyList())).test {
            val result = awaitItem()
            result.usbDevices.size shouldBe 1
            cancelAndIgnoreRemainingEvents()
        }
    }
}
