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

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

private val NamedEntities =
    mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0")

private val HrefPattern = Regex("""href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

private const val HEX_RADIX = 16

/**
 * Renders the markup the app's own strings use, `<a href>`, `<b>`, `<strong>`, `<i>`, `<em>`, `<u>`, `<br>` and `<p>`,
 * for platforms without Android's `Html.fromHtml`. Any other tag is dropped and its text kept, and whitespace collapses
 * as in HTML.
 */
internal fun annotatedStringFromSimpleHtml(html: String, linkStyles: TextLinkStyles?): AnnotatedString =
    buildAnnotatedString {
        val open = ArrayList<String>()
        var lastWasSpace = true
        var i = 0
        while (i < html.length) {
            val c = html[i]
            when {
                c == '<' && html.indexOf('>', i) > i -> {
                    val end = html.indexOf('>', i)
                    val tag = html.substring(i + 1, end).trim()
                    val closing = tag.startsWith("/")
                    val name = tag.removePrefix("/").takeWhile { it.isLetterOrDigit() }.lowercase()
                    when {
                        name == "br" -> {
                            append('\n')
                            lastWasSpace = true
                        }

                        name == "p" -> {
                            if (!closing && length > 0) append("\n\n")
                            lastWasSpace = true
                        }

                        closing -> {
                            val at = open.lastIndexOf(name)
                            if (at >= 0) {
                                repeat(open.size - at) { pop() }
                                while (open.size > at) open.removeAt(open.size - 1)
                            }
                        }

                        else -> if (pushTag(name, tag, linkStyles)) open.add(name)
                    }
                    i = end + 1
                }

                c == '&' -> {
                    val end = html.indexOf(';', i)
                    val decoded = if (end > i) decodeEntity(html.substring(i + 1, end)) else null
                    if (decoded != null) {
                        append(decoded)
                        lastWasSpace = false
                        i = end + 1
                    } else {
                        append(c)
                        lastWasSpace = false
                        i++
                    }
                }

                c.isWhitespace() -> {
                    if (!lastWasSpace) append(' ')
                    lastWasSpace = true
                    i++
                }

                else -> {
                    append(c)
                    lastWasSpace = false
                    i++
                }
            }
        }
        repeat(open.size) { pop() }
    }

/** Pushes the style or link [name] opens and returns true, or returns false for a tag that carries none. */
private fun AnnotatedString.Builder.pushTag(name: String, tag: String, linkStyles: TextLinkStyles?): Boolean {
    val style =
        when (name) {
            "b",
            "strong",
            -> SpanStyle(fontWeight = FontWeight.Bold)

            "i",
            "em",
            -> SpanStyle(fontStyle = FontStyle.Italic)

            "u" -> SpanStyle(textDecoration = TextDecoration.Underline)

            else -> null
        }
    val href = if (name == "a") HrefPattern.find(tag)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() } else null
    when {
        style != null -> pushStyle(style)
        href != null -> pushLink(LinkAnnotation.Url(href, linkStyles))
    }
    return style != null || href != null
}

private fun decodeEntity(entity: String): String? = when {
    entity.startsWith("#x", ignoreCase = true) -> entity.drop(2).toIntOrNull(HEX_RADIX)?.let(::codePointString)
    entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.let(::codePointString)
    else -> NamedEntities[entity.lowercase()]
}

// Only the Basic Multilingual Plane: the app's strings carry nothing beyond it.
private fun codePointString(codePoint: Int): String? =
    codePoint.takeIf { it in 0..Char.MAX_VALUE.code }?.toChar()?.toString()
