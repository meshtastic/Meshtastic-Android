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

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the render extension in [buildAnnotatedStringWithLinks] (see contracts/autolinktext-render.md,
 * rows A-1…A-8). These assert on the produced [AnnotatedString] directly — the builder is a pure function, so no
 * Compose UI harness is required.
 */
class AutoLinkTextRenderTest {

    private val linkStyles = TextLinkStyles(style = SpanStyle())

    private fun build(text: String, mentionName: ((String) -> String?)? = null): AnnotatedString =
        buildAnnotatedStringWithLinks(text, linkStyles, mentionName) {}

    private fun AnnotatedString.urlLinks(): List<Triple<String, Int, Int>> =
        getLinkAnnotations(0, length).mapNotNull { range ->
            (range.item as? LinkAnnotation.Url)?.let { Triple(it.url, range.start, range.end) }
        }

    private fun AnnotatedString.clickableTags(): List<AnnotatedString.Range<LinkAnnotation.Clickable>> =
        getLinkAnnotations(0, length).mapNotNull { range ->
            (range.item as? LinkAnnotation.Clickable)?.let { AnnotatedString.Range(it, range.start, range.end) }
        }

    @Test // A-1
    fun bold_renders_without_delimiters() {
        val result = build("**bold**")
        assertEquals("bold", result.text)
        val bold = result.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals(0, bold.start)
        assertEquals(4, bold.end)
        assertTrue(result.urlLinks().isEmpty())
    }

    @Test // A-2
    fun markdown_link_is_tappable_and_syntax_removed() {
        val result = build("[label](https://e.com)")
        assertEquals("label", result.text)
        val link = result.urlLinks().single()
        assertEquals("https://e.com", link.first)
        assertEquals("label", result.text.substring(link.second, link.third))
    }

    @Test // A-3
    fun bare_url_is_autolinked() {
        val result = build("see https://e.com")
        assertEquals("see https://e.com", result.text)
        val link = result.urlLinks().single()
        assertEquals("https://e.com", link.first)
        assertEquals("https://e.com", result.text.substring(link.second, link.third))
    }

    @Test // A-4
    fun markdown_link_and_bare_url_both_link_without_double_linking() {
        val result = build("[x](https://e.com) and https://e.com")
        assertEquals("x and https://e.com", result.text)
        val links = result.urlLinks().sortedBy { it.second }
        assertEquals(2, links.size)
        // Markdown link on "x".
        assertEquals("x", result.text.substring(links[0].second, links[0].third))
        assertEquals("https://e.com", links[0].first)
        // Bare URL is linked separately, no overlap with the markdown link range.
        assertEquals("https://e.com", result.text.substring(links[1].second, links[1].third))
        assertTrue(links[0].third <= links[1].second, "link ranges must not overlap")
    }

    @Test // A-5
    fun mention_inside_bold_resolves_and_keeps_offsets() {
        val result = build("**@!abcd1234**") { id -> if (id == "!abcd1234") "Alice" else null }
        assertEquals("@Alice", result.text)
        // The markdown bold must cover the whole substituted display name. The parser tokenizes punctuation (`@`, `!`)
        // into separate leaf nodes, so the bold can arrive as several adjacent fragments; they render additively, so
        // coverage — not span count — is the contract. Every index of "@Alice" must fall inside some bold span whose
        // offsets grew with the mention substitution.
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue(
            "@Alice".indices.all { i -> boldSpans.any { i in it.start until it.end } },
            "expected bold to cover the whole display name; text='${result.text}' " +
                "boldSpans=${boldSpans.map { it.start to it.end }}",
        )
        // Mention is a tappable clickable spanning the display name.
        val mention = result.clickableTags().single()
        assertEquals(0, mention.start)
        assertEquals("@Alice".length, mention.end)
        // The bold delimiters must not have produced a bare-URL autolink.
        assertTrue(result.urlLinks().isEmpty())
    }

    @Test // A-6
    fun plain_text_falls_back_to_autolinked_text_only() {
        val result = build("no markdown here")
        assertEquals("no markdown here", result.text)
        assertTrue(result.spanStyles.isEmpty())
        assertTrue(result.urlLinks().isEmpty())
    }

    @Test // A-6 (unpaired delimiter degrades to literal, no crash)
    fun unpaired_delimiter_renders_literally() {
        val result = build("price is 3 * 4 = 12")
        assertEquals("price is 3 * 4 = 12", result.text)
        assertTrue(result.spanStyles.none { it.item.fontStyle == FontStyle.Italic })
    }

    @Test // A-8
    fun emoji_only_short_circuits_and_renders_unchanged() {
        val result = build("😀👍")
        assertEquals("😀👍", result.text)
        assertTrue(result.spanStyles.isEmpty())
        assertTrue(result.urlLinks().isEmpty())
    }

    @Test // A-5 supporting: mention without a resolver keeps the raw id token
    fun mention_without_resolver_is_left_untouched() {
        val result = build("hi @!abcd1234")
        // No mentionName callback → substituteMentions is a no-op, token stays literal, no clickable is added.
        assertEquals("hi @!abcd1234", result.text)
        assertNull(result.clickableTags().singleOrNull())
    }
}
