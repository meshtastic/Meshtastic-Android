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

import io.kotest.matchers.shouldBe

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for [CommonGetDiscoveredDevicesUseCase] covering TCP device discovery and node matching. */
class CommonGetDiscoveredDevicesUseCaseTest {
/*


    private lateinit var useCase: CommonGetDiscoveredDevicesUseCase
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var recentAddressesDataSource: RecentAddressesDataSource
    private lateinit var databaseManager: DatabaseManager
    private val recentAddressesFlow = MutableStateFlow<List<RecentAddress>>(emptyList())

    private fun setUp() {
        nodeRepository = FakeNodeRepository()

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
        useCase.invoke(showMock = false).test {
            val result = awaitItem()
            assertTrue(result.recentTcpDevices.isEmpty(), "No recent TCP devices when empty")
            assertTrue(result.usbDevices.isEmpty(), "No USB devices when showMock=false")
            assertTrue(result.bleDevices.isEmpty(), "No BLE devices in common use case")
            assertTrue(result.discoveredTcpDevices.isEmpty(), "No discovered TCP in common use case")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testRecentAddressesAreSortedByName() = runTest {
        setUp()
        recentAddressesFlow.value =
            listOf(RecentAddress("t192.168.1.100", "Zebra_Node"), RecentAddress("t192.168.1.101", "Alpha_Node"))

        useCase.invoke(showMock = false).test {
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
        useCase.invoke(showMock = true).test {
            val result = awaitItem()
            "Mock device should appear in usbDevices" shouldBe 1, result.usbDevices.size
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testHideMockNoDemo() = runTest {
        setUp()
        useCase.invoke(showMock = false).test {
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

        every { databaseManager.hasDatabaseFor("tMeshtastic_1234") } returns true

        recentAddressesFlow.value = listOf(RecentAddress("tMeshtastic_1234", "Meshtastic_1234"))

        useCase.invoke(showMock = false).test {
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

        every { databaseManager.hasDatabaseFor(any()) } returns false

        recentAddressesFlow.value = listOf(RecentAddress("tMeshtastic_1234", "Meshtastic_1234"))

        useCase.invoke(showMock = false).test {
            val result = awaitItem()
            result.recentTcpDevices.size shouldBe 1
            assertNull(result.recentTcpDevices[0].node, "Node should not be matched when no database")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testSuffixTooShortForMatch() = runTest {
        setUp()
        val testNode = TestDataFactory.createTestNode(num = 1, userId = "!test1234")
        nodeRepository.setNodes(listOf(testNode))

        every { databaseManager.hasDatabaseFor("tShort_ab") } returns true

        recentAddressesFlow.value = listOf(RecentAddress("tShort_ab", "Short_ab"))

        useCase.invoke(showMock = false).test {
            val result = awaitItem()
            result.recentTcpDevices.size shouldBe 1
            assertNull(result.recentTcpDevices[0].node, "Suffix 'ab' is too short (< 4) to match")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testReactiveNodeUpdates() = runTest {
        setUp()
        recentAddressesFlow.value = listOf(RecentAddress("t192.168.1.100", "Node_A"))

        useCase.invoke(showMock = false).test {
            val firstResult = awaitItem()
            firstResult.recentTcpDevices.size shouldBe 1

            // Add a node to the repository — flow should re-emit
            nodeRepository.setNodes(TestDataFactory.createTestNodes(2))
            val secondResult = awaitItem()
            "Recent TCP devices count unchanged" shouldBe 1, secondResult.recentTcpDevices.size
            cancelAndIgnoreRemainingEvents()
        }
    }

*/
}
