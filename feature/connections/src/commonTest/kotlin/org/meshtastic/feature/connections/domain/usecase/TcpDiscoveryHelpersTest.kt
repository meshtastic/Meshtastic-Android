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

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import io.kotest.matchers.shouldBe
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.feature.connections.model.DeviceListEntry
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Unit tests for the shared TCP discovery helper functions. */
class TcpDiscoveryHelpersTest {

    @Test
    fun `processTcpServices maps services to DeviceListEntry with shortname and id`() {
        val services =
            listOf(
                DiscoveredService(
                    name = "Meshtastic_abcd",
                    hostAddress = "192.168.1.10",
                    port = 4403,
                    txt = mapOf("shortname" to "Mesh".encodeToByteArray(), "id" to "!abcd".encodeToByteArray()),
                ),
            )

        val result = processTcpServices(services, emptyList())

        result.size shouldBe 1
        result[0].name shouldBe "Mesh_abcd"
        result[0].fullAddress shouldBe "t192.168.1.10"
    }

    @Test
    fun `processTcpServices uses default shortname when missing`() {
        val services =
            listOf(DiscoveredService(name = "TestDevice", hostAddress = "10.0.0.1", port = 4403, txt = emptyMap()))

        val result = processTcpServices(services, emptyList(), defaultShortName = "Meshtastic")

        result.size shouldBe 1
        result[0].name shouldBe "Meshtastic"
    }

    @Test
    fun `processTcpServices uses recent name over shortname`() {
        val services =
            listOf(
                DiscoveredService(
                    name = "Meshtastic_1234",
                    hostAddress = "192.168.1.50",
                    port = 4403,
                    txt = mapOf("shortname" to "Mesh".encodeToByteArray()),
                ),
            )
        val recentAddresses = listOf(RecentAddress("t192.168.1.50", "MyNode"))

        val result = processTcpServices(services, recentAddresses)

        result.size shouldBe 1
        result[0].name shouldBe "MyNode"
    }

    @Test
    fun `processTcpServices does not duplicate id in display name`() {
        val services =
            listOf(
                DiscoveredService(
                    name = "Meshtastic_1234",
                    hostAddress = "192.168.1.50",
                    port = 4403,
                    txt = mapOf("shortname" to "Mesh".encodeToByteArray(), "id" to "!1234".encodeToByteArray()),
                ),
            )
        val recentAddresses = listOf(RecentAddress("t192.168.1.50", "Mesh_1234"))

        val result = processTcpServices(services, recentAddresses)

        result.size shouldBe 1
        // Should NOT become "Mesh_1234_1234"
        result[0].name shouldBe "Mesh_1234"
    }

    @Test
    fun `processTcpServices results are sorted by name`() {
        val services =
            listOf(
                DiscoveredService("Z", "10.0.0.2", 4403, mapOf("shortname" to "Zulu".encodeToByteArray())),
                DiscoveredService("A", "10.0.0.1", 4403, mapOf("shortname" to "Alpha".encodeToByteArray())),
            )

        val result = processTcpServices(services, emptyList())

        result[0].name shouldBe "Alpha"
        result[1].name shouldBe "Zulu"
    }

    @Test
    fun `matchDiscoveredTcpNodes matches node by device id`() {
        val node = TestDataFactory.createTestNode(num = 1, userId = "!1234")
        val nodeDb = mapOf(1 to node)
        val entries = listOf(DeviceListEntry.Tcp("Mesh_1234", "t192.168.1.50"))
        val resolved =
            listOf(
                DiscoveredService(
                    name = "Meshtastic",
                    hostAddress = "192.168.1.50",
                    port = 4403,
                    txt = mapOf("id" to "!1234".encodeToByteArray()),
                ),
            )
        val databaseManager = mock<DatabaseManager> { every { hasDatabaseFor("t192.168.1.50") } returns true }

        val result = matchDiscoveredTcpNodes(entries, nodeDb, resolved, databaseManager)

        result.size shouldBe 1
        assertNotNull(result[0].node)
        result[0].node?.user?.id shouldBe "!1234"
    }

    @Test
    fun `matchDiscoveredTcpNodes returns null node when no database`() {
        val node = TestDataFactory.createTestNode(num = 1, userId = "!1234")
        val nodeDb = mapOf(1 to node)
        val entries = listOf(DeviceListEntry.Tcp("Mesh_1234", "t192.168.1.50"))
        val resolved =
            listOf(
                DiscoveredService(
                    name = "Meshtastic",
                    hostAddress = "192.168.1.50",
                    port = 4403,
                    txt = mapOf("id" to "!1234".encodeToByteArray()),
                ),
            )
        val databaseManager = mock<DatabaseManager> { every { hasDatabaseFor("t192.168.1.50") } returns false }

        val result = matchDiscoveredTcpNodes(entries, nodeDb, resolved, databaseManager)

        result.size shouldBe 1
        assertNull(result[0].node)
    }

    @Test
    fun `buildRecentTcpEntries filters out discovered addresses`() {
        val recentAddresses = listOf(RecentAddress("t192.168.1.50", "NodeA"), RecentAddress("t192.168.1.51", "NodeB"))
        val discoveredAddresses = setOf("t192.168.1.50")
        val databaseManager = mock<DatabaseManager> { every { hasDatabaseFor(any()) } returns false }

        val result = buildRecentTcpEntries(recentAddresses, discoveredAddresses, emptyMap(), databaseManager)

        result.size shouldBe 1
        result[0].name shouldBe "NodeB"
        result[0].fullAddress shouldBe "t192.168.1.51"
    }

    @Test
    fun `buildRecentTcpEntries matches node by suffix`() {
        val node = TestDataFactory.createTestNode(num = 1, userId = "!test1234")
        val recentAddresses = listOf(RecentAddress("tMeshtastic_1234", "Meshtastic_1234"))
        val databaseManager = mock<DatabaseManager> { every { hasDatabaseFor("tMeshtastic_1234") } returns true }

        val result = buildRecentTcpEntries(recentAddresses, emptySet(), mapOf(1 to node), databaseManager)

        result.size shouldBe 1
        assertNotNull(result[0].node)
        result[0].node?.user?.id shouldBe "!test1234"
    }

    @Test
    fun `buildRecentTcpEntries results are sorted by name`() {
        val recentAddresses = listOf(RecentAddress("t10.0.0.2", "Zebra"), RecentAddress("t10.0.0.1", "Alpha"))
        val databaseManager = mock<DatabaseManager> { every { hasDatabaseFor(any()) } returns false }

        val result = buildRecentTcpEntries(recentAddresses, emptySet(), emptyMap(), databaseManager)

        result[0].name shouldBe "Alpha"
        result[1].name shouldBe "Zebra"
    }
}
