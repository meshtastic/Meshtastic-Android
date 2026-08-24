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
package org.meshtastic.core.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Contract tests for [parseInlineMarkdown] (see contracts/inline-markdown-parser.md, rows P-1…P-11). */
class InlineMarkdownTest {

    private fun styledText(result: InlineMarkdownResult, style: InlineStyle): List<String> =
        result.styleSpans.filter { it.style == style }.map { result.displayText.substring(it.range) }

    @Test // P-1
    fun bold_strips_delimiters_and_styles_content() {
        val result = parseInlineMarkdown("a **b** c")
        assertEquals("a b c", result.displayText)
        assertEquals(listOf("b"), styledText(result, InlineStyle.Bold))
    }

    @Test // P-2
    fun single_asterisk_is_italic_not_bold() {
        val result = parseInlineMarkdown("a *b* c")
        assertEquals("a b c", result.displayText)
        assertEquals(listOf("b"), styledText(result, InlineStyle.Italic))
        assertTrue(styledText(result, InlineStyle.Bold).isEmpty())
    }

    @Test // P-3
    fun triple_asterisk_is_bold_and_italic() {
        val result = parseInlineMarkdown("a ***b*** c")
        assertEquals("a b c", result.displayText)
        assertEquals(listOf("b"), styledText(result, InlineStyle.Bold))
        assertEquals(listOf("b"), styledText(result, InlineStyle.Italic))
    }

    @Test // P-4
    fun strikethrough_strips_and_styles() {
        val result = parseInlineMarkdown("a ~~b~~ c")
        assertEquals("a b c", result.displayText)
        assertEquals(listOf("b"), styledText(result, InlineStyle.Strikethrough))
    }

    @Test // P-5
    fun code_span_does_not_reinterpret_inner_markup() {
        val result = parseInlineMarkdown("a `b*c` d")
        assertEquals("a b*c d", result.displayText)
        assertEquals(listOf("b*c"), styledText(result, InlineStyle.Code))
        assertTrue(styledText(result, InlineStyle.Italic).isEmpty())
    }

    @Test // P-6
    fun inline_link_strips_syntax_and_records_url() {
        val result = parseInlineMarkdown("[x](https://e.com)")
        assertEquals("x", result.displayText)
        assertEquals(1, result.linkSpans.size)
        val link = result.linkSpans.single()
        assertEquals("x", result.displayText.substring(link.range))
        assertEquals("https://e.com", link.url)
    }

    @Test // P-7
    fun unpaired_delimiter_is_literal_and_does_not_throw() {
        val result = parseInlineMarkdown("**oops")
        assertEquals("**oops", result.displayText)
        assertTrue(result.styleSpans.isEmpty())
    }

    @Test // P-7 (loose asterisks around words)
    fun loose_asterisks_render_literally() {
        val result = parseInlineMarkdown("a * b * c")
        assertEquals("a * b * c", result.displayText)
    }

    @Test // P-8
    fun block_syntax_renders_literally() {
        val result = parseInlineMarkdown("# heading\n- item")
        assertEquals("# heading\n- item", result.displayText)
    }

    @Test // P-9
    fun newlines_are_preserved() {
        val result = parseInlineMarkdown("line1\nline2")
        assertEquals("line1\nline2", result.displayText)
    }

    @Test // P-10
    fun empty_input_yields_empty_result() {
        val result = parseInlineMarkdown("")
        assertEquals("", result.displayText)
        assertTrue(result.styleSpans.isEmpty())
        assertTrue(result.linkSpans.isEmpty())
    }

    @Test // P-11 (emoji-only carries no delimiters and is unchanged)
    fun emoji_only_is_unchanged() {
        val result = parseInlineMarkdown("😀👍")
        assertEquals("😀👍", result.displayText)
        assertTrue(result.styleSpans.isEmpty())
    }

    @Test
    fun mixed_message_strips_all_delimiters() {
        val result =
            parseInlineMarkdown(
                "Meet at **noon** by the ~~old~~ new *bridge* — `README` and [map](https://example.com)",
            )
        assertEquals("Meet at noon by the old new bridge — README and map", result.displayText)
        assertEquals(listOf("noon"), styledText(result, InlineStyle.Bold))
        assertEquals(listOf("old"), styledText(result, InlineStyle.Strikethrough))
        assertEquals(listOf("bridge"), styledText(result, InlineStyle.Italic))
        assertEquals(listOf("README"), styledText(result, InlineStyle.Code))
        assertEquals("https://example.com", result.linkSpans.single().url)
        assertEquals("map", result.displayText.substring(result.linkSpans.single().range))
    }
}
