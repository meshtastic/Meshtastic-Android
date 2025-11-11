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
import java.util.regex.Pattern

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
    val spannable = remember(text) { linkify(text) }
    Text(text = spannable.toAnnotatedString(linkStyles), modifier = modifier, style = style.copy(color = color))
}

private fun linkify(text: String) = Factory.getInstance().newSpannable(text).also {
    LinkifyCompat.addLinks(it, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS)
    // Add geo: URI pattern for location links with optional label in parentheses
    val geoPattern = Pattern.compile("geo:[-+]?\\d*\\.?\\d+,[-+]?\\d*\\.?\\d+(?:\\([^)]*\\))?")
    Linkify.addLinks(it, geoPattern, "geo:")
}

private fun Spannable.toAnnotatedString(linkStyles: TextLinkStyles): AnnotatedString = buildAnnotatedString {
    val spannable = this@toAnnotatedString
    var lastEnd = 0

    // Get only URLSpan objects and sort them by start position
    val urlSpans =
        spannable
            .getSpans(0, spannable.length, URLSpan::class.java)
            .map { span -> Triple(span, spannable.getSpanStart(span), spannable.getSpanEnd(span)) }
            .sortedBy { it.second }

    urlSpans.forEach { (span, start, end) ->
        // Skip overlapping spans
        if (start < lastEnd) return@forEach

        // Append text before the link
        if (start > lastEnd) {
            append(spannable.subSequence(lastEnd, start))
        }

        // Append the link
        withLink(LinkAnnotation.Url(url = span.url, styles = linkStyles)) { append(spannable.subSequence(start, end)) }

        lastEnd = end
    }

    // Append remaining text
    if (lastEnd < spannable.length) {
        append(spannable.subSequence(lastEnd, spannable.length))
    }
}

@Preview(showBackground = true)
@Composable
private fun AutoLinkTextPreview() {
    AutoLinkText("A text containing a link https://example.com")
}
