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

// Pure text-mutation helpers backing the formatting toolbar, mirroring the iOS client's MarkdownFormatting.swift so
// the two platforms produce byte-for-byte identical markup for the same selection + action. No UI-framework
// dependency beyond the multiplatform `TextRange` value type, so these run in commonTest.

/**
 * Toggles [style] markup around the selection [range] of [text]: wraps an unwrapped selection, removes the delimiters
 * from an already-wrapped one, and inserts an empty delimiter pair for a collapsed cursor. Whitespace is kept outside
 * the delimiters, delimiter characters the selection cuts through are absorbed, and orphaned delimiters left elsewhere
 * are cleaned up.
 */
@Suppress("ReturnCount") // Guard clauses (toggle-off, empty-selection) read more clearly than one nested expression.
fun wrapSelection(text: String, range: TextRange, style: InlineStyle): FormattingResult {
    val opening = style.openingDelimiter()
    val closing = style.closingDelimiter()
    val start = range.min
    val end = range.max

    val hasOpeningBefore = start - opening.length >= 0 && text.substring(start - opening.length, start) == opening
    val hasClosingAfter = end + closing.length <= text.length && text.substring(end, end + closing.length) == closing
    if (hasOpeningBefore && hasClosingAfter) {
        val delimiterStart = start - opening.length
        val delimiterEnd = end + closing.length
        val newText = buildString {
            append(text, 0, delimiterStart)
            append(text, start, end)
            append(text, delimiterEnd, text.length)
        }
        val resultStart = delimiterStart
        return FormattingResult(newText, TextRange(resultStart, resultStart + (end - start)))
    }

    val (expandedStart, expandedEnd) = expandToDelimiterBoundaries(text, start, end)
    val cleaned = text.substring(expandedStart, expandedEnd).filter { it !in DELIMITER_CHARS }
    val firstNonWs = cleaned.indexOfFirst { !it.isWhitespace() }
    if (firstNonWs < 0) return insertDelimiters(text, expandedStart, style)

    val lastNonWs = cleaned.indexOfLast { !it.isWhitespace() }
    val leadingWs = cleaned.substring(0, firstNonWs)
    val trailingWs = cleaned.substring(lastNonWs + 1)
    val trimmed = cleaned.substring(firstNonWs, lastNonWs + 1)

    val wrapped = leadingWs + opening + trimmed + closing + trailingWs
    val newText =
        (text.substring(0, expandedStart) + wrapped + text.substring(expandedEnd)).let(::cleanOrphanedDelimiters)

    val fullWrapped = opening + trimmed + closing
    val found = newText.indexOf(fullWrapped)
    return if (found >= 0) {
        FormattingResult(newText, TextRange(found, found + fullWrapped.length))
    } else {
        val selStart = (expandedStart + leadingWs.length).coerceIn(0, newText.length)
        val selEnd = (selStart + fullWrapped.length).coerceIn(selStart, newText.length)
        FormattingResult(newText, TextRange(selStart, selEnd))
    }
}

/** Inserts an empty [style] delimiter pair at [index], returning a collapsed cursor between the delimiters. */
fun insertDelimiters(text: String, index: Int, style: InlineStyle): FormattingResult {
    val opening = style.openingDelimiter()
    val closing = style.closingDelimiter()
    val newText = text.substring(0, index) + opening + closing + text.substring(index)
    val cursor = index + opening.length
    return FormattingResult(newText, TextRange(cursor))
}

/** Wraps the selection as `[selection](url)`, or inserts a `[link text](url)` placeholder for a collapsed cursor. */
fun wrapSelectionWithLink(text: String, range: TextRange, url: String): FormattingResult {
    val start = range.min
    val end = range.max
    val selected = text.substring(start, end)
    val replacement = if (selected.isEmpty()) "[link text]($url)" else "[$selected]($url)"
    val newText = text.substring(0, start) + replacement + text.substring(end)
    return FormattingResult(newText, TextRange(start, start + replacement.length))
}

/** Unwraps a `[label](url)` selection back to its label, or returns null when the selection is not a markdown link. */
fun unwrapLink(text: String, range: TextRange): FormattingResult? {
    val start = range.min
    val end = range.max
    val match = LINK_PATTERN.find(text.substring(start, end)) ?: return null
    val label = match.groupValues[1]
    val newText = text.substring(0, start) + label + text.substring(end)
    return FormattingResult(newText, TextRange(start, start + label.length))
}

/** True when [text] is exactly a `[label](url)` markdown link. */
fun isMarkdownLink(text: String): Boolean = LINK_PATTERN.matches(text)

/** True when [text] contains any recognised paired inline-markdown syntax. */
fun containsMarkdownSyntax(text: String): Boolean {
    if (text.isEmpty()) return false
    // Italic strips `**` first so a bold run isn't mistaken for italic (avoids KMP-unsafe lookbehind).
    return BOLD_PATTERN.containsMatchIn(text) ||
        ITALIC_PATTERN.containsMatchIn(text.replace("**", "")) ||
        STRIKE_PATTERN.containsMatchIn(text) ||
        CODE_PATTERN.containsMatchIn(text) ||
        INLINE_LINK_PATTERN.containsMatchIn(text)
}

/** New raw text plus the selection to apply to the field after a formatting action. */
data class FormattingResult(val text: String, val selection: TextRange)

private val DELIMITER_CHARS = setOf('*', '~', '`')
private val LINK_PATTERN = Regex("^\\[([^\\]]+)\\]\\(([^)]+)\\)$")
private val BOLD_PATTERN = Regex("\\*\\*[^*]+\\*\\*")
private val ITALIC_PATTERN = Regex("\\*[^*]+\\*")
private val STRIKE_PATTERN = Regex("~~[^~]+~~")
private val CODE_PATTERN = Regex("`[^`]+`")
private val INLINE_LINK_PATTERN = Regex("\\[[^\\]]+\\]\\([^)]+\\)")

private fun InlineStyle.openingDelimiter(): String = when (this) {
    InlineStyle.Bold -> "**"
    InlineStyle.Italic -> "*"
    InlineStyle.Strikethrough -> "~~"
    InlineStyle.Code -> "`"
    InlineStyle.Link -> "["
}

private fun InlineStyle.closingDelimiter(): String = if (this == InlineStyle.Link) "]" else openingDelimiter()

/**
 * Expands [start]..[end] outward across any contiguous delimiter characters they touch, so wrapping never leaves a
 * half-delimiter behind.
 */
private fun expandToDelimiterBoundaries(text: String, start: Int, end: Int): Pair<Int, Int> {
    val insideHasDelimiter = text.substring(start, end).any { it in DELIMITER_CHARS }
    val beforeIsDelimiter = start > 0 && text[start - 1] in DELIMITER_CHARS
    val afterIsDelimiter = end < text.length && text[end] in DELIMITER_CHARS
    if (!insideHasDelimiter && !beforeIsDelimiter && !afterIsDelimiter) return start to end

    var lower = start
    while (lower > 0 && text[lower - 1] in DELIMITER_CHARS) lower--
    var upper = end
    while (upper < text.length && text[upper] in DELIMITER_CHARS) upper++
    return lower to upper
}

/** Removes unpaired (odd-count) delimiter runs, preserving properly paired `**`, `~~`, `` ` `` and `*`. */
private fun cleanOrphanedDelimiters(text: String): String {
    var result = text
    for (delimiter in listOf("**", "~~", "`", "*")) {
        result = cleanOrphanedPairs(result, delimiter)
    }
    return result
}

private fun cleanOrphanedPairs(text: String, delimiter: String): String {
    var count = 0
    var index = text.indexOf(delimiter)
    while (index >= 0) {
        count++
        index = text.indexOf(delimiter, index + delimiter.length)
    }
    if (count % 2 == 0) return text
    val last = text.lastIndexOf(delimiter)
    return if (last < 0) text else text.removeRange(last, last + delimiter.length)
}
