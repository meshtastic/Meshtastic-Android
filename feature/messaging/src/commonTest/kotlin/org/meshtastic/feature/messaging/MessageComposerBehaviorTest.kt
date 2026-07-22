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
package org.meshtastic.feature.messaging

import org.meshtastic.core.ui.component.InlineStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageComposerBehaviorTest {

    private val candidates =
        listOf(
            MentionCandidate(id = "!a1", longName = "Alpha Node", shortName = "ALP"),
            MentionCandidate(id = "!b2", longName = "Bravo", shortName = "BRV"),
            MentionCandidate(id = "!c3", longName = "Charlie", shortName = "CHR"),
        )

    @Test
    fun `mention matching is case insensitive across id and display names`() {
        assertEquals(listOf(candidates[0]), matchingMentionCandidates(candidates, "ALPHA"))
        assertEquals(listOf(candidates[1]), matchingMentionCandidates(candidates, "brv"))
        assertEquals(listOf(candidates[2]), matchingMentionCandidates(candidates, "!C3"))
    }

    @Test
    fun `mention matching preserves order and stops at the requested limit`() {
        assertEquals(candidates.take(2), matchingMentionCandidates(candidates, query = "", limit = 2))
        assertTrue(matchingMentionCandidates(candidates, query = "", limit = 0).isEmpty())
    }

    @Test
    fun `live markdown parser returns no spans for plain or incomplete text`() {
        assertTrue(liveInlineMarkdownStyleRanges("plain text").isEmpty())
        assertTrue(liveInlineMarkdownStyleRanges("**unfinished").isEmpty())
        assertTrue(liveInlineMarkdownStyleRanges("`unfinished").isEmpty())
    }

    @Test
    fun `live markdown parser identifies supported inline styles without styling delimiters`() {
        val source = "**bold** *italic* ~~gone~~ `code`"
        val spans = liveInlineMarkdownStyleRanges(source).map { span -> source.substring(span.range) to span.style }

        assertEquals(
            listOf(
                "bold" to InlineStyle.Bold,
                "italic" to InlineStyle.Italic,
                "gone" to InlineStyle.Strikethrough,
                "code" to InlineStyle.Code,
            ),
            spans,
        )
    }

    @Test
    fun `bold delimiters are not double counted as italic`() {
        val spans = liveInlineMarkdownStyleRanges("**bold**")

        assertEquals(listOf(LiveStyleSpan(2..5, InlineStyle.Bold)), spans)
    }
}
