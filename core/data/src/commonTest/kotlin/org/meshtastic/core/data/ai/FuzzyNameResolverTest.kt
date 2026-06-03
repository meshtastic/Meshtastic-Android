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
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuzzyNameResolverTest {

    @Test
    fun longestCommonSubstring_exact_match() {
        assertEquals(5, longestCommonSubstringLength("hello", "hello"))
    }

    @Test
    fun longestCommonSubstring_partial_match() {
        assertEquals(3, longestCommonSubstringLength("abcdef", "xbcdx"))
    }

    @Test
    fun longestCommonSubstring_no_match() {
        assertEquals(0, longestCommonSubstringLength("abc", "xyz"))
    }

    @Test
    fun longestCommonSubstring_empty_string() {
        assertEquals(0, longestCommonSubstringLength("", "abc"))
        assertEquals(0, longestCommonSubstringLength("abc", ""))
    }

    @Test
    fun longestCommonSubstring_case_sensitive() {
        // The function itself is case-sensitive; callers lowercase
        assertEquals(0, longestCommonSubstringLength("ABC", "abc"))
    }

    @Test
    fun longestCommonSubstring_longer_second() {
        assertEquals(4, longestCommonSubstringLength("test", "this is a test string"))
    }

    // NodeNameResult / ChannelNameResult sealed classes are tested indirectly via
    // the integration with AiFunctionProviderImpl, but we verify basic structure here.

    @Test
    fun nodeNameResult_found_carries_data() {
        val result = NodeNameResult.Found(nodeNum = 42, userId = "!abcd1234")
        assertIs<NodeNameResult.Found>(result)
        assertEquals(42, result.nodeNum)
        assertEquals("!abcd1234", result.userId)
    }

    @Test
    fun nodeNameResult_ambiguous_carries_candidates() {
        val result = NodeNameResult.Ambiguous(listOf("Alice", "Alicia"))
        assertIs<NodeNameResult.Ambiguous>(result)
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun channelNameResult_found_carries_data() {
        val result = ChannelNameResult.Found(channelIndex = 1, name = "General")
        assertIs<ChannelNameResult.Found>(result)
        assertEquals(1, result.channelIndex)
        assertEquals("General", result.name)
    }

    // --- Behavioral tests for resolveNodeName ---

    @Test
    fun resolveNodeName_exact_match_case_insensitive() {
        val nodeRepository: NodeRepository = mock(MockMode.autofill)
        val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
        val nodes =
            mapOf(
                1 to Node(num = 1, user = User(id = "!00000001", long_name = "Alice", short_name = "AL")),
                2 to Node(num = 2, user = User(id = "!00000002", long_name = "Bob", short_name = "BO")),
            )
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(nodes)

        val resolver = FuzzyNameResolver(nodeRepository, radioConfigRepository)
        val result = resolver.resolveNodeName("alice")

        assertIs<NodeNameResult.Found>(result)
        assertEquals(1, result.nodeNum)
        assertEquals("!00000001", result.userId)
    }

    @Test
    fun resolveNodeName_fuzzy_match_single_candidate() {
        val nodeRepository: NodeRepository = mock(MockMode.autofill)
        val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
        val nodes =
            mapOf(
                1 to Node(num = 1, user = User(id = "!00000001", long_name = "Alexander", short_name = "AX")),
                2 to Node(num = 2, user = User(id = "!00000002", long_name = "Bob", short_name = "BO")),
            )
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(nodes)

        val resolver = FuzzyNameResolver(nodeRepository, radioConfigRepository)
        val result = resolver.resolveNodeName("Alexan")

        assertIs<NodeNameResult.Found>(result)
        assertEquals(1, result.nodeNum)
    }

    @Test
    fun resolveNodeName_ambiguous_returns_candidates() {
        val nodeRepository: NodeRepository = mock(MockMode.autofill)
        val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
        val nodes =
            mapOf(
                1 to Node(num = 1, user = User(id = "!00000001", long_name = "Alice Smith", short_name = "AS")),
                2 to Node(num = 2, user = User(id = "!00000002", long_name = "Alice Jones", short_name = "AJ")),
            )
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(nodes)

        val resolver = FuzzyNameResolver(nodeRepository, radioConfigRepository)
        val result = resolver.resolveNodeName("Alice")

        // "Alice" matches both equally via LCS
        assertIs<NodeNameResult.Ambiguous>(result)
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun resolveNodeName_not_found_when_no_nodes() {
        val nodeRepository: NodeRepository = mock(MockMode.autofill)
        val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())

        val resolver = FuzzyNameResolver(nodeRepository, radioConfigRepository)
        val result = resolver.resolveNodeName("Unknown")

        assertIs<NodeNameResult.NotFound>(result)
    }

    @Test
    fun resolveNodeName_not_found_when_no_match() {
        val nodeRepository: NodeRepository = mock(MockMode.autofill)
        val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
        val nodes = mapOf(1 to Node(num = 1, user = User(id = "!00000001", long_name = "Alice", short_name = "AL")))
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(nodes)

        val resolver = FuzzyNameResolver(nodeRepository, radioConfigRepository)
        val result = resolver.resolveNodeName("Zzzzzz")

        assertIs<NodeNameResult.NotFound>(result)
    }

    // --- Behavioral tests for resolveChannelName ---

    @Test
    fun resolveChannelName_exact_match() = runTest {
        val nodeRepository: NodeRepository = mock(MockMode.autofill)
        val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
        val channelSet =
            ChannelSet(settings = listOf(ChannelSettings(name = "General"), ChannelSettings(name = "Emergency")))
        every { radioConfigRepository.channelSetFlow } returns flowOf(channelSet)

        val resolver = FuzzyNameResolver(nodeRepository, radioConfigRepository)
        val result = resolver.resolveChannelName("General")

        assertIs<ChannelNameResult.Found>(result)
        assertEquals(0, result.channelIndex)
        assertEquals("General", result.name)
    }

    @Test
    fun resolveChannelName_excludes_admin_channel() = runTest {
        val nodeRepository: NodeRepository = mock(MockMode.autofill)
        val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
        val channelSet =
            ChannelSet(settings = listOf(ChannelSettings(name = "admin"), ChannelSettings(name = "General")))
        every { radioConfigRepository.channelSetFlow } returns flowOf(channelSet)

        val resolver = FuzzyNameResolver(nodeRepository, radioConfigRepository)
        val result = resolver.resolveChannelName("admin")

        // "admin" should be excluded — cannot resolve to the admin channel
        assertIs<ChannelNameResult.NotFound>(result)
    }

    @Test
    fun resolveChannelName_not_found_when_empty() = runTest {
        val nodeRepository: NodeRepository = mock(MockMode.autofill)
        val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
        val channelSet = ChannelSet(settings = emptyList())
        every { radioConfigRepository.channelSetFlow } returns flowOf(channelSet)

        val resolver = FuzzyNameResolver(nodeRepository, radioConfigRepository)
        val result = resolver.resolveChannelName("General")

        assertIs<ChannelNameResult.NotFound>(result)
    }
}
