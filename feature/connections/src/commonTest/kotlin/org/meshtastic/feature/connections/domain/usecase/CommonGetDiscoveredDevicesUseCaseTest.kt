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
package org.meshtastic.feature.connections.domain.usecase

import app.cash.turbine.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.util.normalizeAddress
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.testing.FakeDatabaseManager
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.feature.connections.data.RecentAddressesSource
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.UsbDeviceData
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for [CommonGetDiscoveredDevicesUseCase] covering TCP device discovery and node matching. */
class CommonGetDiscoveredDevicesUseCaseTest {
    private lateinit var useCase: CommonGetDiscoveredDevicesUseCase
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var recentAddressesDataSource: RecentAddressesSource
    private lateinit var databaseManager: FakeDatabaseManager
    private val identityFlow = MutableStateFlow<ConnectionIdentity?>(null)
    private val nodeManager: NodeManager = mock { every { connectionIdentity } returns identityFlow }
    private val recentAddressesFlow = MutableStateFlow<List<RecentAddress>>(emptyList())
    private val resolvedServicesFlow = MutableStateFlow<List<DiscoveredService>>(emptyList())

    private fun setUp() {
        nodeRepository = FakeNodeRepository()
        recentAddressesDataSource = mock { every { recentAddresses } returns recentAddressesFlow }
        databaseManager = FakeDatabaseManager()

        useCase =
            CommonGetDiscoveredDevicesUseCase(
                recentAddressesDataSource = recentAddressesDataSource,
                nodeRepository = nodeRepository,
                databaseManager = databaseManager,
                nodeManager = nodeManager,
            )
    }

    @Test
    fun testEmptyRecentAddresses() = runTest {
        setUp()
        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolvedServicesFlow).test {
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

        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            result.recentTcpDevices.size shouldBe 2
            result.recentTcpDevices[0].name shouldBe "Alpha_Node"
            result.recentTcpDevices[1].name shouldBe "Zebra_Node"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testShowMockAddsDemoOnly() = runTest {
        setUp()
        useCase.invoke(showMock = true, showReplay = false, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            result.virtualDevices.map { it::class } shouldBe listOf(DeviceListEntry.Mock::class)
            assertTrue(result.usbDevices.isEmpty(), "Demo Mode is not a USB device")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testShowReplayAddsReplayAlongsideDemo() = runTest {
        setUp()
        useCase.invoke(showMock = true, showReplay = true, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            result.virtualDevices.map { it::class } shouldBe
                listOf(DeviceListEntry.Mock::class, DeviceListEntry.Replay::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Replay is nested inside the demo gate: it is never offered on its own. */
    @Test
    fun testShowReplayIgnoredWhenMockHidden() = runTest {
        setUp()
        useCase.invoke(showMock = false, showReplay = true, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            assertTrue(result.virtualDevices.isEmpty(), "No replay device when showMock=false")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testHideMockNoDemo() = runTest {
        setUp()
        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolvedServicesFlow).test {
            val result = awaitItem()
            assertTrue(result.virtualDevices.isEmpty(), "No mock device when showMock=false")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent radio shows the node it reported at association and never a node matching its name suffix`() = runTest {
        setUp()
        // Issue #8228: the radio advertises SD7T_964e; on firmware 2.8 its node number is crc32 of its key, while
        // another node on the mesh, ptzm, has an id ending in 964e.
        val sd7t = TestDataFactory.createTestNode(num = 0x1A2B3C4D, userId = "!1a2b3c4d", longName = "SD7T")
        val ptzm = TestDataFactory.createTestNode(num = 0x5E6F964E, userId = "!5e6f964e", longName = "ptzm")
        nodeRepository.setNodes(listOf(sd7t, ptzm))
        databaseManager.existingDatabases += "t192.168.1.50"
        recentAddressesFlow.value = listOf(RecentAddress("t192.168.1.50", "SD7T_964e"))

        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolvedServicesFlow).test {
            awaitItem().recentTcpDevices.single().node shouldBe null

            databaseManager.switchActiveDatabase("t192.168.1.50")
            databaseManager.associateDevice("t192.168.1.50", sd7t.num, deviceId = null) { true }

            awaitItem().recentTcpDevices.single().node shouldBe sd7t
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discovered radio announcing no id shows the node recorded for its address`() = runTest {
        setUp()
        val sd7t = TestDataFactory.createTestNode(num = 0x1A2B3C4D, userId = "!1a2b3c4d")
        nodeRepository.setNodes(listOf(sd7t))
        databaseManager.localNodeNumsFlow.value = mapOf(normalizeAddress("t192.168.1.50") to sd7t.num)
        resolvedServicesFlow.value =
            listOf(DiscoveredService(name = "Meshtastic", hostAddress = "192.168.1.50", port = 4403, txt = emptyMap()))

        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolvedServicesFlow).test {
            awaitItem().discoveredTcpDevices.single().node shouldBe sd7t
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `serial port reused by another radio shows only the live node never the one recorded for the port`() = runTest {
        setUp()
        // A desktop serial address is the port name, so the radio last connected on it need not be the one there now.
        val first = TestDataFactory.createTestNode(num = 0x1A2B3C4D, userId = "!1a2b3c4d", longName = "First")
        val second = TestDataFactory.createTestNode(num = 0x0A0B0C0D, userId = "!0a0b0c0d", longName = "Second")
        nodeRepository.setNodes(listOf(first, second))
        val port = "s/dev/ttyUSB0"
        databaseManager.existingDatabases += port
        databaseManager.localNodeNumsFlow.value = mapOf(normalizeAddress(port) to first.num)
        val usbScanner =
            object : UsbScanner {
                override fun scanUsbDevices() =
                    flowOf(listOf(DeviceListEntry.Usb(object : UsbDeviceData {}, "/dev/ttyUSB0", port, bonded = true)))
            }
        useCase =
            CommonGetDiscoveredDevicesUseCase(
                recentAddressesDataSource = recentAddressesDataSource,
                nodeRepository = nodeRepository,
                databaseManager = databaseManager,
                nodeManager = nodeManager,
                usbScanner = usbScanner,
            )

        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolvedServicesFlow).test {
            awaitItem().usbDevices.single().node shouldBe null

            identityFlow.value = ConnectionIdentity(2L, port, second.num, deviceId = null)

            awaitItem().usbDevices.single().node shouldBe second
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testNodeNotMatchedWhenNoDatabaseExists() = runTest {
        setUp()
        val testNode = TestDataFactory.createTestNode(num = 1, userId = "!test1234")
        nodeRepository.setNodes(listOf(testNode))

        recentAddressesFlow.value = listOf(RecentAddress("tMeshtastic_1234", "Meshtastic_1234"))

        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolvedServicesFlow).test {
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

        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolvedServicesFlow).test {
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

        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolvedServicesFlow).test {
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

        databaseManager.existingDatabases += "t192.168.1.50"

        resolvedServicesFlow.value =
            listOf(
                DiscoveredService(
                    name = "Meshtastic_1234",
                    hostAddress = "192.168.1.50",
                    port = 4403,
                    txt = mapOf("id" to "!1234".encodeToByteArray(), "shortname" to "Mesh".encodeToByteArray()),
                ),
            )

        useCase.invoke(showMock = false, showReplay = false, resolvedList = resolvedServicesFlow).test {
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

        useCase.invoke(showMock = false, showReplay = false, resolvedList = flowOf(emptyList())).test {
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
        useCase.invoke(showMock = true, showReplay = true, resolvedList = flowOf(emptyList())).test {
            val result = awaitItem()
            result.virtualDevices.map { it::class } shouldBe
                listOf(DeviceListEntry.Mock::class, DeviceListEntry.Replay::class)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
