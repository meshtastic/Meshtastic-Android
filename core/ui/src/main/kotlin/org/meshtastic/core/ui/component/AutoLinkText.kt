/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.text.Spannable
import android.text.Spannable.Factory
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.text.util.LinkifyCompat
import org.meshtastic.core.ui.theme.HyperlinkBlue

private val DefaultTextLinkStyles =
    TextLinkStyles(style = SpanStyle(color = HyperlinkBlue, textDecoration = TextDecoration.Underline))

@Composable
fun AutoLinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    linkStyles: TextLinkStyles = DefaultTextLinkStyles,
    color: Color = Color.Unspecified,
) {
    val annotatedString = remember(text, linkStyles) {
        processMarkdownAndLinks(text, linkStyles)
    }
    Text(text = annotatedString, modifier = modifier, style = style.copy(color = color))
}

/**
 * Processes markdown-style links [text](url) and then auto-linkifies remaining URLs
 */
private fun processMarkdownAndLinks(text: String, linkStyles: TextLinkStyles): AnnotatedString {
    // First, extract markdown links and create a spannable without them
    val markdownLinks = mutableListOf<MarkdownLink>()
    val markdownPattern = "\\[([^\\]]+)\\]\\(([^)]+)\\)".toRegex()
    var processedText = text
    var offsetAdjustment = 0

    markdownPattern.findAll(text).forEach { match ->
        val displayText = match.groupValues[1]
        val url = match.groupValues[2]
        val originalStart = match.range.first - offsetAdjustment
        markdownLinks.add(
            MarkdownLink(
                start = originalStart,
                end = originalStart + displayText.length,
                displayText = displayText,
                url = url
            )
        )
        // Replace [text](url) with just text for linkify processing
        processedText = processedText.replaceFirst(match.value, displayText)
        // Adjust offset for next replacements
        offsetAdjustment += match.value.length - displayText.length
    }

    // Linkify the processed text for auto-detection
    val spannable = linkify(processedText)

    // Convert to AnnotatedString with both markdown and auto-detected links
    return spannable.toAnnotatedStringWithMarkdown(linkStyles, markdownLinks)
}

private data class MarkdownLink(
    val start: Int,
    val end: Int,
    val displayText: String,
    val url: String
)

private fun linkify(text: String) = Factory.getInstance().newSpannable(text).also {
    LinkifyCompat.addLinks(it, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS)
}

private fun Spannable.toAnnotatedStringWithMarkdown(
    linkStyles: TextLinkStyles,
    markdownLinks: List<MarkdownLink>
): AnnotatedString = buildAnnotatedString {
    val spannable = this@toAnnotatedStringWithMarkdown
    var lastEnd = 0

    // Collect all link positions (both markdown and auto-detected)
    val allLinks = mutableListOf<LinkInfo>()

    // Add markdown links
    markdownLinks.forEach { mdLink ->
        allLinks.add(LinkInfo(mdLink.start, mdLink.end, mdLink.url, isMarkdown = true))
    }

    // Add auto-detected links (URLSpans)
    spannable.getSpans(0, spannable.length, URLSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        // Only add if it doesn't overlap with markdown links
        if (allLinks.none { it.start <= start && start < it.end }) {
            allLinks.add(LinkInfo(start, end, span.url, isMarkdown = false))
        }
    }

    // Sort by position
    allLinks.sortBy { it.start }

    // Build the annotated string
    allLinks.forEach { link ->
        append(spannable.subSequence(lastEnd, link.start))
        withLink(LinkAnnotation.Url(url = link.url, styles = linkStyles)) {
            append(spannable.subSequence(link.start, link.end))
        }
        lastEnd = link.end
    }
    append(spannable.subSequence(lastEnd, spannable.length))
}

private data class LinkInfo(
    val start: Int,
    val end: Int,
    val url: String,
    val isMarkdown: Boolean
)

@Preview(showBackground = true)
@Composable
private fun AutoLinkTextPreview() {
    AutoLinkText("A text containing a link https://example.com")
}
