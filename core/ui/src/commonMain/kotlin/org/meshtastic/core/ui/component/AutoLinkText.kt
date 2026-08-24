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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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

private const val MARKDOWN_DELIMITERS = "*~`["

private data class MentionReplacement(val start: Int, val oldLength: Int, val newLength: Int)

// Maps an offset in the parsed (markdown-stripped) text to the final display text after mention substitution:
// every mention replacement that ends at or before the offset shifts it by its length delta. A replacement that
// falls strictly inside a span (start before, end after) shifts only the span's end, so the span grows to cover
// the substituted display name.
private fun Int.shiftedBy(replacements: List<MentionReplacement>): Int {
    var delta = 0
    for (replacement in replacements) {
        if (replacement.start + replacement.oldLength <= this) delta += replacement.newLength - replacement.oldLength
    }
    return this + delta
}

private fun IntRange.shiftedBy(replacements: List<MentionReplacement>): IntRange =
    first.shiftedBy(replacements) until (last + 1).shiftedBy(replacements)

private fun InlineStyle.toSpanStyle(): SpanStyle = when (this) {
    InlineStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
    InlineStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
    InlineStyle.Strikethrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    InlineStyle.Code -> SpanStyle(fontFamily = FontFamily.Monospace)
    InlineStyle.Link -> SpanStyle()
}

// Interleaves four span sources (mentions, markdown styles, markdown links, bare autolinks) into one AnnotatedString;
// the per-source application loops keep it just over the default complexity threshold.
@Suppress("CyclomaticComplexMethod")
internal fun buildAnnotatedStringWithLinks(
    text: String,
    linkStyles: TextLinkStyles,
    mentionName: ((String) -> String?)?,
    onMentionClick: (String) -> Unit,
): AnnotatedString {
    // 1) Inline markdown over the raw text. Mention tokens (`@!<hex>`) contain no markdown delimiters, so they
    //    survive parsing untouched. Skip parsing when there is no delimiter at all — this also leaves emoji-only
    //    messages (and plain text) unchanged (FR-006) and avoids the parser on the common case.
    val parsed =
        if (text.any { it in MARKDOWN_DELIMITERS }) {
            parseInlineMarkdown(text)
        } else {
            InlineMarkdownResult(text, emptyList(), emptyList())
        }

    // 2) Substitute each mention token with its live display name, then shift the markdown spans so they stay aligned.
    val substitution = substituteMentions(parsed.displayText, mentionName)
    val display = substitution.display
    val styleSpans = parsed.styleSpans.map { it.range.shiftedBy(substitution.replacements) to it.style }
    val linkSpans = parsed.linkSpans.map { it.range.shiftedBy(substitution.replacements) to it.url }
    val autoLinks = collectAutolinkMatches(display)

    return buildAnnotatedString {
        append(display)

        val usedIndices = mutableSetOf<Int>()

        for ((range, id) in substitution.mentions) {
            addLink(
                LinkAnnotation.Clickable(tag = "mention", styles = TextLinkStyles(MentionSpanStyle)) {
                    onMentionClick(id)
                },
                range.first,
                range.last + 1,
            )
            range.forEach { usedIndices.add(it) }
        }

        // Markdown character styles (bold/italic/strikethrough/code) — may overlap mentions harmlessly.
        for ((range, style) in styleSpans) {
            val end = range.last + 1
            if (range.first in display.indices && range.first < end && end <= display.length) {
                addStyle(style.toSpanStyle(), range.first, end)
            }
        }

        // Markdown links (`[label](url)`) take precedence over bare-URL autolinking of the same span.
        for ((range, url) in linkSpans) {
            if (range.first !in display.indices || range.any { it in usedIndices }) continue
            addLink(LinkAnnotation.Url(url = url, styles = linkStyles), range.first, range.last + 1)
            range.forEach { usedIndices.add(it) }
        }

        // Bare URL/email/phone autolinking, skipping anything already covered by a mention or markdown link.
        for ((range, url) in autoLinks) {
            if (range.any { it in usedIndices }) continue
            addLink(LinkAnnotation.Url(url = url, styles = linkStyles), range.first, range.last + 1)
            range.forEach { usedIndices.add(it) }
        }
    }
}

/** Result of mention substitution: the substituted [display] text, its mention ranges, and the length deltas. */
private data class MentionSubstitution(
    val display: String,
    val mentions: List<Pair<IntRange, String>>,
    val replacements: List<MentionReplacement>,
)

/** Replaces `@!<hex>` tokens in [source] with their live display names, recording ranges and length deltas. */
private fun substituteMentions(source: String, mentionName: ((String) -> String?)?): MentionSubstitution {
    if (mentionName == null) return MentionSubstitution(source, emptyList(), emptyList())
    val mentions = mutableListOf<Pair<IntRange, String>>()
    val replacements = mutableListOf<MentionReplacement>()
    val display = buildString {
        var cursor = 0
        for (match in MENTION_TOKEN_REGEX.findAll(source)) {
            append(source, cursor, match.range.first)
            val id = match.groupValues[1]
            val name = "@" + (mentionName(id) ?: id)
            val start = length
            append(name)
            mentions.add((start until length) to id)
            replacements.add(
                MentionReplacement(match.range.first, match.range.last - match.range.first + 1, name.length),
            )
            cursor = match.range.last + 1
        }
        append(source, cursor, source.length)
    }
    return MentionSubstitution(display, mentions, replacements)
}

/** Collects bare URL/email/phone matches in [display], sorted by start then longest-first. */
private fun collectAutolinkMatches(display: String): List<Pair<IntRange, String>> {
    val matches = mutableListOf<Pair<IntRange, String>>()
    WEB_URL_REGEX.findAll(display).forEach { match ->
        val url = match.value
        matches.add(match.range to if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url)
    }
    EMAIL_REGEX.findAll(display).forEach { match -> matches.add(match.range to "mailto:${match.value}") }
    PHONE_REGEX.findAll(display).forEach { match -> matches.add(match.range to "tel:${match.value}") }
    return matches.sortedWith(compareBy({ it.first.first }, { -(it.first.last - it.first.first) }))
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
