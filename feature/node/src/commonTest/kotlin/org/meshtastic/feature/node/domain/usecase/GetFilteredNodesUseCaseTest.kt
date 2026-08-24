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
package org.meshtastic.feature.node.domain.usecase

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.feature.node.list.NodeFilterState
import org.meshtastic.proto.Config
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetFilteredNodesUseCaseTest {

    private lateinit var nodeRepository: NodeRepository
    private lateinit var useCase: GetFilteredNodesUseCase

    @BeforeTest
    fun setUp() {
        nodeRepository = mock()
        useCase = GetFilteredNodesUseCase(nodeRepository)
    }

    private fun createNode(
        num: Int,
        role: Config.DeviceConfig.Role = Config.DeviceConfig.Role.CLIENT,
        ignored: Boolean = false,
        name: String = "Node$num",
        viaMqtt: Boolean = false,
    ): Node {
        val user = User(id = "!$num", long_name = name, short_name = "N$num", role = role)
        return Node(num = num, user = user, isIgnored = ignored, viaMqtt = viaMqtt)
    }

    @Test
    fun `invoke applies repository filters and returns nodes`() = runTest {
        // Arrange
        val nodes = listOf(createNode(1), createNode(2))
        val filter = NodeFilterState(filterText = "Node", includeUnknown = true)

        every {
            nodeRepository.getNodes(
                sort = NodeSortOption.LAST_HEARD,
                filter = "Node",
                includeUnknown = true,
                onlyOnline = false,
                onlyDirect = false,
            )
        } returns flowOf(nodes)

        // Act
        val result = useCase(filter, NodeSortOption.LAST_HEARD).first()

        // Assert
        assertEquals(2, result.size)
        assertEquals(1, result[0].num)
    }

    @Test
    fun `invoke filters out ignored nodes if showIgnored is false`() = runTest {
        // Arrange
        val normalNode = createNode(1, ignored = false)
        val ignoredNode = createNode(2, ignored = true)
        val filter = NodeFilterState(showIgnored = false)

        every { nodeRepository.getNodes(any(), any(), any(), any(), any()) } returns
            flowOf(listOf(normalNode, ignoredNode))

        // Act
        val result = useCase(filter, NodeSortOption.LAST_HEARD).first()

        // Assert
        assertEquals(1, result.size)
        assertEquals(1, result.first().num)
    }

    @Test
    fun `invoke filters out infrastructure nodes if excludeInfrastructure is true`() = runTest {
        // Arrange
        val clientNode = createNode(1, role = Config.DeviceConfig.Role.CLIENT)
        val routerNode = createNode(2, role = Config.DeviceConfig.Role.ROUTER)

        @Suppress("DEPRECATION")
        val repeaterNode = createNode(3, role = Config.DeviceConfig.Role.REPEATER)
        val clientBaseNode = createNode(4, role = Config.DeviceConfig.Role.CLIENT_BASE)
        val filter = NodeFilterState(excludeInfrastructure = true)

        every { nodeRepository.getNodes(any(), any(), any(), any(), any()) } returns
            flowOf(listOf(clientNode, routerNode, repeaterNode, clientBaseNode))

        // Act
        val result = useCase(filter, NodeSortOption.LAST_HEARD).first()

        // Assert
        // Should only keep the CLIENT node, others are infrastructure
        assertEquals(1, result.size)
        assertEquals(1, result.first().num)
    }

    @Test
    fun `invoke filters out MQTT nodes if excludeMqtt is true`() = runTest {
        // Arrange
        val loraNode = createNode(1, viaMqtt = false)
        val mqttNode = createNode(2, viaMqtt = true)
        val filter = NodeFilterState(excludeMqtt = true)

        every { nodeRepository.getNodes(any(), any(), any(), any(), any()) } returns flowOf(listOf(loraNode, mqttNode))

        // Act
        val result = useCase(filter, NodeSortOption.LAST_HEARD).first()

        // Assert
        assertEquals(1, result.size)
        assertEquals(1, result.first().num)
    }

    @Test
    fun `invoke keeps MQTT nodes if excludeMqtt is false`() = runTest {
        // Arrange
        val loraNode = createNode(1, viaMqtt = false)
        val mqttNode = createNode(2, viaMqtt = true)
        val filter = NodeFilterState(excludeMqtt = false)

        every { nodeRepository.getNodes(any(), any(), any(), any(), any()) } returns flowOf(listOf(loraNode, mqttNode))

        // Act
        val result = useCase(filter, NodeSortOption.LAST_HEARD).first()

        // Assert
        assertEquals(2, result.size)
    }
}
