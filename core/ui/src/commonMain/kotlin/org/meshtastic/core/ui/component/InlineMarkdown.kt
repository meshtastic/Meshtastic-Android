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

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * Parses the lightweight **inline** markdown subset shared with the iOS client — `**bold**`, `*italic*`,
 * `~~strikethrough~~`, `` `code` `` and `[label](url)` — into a delimiter-stripped [InlineMarkdownResult.displayText]
 * plus display-space [StyleSpan]s and [LinkSpan]s.
 *
 * Inline-only, matching iOS `AttributedString(markdown: .inlineOnlyPreservingWhitespace)`: block constructs (headings,
 * lists, block quotes, fenced code, tables, images) are NOT interpreted — their markers survive as literal text because
 * only the delimiter tokens that are direct children of a recognised inline element are removed. Whitespace and
 * newlines are preserved. Malformed or unpaired markup degrades to literal text and never throws.
 *
 * The result is consumed by [AutoLinkText], which layers `@mention` and bare URL/email/phone links over the same
 * [InlineMarkdownResult.displayText].
 */
fun parseInlineMarkdown(source: String): InlineMarkdownResult {
    // Empty input or a parser failure (assertions are disabled, but stay total regardless) → literal text, no spans.
    val tree = if (source.isEmpty()) null else runCatching { parser.buildMarkdownTreeFromString(source) }.getOrNull()
    return tree?.let { InlineMarkdownVisitor(source).build(it) }
        ?: InlineMarkdownResult(source, emptyList(), emptyList())
}

// assertionsEnabled = false → the parser recovers to a flat tree instead of throwing on malformed input.
private val parser = MarkdownParser(GFMFlavourDescriptor(), false)

/**
 * Style spans over the RAW [source] (delimiters kept in place) for live in-field styling. Unlike [parseInlineMarkdown]
 * this does NOT strip delimiters — it reports each bold/italic/strikethrough/code element's raw content range so a
 * text-field `OutputTransformation` can `addStyle` without changing the stored text. Links are not styled here.
 */
fun inlineMarkdownStyleRanges(source: String): List<StyleSpan> {
    val hasDelimiter = source.any { it == '*' || it == '~' || it == '`' }
    val tree =
        (if (hasDelimiter) runCatching { parser.buildMarkdownTreeFromString(source) }.getOrNull() else null)
            ?: return emptyList()
    val spans = mutableListOf<StyleSpan>()
    collectStyleRanges(tree, spans)
    return spans
}

private fun collectStyleRanges(node: ASTNode, spans: MutableList<StyleSpan>) {
    val style =
        when (node.type) {
            MarkdownElementTypes.STRONG -> InlineStyle.Bold
            MarkdownElementTypes.EMPH -> InlineStyle.Italic
            MarkdownElementTypes.CODE_SPAN -> InlineStyle.Code
            GFMElementTypes.STRIKETHROUGH -> InlineStyle.Strikethrough
            else -> null
        }
    if (style != null) {
        val content = node.children.filter { !(it.children.isEmpty() && it.type in delimiterTokens) }
        val start = content.firstOrNull()?.startOffset
        val end = content.lastOrNull()?.endOffset
        if (start != null && end != null && end > start) spans.add(StyleSpan(start until end, style))
    }
    node.children.forEach { collectStyleRanges(it, spans) }
}

/** The five inline styles shared with iOS. */
enum class InlineStyle {
    Bold,
    Italic,
    Strikethrough,
    Code,
    Link,
}

/** A non-link inline style applied over a half-open [range] in display-space. */
data class StyleSpan(val range: IntRange, val style: InlineStyle)

/** A markdown link covering the display-space [range] of its label, pointing at [url]. */
data class LinkSpan(val range: IntRange, val url: String)

/** Output of [parseInlineMarkdown]: the stripped text plus spans, all in display-space offsets. */
data class InlineMarkdownResult(val displayText: String, val styleSpans: List<StyleSpan>, val linkSpans: List<LinkSpan>)

/** Delimiter tokens removed when they are direct children of a recognised inline element. */
private val delimiterTokens =
    setOf(
        MarkdownTokenTypes.EMPH,
        MarkdownTokenTypes.BACKTICK,
        MarkdownTokenTypes.ESCAPED_BACKTICKS,
        GFMTokenTypes.TILDE,
    )

private class InlineMarkdownVisitor(private val source: String) {
    private val out = StringBuilder()
    private val styleSpans = mutableListOf<StyleSpan>()
    private val linkSpans = mutableListOf<LinkSpan>()

    fun build(root: ASTNode): InlineMarkdownResult {
        visit(root, emptySet())
        return InlineMarkdownResult(out.toString(), styleSpans.toList(), linkSpans.toList())
    }

    private fun visit(node: ASTNode, styles: Set<InlineStyle>) {
        when (node.type) {
            MarkdownElementTypes.STRONG -> visitStyled(node, styles, InlineStyle.Bold)

            MarkdownElementTypes.EMPH -> visitStyled(node, styles, InlineStyle.Italic)

            GFMElementTypes.STRIKETHROUGH -> visitStyled(node, styles, InlineStyle.Strikethrough)

            MarkdownElementTypes.CODE_SPAN -> visitCodeSpan(node, styles)

            MarkdownElementTypes.INLINE_LINK -> visitLink(node, styles)

            // Images are not interpreted (inline-only): render the raw source literally.
            MarkdownElementTypes.IMAGE -> emitRaw(node, styles)

            else -> if (node.children.isEmpty()) emitRaw(node, styles) else node.children.forEach { visit(it, styles) }
        }
    }

    private fun visitStyled(node: ASTNode, styles: Set<InlineStyle>, style: InlineStyle) {
        val next = styles + style
        node.children.forEach { child ->
            // Skip the leaf open/close markers; recurse everything else (content may itself be styled/nested).
            if (child.children.isEmpty() && child.type in delimiterTokens) return@forEach
            visit(child, next)
        }
    }

    private fun visitCodeSpan(node: ASTNode, styles: Set<InlineStyle>) {
        // A code span's content is literal and never re-interpreted, so emit everything between the backtick runs
        // as ONE contiguous chunk — the lexer may split the inner text (e.g. a literal `*`) into several tokens,
        // but it must render as a single monospace span.
        val content =
            node.children.filter {
                it.type != MarkdownTokenTypes.BACKTICK && it.type != MarkdownTokenTypes.ESCAPED_BACKTICKS
            }
        val startOffset = content.firstOrNull()?.startOffset ?: return
        val endOffset = content.last().endOffset
        val start = out.length
        out.append(source, startOffset, endOffset)
        val end = out.length
        if (end > start) {
            (styles + InlineStyle.Code).forEach { style ->
                if (style != InlineStyle.Link) styleSpans.add(StyleSpan(start until end, style))
            }
        }
    }

    private fun visitLink(node: ASTNode, styles: Set<InlineStyle>) {
        val linkText = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
        val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
        if (linkText == null || destination == null) {
            // Malformed link — render literally.
            if (node.children.isEmpty()) emitRaw(node, styles) else node.children.forEach { visit(it, styles) }
            return
        }
        val url = source.substring(destination.startOffset, destination.endOffset)
        val start = out.length
        linkText.children.forEach { child ->
            // LINK_TEXT wraps its content in `[` … `]`; drop the brackets, keep (and style) the label.
            if (child.type == MarkdownTokenTypes.LBRACKET || child.type == MarkdownTokenTypes.RBRACKET) return@forEach
            visit(child, styles)
        }
        val end = out.length
        if (end > start) linkSpans.add(LinkSpan(start until end, url))
    }

    private fun emitRaw(node: ASTNode, styles: Set<InlineStyle>) {
        val start = out.length
        out.append(source, node.startOffset, node.endOffset)
        val end = out.length
        if (end > start) {
            styles.forEach { style -> if (style != InlineStyle.Link) styleSpans.add(StyleSpan(start until end, style)) }
        }
    }
}
