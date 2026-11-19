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
package org.meshtastic.core.ui.util

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleHtmlTest {

    @Test
    fun anchorsBecomeUrlLinksOverTheirText() {
        val text = annotatedStringFromSimpleHtml("""I have read the <a href="https://example.org/a">docs</a>.""", null)

        assertEquals("I have read the docs.", text.text)
        val link = text.getLinkAnnotations(0, text.length).single()
        assertEquals("https://example.org/a", (link.item as LinkAnnotation.Url).url)
        assertEquals("docs", text.text.substring(link.start, link.end))
    }

    @Test
    fun boldSpansItsText() {
        val text = annotatedStringFromSimpleHtml("a <b>bold</b> word", null)

        assertEquals("a bold word", text.text)
        val span = text.spanStyles.single()
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        assertEquals("bold", text.text.substring(span.start, span.end))
    }

    @Test
    fun entitiesDecode() {
        val text = annotatedStringFromSimpleHtml("a &amp; b &lt;c&gt; &#39;d&#x27; &apos;e&apos;", null)

        assertEquals("a & b <c> 'd' 'e'", text.text)
    }

    @Test
    fun breaksAndParagraphsBecomeNewlines() {
        val text = annotatedStringFromSimpleHtml("<p>one<br>two</p><p>three</p>", null)

        assertEquals("one\ntwo\n\nthree", text.text)
    }

    @Test
    fun unknownTagsDropAndKeepTheirText() {
        val text = annotatedStringFromSimpleHtml("<span class=\"x\">kept</span>  <tt>text</tt>", null)

        assertEquals("kept text", text.text)
    }

    @Test
    fun aLoneAmpersandOrBracketStaysLiteral() {
        assertEquals("fish & chips < 3", annotatedStringFromSimpleHtml("fish & chips < 3", null).text)
    }
}
