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

/**
 * Segments markdown into translatable and non-translatable blocks, translates the text portions, and reassembles valid
 * markdown.
 *
 * Preserves:
 * - Fenced code blocks (``` or ~~~)
 * - Indented code blocks (4+ spaces or tab)
 * - Link URLs and image paths (translates link text only)
 * - HTML tags
 * - Frontmatter (--- delimited YAML)
 *
 * Translates:
 * - Paragraphs, headings, list items, blockquotes, table cell text
 */
@Suppress("TooManyFunctions")
object MarkdownTranslationSegmenter {

    /**
     * Translate markdown content by extracting text segments, translating them, and reassembling.
     *
     * @param markdown The source markdown content
     * @param translate A suspend function that translates a plain text string
     * @return The translated markdown with structure preserved
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    suspend fun translateMarkdown(markdown: String, translate: suspend (String) -> String): String {
        val lines = markdown.lines()
        val result = StringBuilder()
        var i = 0

        // Skip frontmatter
        if (lines.isNotEmpty() && lines[0].trim() == "---") {
            result.appendLine(lines[0])
            i = 1
            while (i < lines.size) {
                result.appendLine(lines[i])
                if (lines[i].trim() == "---") {
                    i++
                    break
                }
                i++
            }
        }

        while (i < lines.size) {
            val line = lines[i]

            when {
                // Fenced code block
                isFencedCodeStart(line) -> {
                    val fence = extractFence(line)
                    result.appendLine(line)
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                        result.appendLine(lines[i])
                        i++
                    }
                    if (i < lines.size) {
                        result.appendLine(lines[i])
                        i++
                    }
                }

                // Indented code block (4 spaces or tab, and previous line is blank or start)
                isIndentedCode(line) && (i == 0 || lines[i - 1].isBlank()) -> {
                    while (i < lines.size && (isIndentedCode(lines[i]) || lines[i].isBlank())) {
                        result.appendLine(lines[i])
                        i++
                    }
                }

                // HTML block (starts with <)
                line.trimStart().startsWith("<") && isHtmlBlock(line) -> {
                    result.appendLine(line)
                    i++
                }

                // Empty line
                line.isBlank() -> {
                    result.appendLine(line)
                    i++
                }

                // Heading (ATX: # followed by space or end-of-line)
                line.trimStart().matches(Regex("^#{1,6}(\\s.*|$)")) -> {
                    val headingPrefix =
                        line.substring(0, line.indexOf('#')) +
                            line.substring(line.indexOf('#')).takeWhile { it == '#' } +
                            " "
                    val text = line.substring(headingPrefix.length)
                    val translated = if (text.isNotBlank()) translate(text) else text
                    result.appendLine("$headingPrefix$translated")
                    i++
                }

                // Blockquote
                line.trimStart().startsWith(">") -> {
                    val stripped = line.trimStart().removePrefix(">").trimStart()
                    val indent = line.takeWhile { it != '>' }
                    val translated = if (stripped.isNotBlank()) translate(stripped) else stripped
                    result.appendLine("$indent> $translated")
                    i++
                }

                // List item (unordered or ordered)
                isListItem(line) -> {
                    val (listPrefix, text) = splitListItem(line)
                    val translatedText = translateInlineMarkdown(text, translate)
                    result.appendLine("$listPrefix$translatedText")
                    i++
                }

                // Table row
                line.trimStart().startsWith("|") -> {
                    if (isTableSeparator(line)) {
                        result.appendLine(line)
                    } else {
                        val translated = translateTableRow(line, translate)
                        result.appendLine(translated)
                    }
                    i++
                }

                // Regular paragraph text
                else -> {
                    val translated = translateInlineMarkdown(line, translate)
                    result.appendLine(translated)
                    i++
                }
            }
        }

        // Remove trailing newline added by appendLine on last line
        return result.toString().trimEnd('\n')
    }

    /** Translate text while preserving inline markdown elements like links, images, and inline code. */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun translateInlineMarkdown(text: String, translate: suspend (String) -> String): String {
        if (text.isBlank()) return text

        val segments = mutableListOf<Segment>()
        var pos = 0

        while (pos < text.length) {
            when {
                // Inline code
                text[pos] == '`' -> {
                    val end = text.indexOf('`', pos + 1)
                    if (end > pos) {
                        segments.add(Segment.Verbatim(text.substring(pos, end + 1)))
                        pos = end + 1
                    } else {
                        segments.add(Segment.Translatable(text[pos].toString()))
                        pos++
                    }
                }

                // Image: ![alt](url)
                text[pos] == '!' && pos + 1 < text.length && text[pos + 1] == '[' -> {
                    val closeBracket = text.indexOf(']', pos + 2)
                    if (closeBracket > pos && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > closeBracket) {
                            segments.add(Segment.Verbatim(text.substring(pos, closeParen + 1)))
                            pos = closeParen + 1
                        } else {
                            segments.add(Segment.Translatable(text[pos].toString()))
                            pos++
                        }
                    } else {
                        segments.add(Segment.Translatable(text[pos].toString()))
                        pos++
                    }
                }

                // Link: [text](url)
                text[pos] == '[' -> {
                    val closeBracket = text.indexOf(']', pos + 1)
                    if (closeBracket > pos && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > closeBracket) {
                            val linkText = text.substring(pos + 1, closeBracket)
                            val url = text.substring(closeBracket + 2, closeParen)
                            segments.add(Segment.Link(linkText, url))
                            pos = closeParen + 1
                        } else {
                            segments.add(Segment.Translatable(text[pos].toString()))
                            pos++
                        }
                    } else {
                        segments.add(Segment.Translatable(text[pos].toString()))
                        pos++
                    }
                }

                else -> {
                    // Accumulate regular text
                    val start = pos
                    while (pos < text.length && !isInlineMarker(text, pos)) {
                        pos++
                    }
                    segments.add(Segment.Translatable(text.substring(start, pos)))
                }
            }
        }

        // Translate all translatable segments
        return segments
            .joinToString("") { segment ->
                when (segment) {
                    is Segment.Verbatim -> segment.text

                    is Segment.Translatable ->
                        if (segment.text.isNotBlank()) {
                            // We can't suspend in joinToString, so we pre-translate below
                            segment.text
                        } else {
                            segment.text
                        }

                    is Segment.Link -> "[${segment.text}](${segment.url})"
                }
            }
            .let { assembled ->
                // Check if there's anything to translate (text segments or link text)
                val translatableText = segments.filterIsInstance<Segment.Translatable>().joinToString("") { it.text }
                val hasTranslatableLinks = segments.any { it is Segment.Link && it.text.isNotBlank() }
                if (translatableText.isBlank() && !hasTranslatableLinks) return@let assembled

                // Translate with preserved inline elements (handles both text and link text)
                translateWithPreservedInlines(segments, translate)
            }
    }

    private suspend fun translateWithPreservedInlines(
        segments: List<Segment>,
        translate: suspend (String) -> String,
    ): String {
        val result = StringBuilder()
        // Group consecutive translatables, translate them together, preserve verbatim/links
        val buffer = StringBuilder()

        for (segment in segments) {
            when (segment) {
                is Segment.Translatable -> buffer.append(segment.text)

                is Segment.Verbatim -> {
                    if (buffer.isNotBlank()) {
                        result.append(translate(buffer.toString()))
                    } else {
                        result.append(buffer)
                    }
                    buffer.clear()
                    result.append(segment.text)
                }

                is Segment.Link -> {
                    if (buffer.isNotBlank()) {
                        result.append(translate(buffer.toString()))
                    } else {
                        result.append(buffer)
                    }
                    buffer.clear()
                    val translatedLinkText = if (segment.text.isNotBlank()) translate(segment.text) else segment.text
                    result.append("[$translatedLinkText](${segment.url})")
                }
            }
        }
        if (buffer.isNotBlank()) {
            result.append(translate(buffer.toString()))
        } else {
            result.append(buffer)
        }

        return result.toString()
    }

    private suspend fun translateTableRow(line: String, translate: suspend (String) -> String): String {
        val cells = line.split("|")
        val translated =
            cells.map { cell ->
                val trimmed = cell.trim()
                if (trimmed.isNotBlank()) " ${translate(trimmed)} " else cell
            }
        return translated.joinToString("|")
    }

    private fun isFencedCodeStart(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("```") || trimmed.startsWith("~~~")
    }

    private fun isInlineMarker(text: String, pos: Int): Boolean =
        text[pos] == '`' || text[pos] == '[' || (text[pos] == '!' && pos + 1 < text.length && text[pos + 1] == '[')

    private fun extractFence(line: String): String {
        val trimmed = line.trimStart()
        val fenceChar = trimmed[0]
        return trimmed.takeWhile { it == fenceChar }
    }

    private fun isIndentedCode(line: String): Boolean = line.startsWith("    ") || line.startsWith("\t")

    private fun isHtmlBlock(line: String): Boolean {
        val trimmed = line.trimStart().lowercase()
        return trimmed.startsWith("<div") ||
            trimmed.startsWith("</div") ||
            trimmed.startsWith("<table") ||
            trimmed.startsWith("<pre") ||
            trimmed.startsWith("<!--") ||
            trimmed.startsWith("<br") ||
            trimmed.startsWith("<hr")
    }

    private fun isListItem(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("- ") ||
            trimmed.startsWith("* ") ||
            trimmed.startsWith("+ ") ||
            ORDERED_LIST_REGEX.containsMatchIn(trimmed)
    }

    private fun splitListItem(line: String): Pair<String, String> {
        val leadingWhitespace = line.takeWhile { it == ' ' || it == '\t' }
        val trimmed = line.trimStart()
        return when {
            trimmed.startsWith("- ") -> Pair("$leadingWhitespace- ", trimmed.removePrefix("- "))

            trimmed.startsWith("* ") -> Pair("$leadingWhitespace* ", trimmed.removePrefix("* "))

            trimmed.startsWith("+ ") -> Pair("$leadingWhitespace+ ", trimmed.removePrefix("+ "))

            else -> {
                val match = ORDERED_LIST_REGEX.find(trimmed)
                if (match != null) {
                    Pair("$leadingWhitespace${match.value}", trimmed.removePrefix(match.value))
                } else {
                    Pair(leadingWhitespace, trimmed)
                }
            }
        }
    }

    private fun isTableSeparator(line: String): Boolean =
        line.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").isEmpty()

    private sealed class Segment {
        data class Translatable(val text: String) : Segment()

        data class Verbatim(val text: String) : Segment()

        data class Link(val text: String, val url: String) : Segment()
    }

    private val ORDERED_LIST_REGEX = Regex("^\\d+[.)]+\\s")
}
