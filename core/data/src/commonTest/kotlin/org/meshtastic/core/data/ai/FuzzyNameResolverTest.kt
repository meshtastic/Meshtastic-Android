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
    // the full integration in AiFunctionProviderImplTest, but we verify basic structure here.

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
}
