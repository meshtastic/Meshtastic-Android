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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import org.meshtastic.core.model.MENTION_TOKEN_REGEX
import org.meshtastic.core.ui.theme.HyperlinkBlue

private val DefaultTextLinkStyles =
    TextLinkStyles(style = SpanStyle(color = HyperlinkBlue, textDecoration = TextDecoration.Underline))

private val WEB_URL_REGEX =
    Regex(
        """(?:(?:https?|ftp)://|www\.)[-a-zA-Z0-9@:%._\+~#=]{1,256}""" +
            """\.[a-zA-Z0-9()]{1,6}\b(?:[-a-zA-Z0-9()@:%_\+.~#?&//=]*)""",
        RegexOption.IGNORE_CASE,
    )

private val EMAIL_REGEX =
    Regex(
        """[a-zA-Z0-9\+\.\_\%\-\+]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\-]{0,64}(?:\.[a-zA-Z0-9][a-zA-Z0-9\-]{0,25})+""",
        RegexOption.IGNORE_CASE,
    )

private val PHONE_REGEX = Regex("""(?:\+?\d{1,3}[-.\s]?)?\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}""")

private val MentionSpanStyle = SpanStyle(color = HyperlinkBlue, fontWeight = FontWeight.Bold)

/**
 * A [Text] component that automatically detects and linkifies URLs, email addresses, and phone numbers.
 *
 * When [mentionName] is supplied, `@!<hex>` mention tokens are additionally rendered as tappable text showing the
 * *current* display name resolved live (the hex id is the only thing stored on the wire), invoking [onMentionClick]
 * with the `!<hex>` id on tap.
 */
@Composable
fun AutoLinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    linkStyles: TextLinkStyles = DefaultTextLinkStyles,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    mentionName: ((String) -> String?)? = null,
    onMentionClick: ((String) -> Unit)? = null,
) {
    // Keep the click handler out of the annotated-string cache key so a fresh lambda per recomposition
    // doesn't force a rebuild; only text/name changes should.
    val currentOnMentionClick by rememberUpdatedState(onMentionClick)
    val annotatedString =
        remember(text, linkStyles, mentionName) {
            buildAnnotatedStringWithLinks(text, linkStyles, mentionName) { id -> currentOnMentionClick?.invoke(id) }
        }
    Text(text = annotatedString, modifier = modifier, style = style.copy(color = color), textAlign = textAlign)
}

private fun buildAnnotatedStringWithLinks(
    text: String,
    linkStyles: TextLinkStyles,
    mentionName: ((String) -> String?)?,
    onMentionClick: (String) -> Unit,
): AnnotatedString {
    // Substitute each mention token with its live display name up front, tracking display-space ranges.
    // URL/email/phone detection then runs over the substituted text so every offset lines up.
    val mentions = mutableListOf<Pair<IntRange, String>>() // display range -> "!hex" id
    val display =
        if (mentionName == null) {
            text
        } else {
            buildString {
                var cursor = 0
                for (match in MENTION_TOKEN_REGEX.findAll(text)) {
                    append(text, cursor, match.range.first)
                    val id = match.groupValues[1]
                    val start = length
                    append("@").append(mentionName(id) ?: id)
                    mentions.add((start until length) to id)
                    cursor = match.range.last + 1
                }
                append(text, cursor, text.length)
            }
        }

    return buildAnnotatedString {
        append(display)

        val usedIndices = mutableSetOf<Int>()

        for ((range, id) in mentions) {
            addLink(
                LinkAnnotation.Clickable(tag = "mention", styles = TextLinkStyles(MentionSpanStyle)) {
                    onMentionClick(id)
                },
                range.first,
                range.last + 1,
            )
            range.forEach { usedIndices.add(it) }
        }

        val matches = mutableListOf<Pair<IntRange, String>>()

        WEB_URL_REGEX.findAll(display).forEach { match ->
            val url = match.value
            val fullUrl = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
            matches.add(match.range to fullUrl)
        }

        EMAIL_REGEX.findAll(display).forEach { match -> matches.add(match.range to "mailto:${match.value}") }

        PHONE_REGEX.findAll(display).forEach { match -> matches.add(match.range to "tel:${match.value}") }

        // Sort by start position, then by length (longer first)
        val sortedMatches = matches.sortedWith(compareBy({ it.first.first }, { -(it.first.last - it.first.first) }))

        for ((range, url) in sortedMatches) {
            if (range.any { it in usedIndices }) continue

            addLink(LinkAnnotation.Url(url = url, styles = linkStyles), range.first, range.last + 1)
            range.forEach { usedIndices.add(it) }
        }
    }
}

/**
 * A [Text] component that highlights occurrences of [query] within [text] using the tertiary container color. Each
 * matching token in the query is highlighted independently (case-insensitive).
 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
) {
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val highlightContentColor = MaterialTheme.colorScheme.onTertiaryContainer
    val annotatedString =
        remember(text, query, highlightColor, highlightContentColor) {
            buildHighlightedString(text, query, highlightColor, highlightContentColor)
        }
    Text(text = annotatedString, modifier = modifier, style = style.copy(color = color))
}

private fun buildHighlightedString(
    text: String,
    query: String,
    highlightColor: Color,
    contentColor: Color,
): AnnotatedString = buildAnnotatedString {
    val lowerText = text.lowercase()
    val tokens = query.split("\\s+".toRegex()).filter { it.isNotBlank() }.map { it.lowercase() }
    if (tokens.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }

    // Find all match ranges
    val matchRanges = mutableListOf<IntRange>()
    for (token in tokens) {
        var start = 0
        while (start < lowerText.length) {
            val matchStart = lowerText.indexOf(token, start)
            if (matchStart == -1) break
            matchRanges.add(matchStart until matchStart + token.length)
            start = matchStart + token.length
        }
    }

    // Merge overlapping ranges and sort
    val merged = mergeRanges(matchRanges.sortedBy { it.first })

    val highlightStyle = SpanStyle(background = highlightColor, color = contentColor, fontWeight = FontWeight.Bold)

    var cursor = 0
    for (range in merged) {
        if (range.first > cursor) append(text.substring(cursor, range.first))
        withStyle(highlightStyle) { append(text.substring(range.first, range.last + 1)) }
        cursor = range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

private fun mergeRanges(sorted: List<IntRange>): List<IntRange> {
    if (sorted.isEmpty()) return emptyList()
    val result = mutableListOf(sorted.first())
    for (range in sorted.drop(1)) {
        val last = result.last()
        if (range.first <= last.last + 1) {
            result[result.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            result.add(range)
        }
    }
    return result
}
