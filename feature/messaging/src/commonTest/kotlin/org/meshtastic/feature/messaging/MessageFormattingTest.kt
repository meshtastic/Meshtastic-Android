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

import androidx.compose.ui.text.TextRange
import org.meshtastic.core.ui.component.InlineStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contract tests for the formatting helpers (see contracts/formatting-helpers.md, rows F-1…F-9). */
class MessageFormattingTest {

    @Test // F-1
    fun wrap_bold_wraps_selection_and_keeps_delimiters_selected() {
        val result = wrapSelection("hello world", TextRange(6, 11), InlineStyle.Bold)
        assertEquals("hello **world**", result.text)
        assertEquals("**world**", result.text.substring(result.selection.min, result.selection.max))
    }

    @Test // F-2
    fun wrap_bold_toggles_off_when_already_wrapped() {
        // "hello **world**" — select the inner "world" (indices 8..13).
        val result = wrapSelection("hello **world**", TextRange(8, 13), InlineStyle.Bold)
        assertEquals("hello world", result.text)
        assertEquals("world", result.text.substring(result.selection.min, result.selection.max))
    }

    @Test // F-3
    fun collapsed_cursor_inserts_pair_with_caret_between() {
        val result = wrapSelection("hi", TextRange(1), InlineStyle.Italic)
        assertEquals("h**i", result.text)
        assertTrue(result.selection.collapsed)
        assertEquals(2, result.selection.start)
    }

    @Test // F-4
    fun link_wraps_selection() {
        val result = wrapSelectionWithLink("see docs", TextRange(4, 8), "https://e.com")
        assertEquals("see [docs](https://e.com)", result.text)
    }

    @Test // F-5
    fun link_inserts_placeholder_for_collapsed_cursor() {
        val result = wrapSelectionWithLink("", TextRange(0), "https://e.com")
        assertEquals("[link text](https://e.com)", result.text)
    }

    @Test // F-6
    fun link_unwraps_to_label() {
        val text = "[docs](https://e.com)"
        val result = unwrapLink(text, TextRange(0, text.length))
        assertEquals("docs", result?.text)
    }

    @Test // F-6 (negative)
    fun unwrap_returns_null_for_non_link() {
        assertNull(unwrapLink("plain text", TextRange(0, 10)))
    }

    @Test // F-7
    fun wrapping_hugs_content_leaving_whitespace_outside() {
        val result = wrapSelection(" world ", TextRange(0, 7), InlineStyle.Bold)
        assertEquals(" **world** ", result.text)
    }

    @Test // F-8
    fun wrapping_absorbs_adjacent_delimiter_without_orphan() {
        // The stray '*' before the selection is absorbed rather than left orphaned.
        val result = wrapSelection("a*b", TextRange(2, 3), InlineStyle.Bold)
        assertEquals("a**b**", result.text)
    }

    @Test // F-9
    fun contains_markdown_syntax_detects_and_rejects() {
        assertTrue(containsMarkdownSyntax("a **b**"))
        assertTrue(containsMarkdownSyntax("a *b*"))
        assertTrue(containsMarkdownSyntax("a ~~b~~"))
        assertTrue(containsMarkdownSyntax("a `b`"))
        assertTrue(containsMarkdownSyntax("a [b](c)"))
        assertFalse(containsMarkdownSyntax("a b"))
        assertFalse(containsMarkdownSyntax(""))
    }

    @Test // isMarkdownLink
    fun is_markdown_link_matches_exact_link_only() {
        assertTrue(isMarkdownLink("[x](https://e.com)"))
        assertFalse(isMarkdownLink("see [x](https://e.com) here"))
    }
}
