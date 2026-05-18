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
package org.meshtastic.feature.docs.translation

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MarkdownTranslationSegmenterTest {

    /** Simple uppercase "translator" for deterministic assertions. */
    private val uppercaseTranslator: suspend (String) -> String = { it.uppercase() }

    @Test
    fun `paragraphs are translated`() = runTest {
        val input = "Hello world\n\nThis is a paragraph"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "HELLO WORLD")
        assertContains(result, "THIS IS A PARAGRAPH")
    }

    @Test
    fun `headings are translated`() = runTest {
        val input = "# Welcome\n## Getting Started"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "# WELCOME")
        assertContains(result, "## GETTING STARTED")
    }

    @Test
    fun `fenced code blocks are NOT translated`() = runTest {
        val input = "Before code\n\n```kotlin\nval x = 1\n```\n\nAfter code"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "BEFORE CODE")
        assertContains(result, "val x = 1")
        assertContains(result, "AFTER CODE")
        assertFalse(result.contains("VAL X = 1"))
    }

    @Test
    fun `indented code blocks are NOT translated`() = runTest {
        val input = "Some text\n\n    code line 1\n    code line 2\n\nMore text"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "SOME TEXT")
        assertContains(result, "    code line 1")
        assertContains(result, "MORE TEXT")
    }

    @Test
    fun `inline code is NOT translated`() = runTest {
        val input = "Use `meshtastic --info` to check"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "`meshtastic --info`")
        assertContains(result, "USE")
        assertContains(result, "TO CHECK")
    }

    @Test
    fun `link text is translated but URL is preserved`() = runTest {
        val input = "Click [here](https://example.com) for more"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "[HERE](https://example.com)")
        assertContains(result, "CLICK")
    }

    @Test
    fun `images are NOT translated`() = runTest {
        val input = "See the diagram:\n\n![alt text](/images/diagram.png)"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "![alt text](/images/diagram.png)")
        assertContains(result, "SEE THE DIAGRAM:")
    }

    @Test
    fun `frontmatter is NOT translated`() = runTest {
        val input = "---\ntitle: My Page\nlayout: default\n---\n\nHello world"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "title: My Page")
        assertContains(result, "layout: default")
        assertContains(result, "HELLO WORLD")
    }

    @Test
    fun `list items are translated`() = runTest {
        val input = "- First item\n- Second item\n* Third item"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "- FIRST ITEM")
        assertContains(result, "- SECOND ITEM")
        assertContains(result, "* THIRD ITEM")
    }

    @Test
    fun `ordered list items are translated`() = runTest {
        val input = "1. First step\n2. Second step"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "1. FIRST STEP")
        assertContains(result, "2. SECOND STEP")
    }

    @Test
    fun `blockquotes are translated`() = runTest {
        val input = "> Important note here"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "> IMPORTANT NOTE HERE")
    }

    @Test
    fun `table cells are translated but separators are not`() = runTest {
        val input = "| Header 1 | Header 2 |\n|---|---|\n| Cell A | Cell B |"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "HEADER 1")
        assertContains(result, "HEADER 2")
        assertContains(result, "|---|---|")
        assertContains(result, "CELL A")
        assertContains(result, "CELL B")
    }

    @Test
    fun `empty input returns empty output`() = runTest {
        val result = MarkdownTranslationSegmenter.translateMarkdown("", uppercaseTranslator)
        assertEquals("", result)
    }

    @Test
    fun `html blocks are NOT translated`() = runTest {
        val input = "Before\n\n<div class=\"note\">Don't translate</div>\n\nAfter"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "<div class=\"note\">Don't translate</div>")
        assertContains(result, "BEFORE")
        assertContains(result, "AFTER")
    }

    @Test
    fun `nested list indentation is preserved`() = runTest {
        val input = "- Parent\n    - Child item"
        val result = MarkdownTranslationSegmenter.translateMarkdown(input, uppercaseTranslator)
        assertContains(result, "- PARENT")
        assertContains(result, "    - CHILD ITEM")
    }
}
