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
package org.meshtastic.core.data.ai

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class AiFunctionProviderImplTest {

    private val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    private val serviceRepository: ServiceRepository =
        mock(MockMode.autofill) { every { connectionState } returns this@AiFunctionProviderImplTest.connectionState }
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val sendMessageUseCase: SendMessageUseCase = mock(MockMode.autofill)
    private val fuzzyNameResolver = FuzzyNameResolver(nodeRepository, radioConfigRepository)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val clock = TestClock(Instant.fromEpochSeconds(1_700_000_000))
    private val rateLimiter = RateLimiter(clock)

    private fun createProvider() = AiFunctionProviderImpl(
        serviceRepository = serviceRepository,
        nodeRepository = nodeRepository,
        radioConfigRepository = radioConfigRepository,
        sendMessageUseCase = sendMessageUseCase,
        fuzzyNameResolver = fuzzyNameResolver,
        packetRepository = packetRepository,
        rateLimiter = rateLimiter,
        clock = clock,
    )

    // --- getNodeDetails tests ---

    @Test
    fun getNodeDetails_returns_not_connected_when_disconnected() = runTest {
        connectionState.value = ConnectionState.Disconnected
        val provider = createProvider()

        val result = provider.getNodeDetails("!abc123")
        assertIs<GetNodeDetailsResult.NotConnected>(result)
    }

    @Test
    fun getNodeDetails_returns_not_found_for_unknown_node() = runTest {
        val nodeMap = MutableStateFlow(emptyMap<Int, Node>())
        every { nodeRepository.nodeDBbyNum } returns nodeMap

        val provider = createProvider()
        val result = provider.getNodeDetails("!ffffff")

        assertIs<GetNodeDetailsResult.NotFound>(result)
    }

    @Test
    fun getNodeDetails_returns_node_data_for_valid_hex_id() = runTest {
        val testNode =
            Node(
                num = 0xabc,
                user = User(id = "!00000abc", long_name = "Alice", short_name = "AL"),
                lastHeard = 1_700_000_000,
                snr = 5.5f,
                rssi = -70,
                channel = 0,
                hopsAway = 1,
            )
        val nodeMap = MutableStateFlow(mapOf(0xabc to testNode))
        every { nodeRepository.nodeDBbyNum } returns nodeMap

        val provider = createProvider()
        val result = provider.getNodeDetails("!abc")

        assertIs<GetNodeDetailsResult.Success>(result)
        assertEquals("Alice", result.node.name)
        assertEquals(5.5f, result.node.snr)
        assertEquals(-70, result.node.rssi)
        assertEquals(1, result.node.hopsAway)
    }

    @Test
    fun getNodeDetails_returns_null_position_when_no_fix() = runTest {
        // Node with (0.0, 0.0) position and time=0 → no valid position
        val testNode = Node(num = 1, user = User(id = "!00000001", long_name = "NoGPS", short_name = "NG"))
        val nodeMap = MutableStateFlow(mapOf(1 to testNode))
        every { nodeRepository.nodeDBbyNum } returns nodeMap

        val provider = createProvider()
        val result = provider.getNodeDetails("!1")

        assertIs<GetNodeDetailsResult.Success>(result)
        assertNull(result.node.latitude)
        assertNull(result.node.longitude)
    }

    @Test
    fun getNodeDetails_returns_error_for_invalid_hex_format() = runTest {
        val nodeMap = MutableStateFlow(emptyMap<Int, Node>())
        every { nodeRepository.nodeDBbyNum } returns nodeMap

        val provider = createProvider()
        val result = provider.getNodeDetails("!not_hex")

        // Invalid hex should result in NotFound or Error
        val isHandled = result is GetNodeDetailsResult.NotFound || result is GetNodeDetailsResult.Error
        assertEquals(true, isHandled)
    }

    // --- getMeshMetrics tests ---

    @Test
    fun getMeshMetrics_returns_not_connected_when_disconnected() = runTest {
        connectionState.value = ConnectionState.Disconnected
        val provider = createProvider()

        val result = provider.getMeshMetrics()
        assertIs<GetMeshMetricsResult.NotConnected>(result)
    }

    @Test
    fun getMeshMetrics_returns_valid_metrics_with_active_nodes() = runTest {
        val nodes = mapOf(1 to Node(num = 1, lastHeard = 1_699_999_990), 2 to Node(num = 2, lastHeard = 1_699_999_980))
        val nodeMap = MutableStateFlow(nodes)
        every { nodeRepository.nodeDBbyNum } returns nodeMap
        every { nodeRepository.totalNodeCount } returns flowOf(2)
        every { nodeRepository.onlineNodeCount } returns flowOf(2)

        val provider = createProvider()
        val result = provider.getMeshMetrics()

        assertIs<GetMeshMetricsResult.Success>(result)
        assertEquals(2, result.metrics.totalNodeCount)
        assertEquals(2, result.metrics.onlineNodeCount)
        // Health score: 50 + (50 * 2) / 2 = 100
        assertEquals(100, result.metrics.meshHealthScore)
        // Most recent packet: 1_699_999_990 * 1000
        assertEquals(1_699_999_990_000L, result.metrics.mostRecentPacketTime)
    }

    @Test
    fun getMeshMetrics_returns_zero_health_score_when_empty() = runTest {
        val nodeMap = MutableStateFlow(emptyMap<Int, Node>())
        every { nodeRepository.nodeDBbyNum } returns nodeMap
        every { nodeRepository.totalNodeCount } returns flowOf(0)
        every { nodeRepository.onlineNodeCount } returns flowOf(0)

        val provider = createProvider()
        val result = provider.getMeshMetrics()

        assertIs<GetMeshMetricsResult.Success>(result)
        assertEquals(0, result.metrics.totalNodeCount)
        assertEquals(0, result.metrics.meshHealthScore)
    }

    @Test
    fun getMeshMetrics_falls_back_to_current_time_when_all_lastHeard_zero() = runTest {
        val nodes = mapOf(1 to Node(num = 1, lastHeard = 0))
        val nodeMap = MutableStateFlow(nodes)
        every { nodeRepository.nodeDBbyNum } returns nodeMap
        every { nodeRepository.totalNodeCount } returns flowOf(1)
        every { nodeRepository.onlineNodeCount } returns flowOf(0)

        val provider = createProvider()
        val result = provider.getMeshMetrics()

        assertIs<GetMeshMetricsResult.Success>(result)
        // Falls back to clock.now() since all lastHeard are 0
        assertEquals(clock.now().toEpochMilliseconds(), result.metrics.mostRecentPacketTime)
    }

    @Test
    fun getMeshMetrics_returns_degraded_health_when_no_nodes_online() = runTest {
        val nodes = mapOf(1 to Node(num = 1, lastHeard = 1_000))
        val nodeMap = MutableStateFlow(nodes)
        every { nodeRepository.nodeDBbyNum } returns nodeMap
        every { nodeRepository.totalNodeCount } returns flowOf(1)
        every { nodeRepository.onlineNodeCount } returns flowOf(0)

        val provider = createProvider()
        val result = provider.getMeshMetrics()

        assertIs<GetMeshMetricsResult.Success>(result)
        // HEALTH_SCORE_DEGRADED = 10
        assertEquals(10, result.metrics.meshHealthScore)
    }

    // --- sendMessage error propagation test ---

    @Test
    fun sendMessage_returns_not_connected_when_disconnected() = runTest {
        connectionState.value = ConnectionState.Disconnected
        val provider = createProvider()

        val result = provider.sendMessage("hello", null, null)
        assertIs<SendMessageResult.NotConnected>(result)
    }

    @Test
    fun sendMessage_returns_rate_limited_when_exhausted() = runTest {
        val provider = createProvider()

        // Exhaust rate limit
        repeat(RateLimiter.MAX_CALLS) { rateLimiter.tryAcquire() }

        val result = provider.sendMessage("hello", null, null)
        assertIs<SendMessageResult.RateLimited>(result)
    }

    // --- getRecentMessages tests ---

    @Test
    fun getRecentMessages_contact_not_found() = runTest {
        val nodeMap = MutableStateFlow(emptyMap<Int, Node>())
        every { nodeRepository.nodeDBbyNum } returns nodeMap
        every { radioConfigRepository.channelSetFlow } returns flowOf(org.meshtastic.proto.ChannelSet())

        val provider = createProvider()
        val result = provider.getRecentMessages("NonExistent", 10)
        assertIs<GetRecentMessagesResult.ContactNotFound>(result)
    }

    // --- getUnreadSummary tests ---

    @Test
    fun getUnreadSummary_returns_empty_when_no_unread() = runTest {
        every { packetRepository.getContacts() } returns flowOf(emptyMap())
        every { packetRepository.getContactSettings() } returns flowOf(emptyMap())
        every { radioConfigRepository.channelSetFlow } returns flowOf(org.meshtastic.proto.ChannelSet())
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())

        val provider = createProvider()
        val result = provider.getUnreadSummary()
        assertIs<GetUnreadSummaryResult.Success>(result)
        assertEquals(0, result.summary.totalUnreadCount)
        assertEquals(0, result.summary.contacts.size)
    }
}

private class TestClock(var currentTime: Instant) : Clock {
    override fun now(): Instant = currentTime
}
