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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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

/** A [Text] component that automatically detects and linkifies URLs, email addresses, and phone numbers. */
@Composable
fun AutoLinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    linkStyles: TextLinkStyles = DefaultTextLinkStyles,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val annotatedString = remember(text, linkStyles) { buildAnnotatedStringWithLinks(text, linkStyles) }
    Text(text = annotatedString, modifier = modifier, style = style.copy(color = color), textAlign = textAlign)
}

private fun buildAnnotatedStringWithLinks(text: String, linkStyles: TextLinkStyles): AnnotatedString =
    buildAnnotatedString {
        append(text)

        val matches = mutableListOf<Pair<IntRange, String>>()

        WEB_URL_REGEX.findAll(text).forEach { match ->
            val url = match.value
            val fullUrl = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
            matches.add(match.range to fullUrl)
        }

        EMAIL_REGEX.findAll(text).forEach { match -> matches.add(match.range to "mailto:${match.value}") }

        PHONE_REGEX.findAll(text).forEach { match -> matches.add(match.range to "tel:${match.value}") }

        // Sort by start position, then by length (longer first)
        val sortedMatches = matches.sortedWith(compareBy({ it.first.first }, { -(it.first.last - it.first.first) }))

        val usedIndices = mutableSetOf<Int>()
        for ((range, url) in sortedMatches) {
            if (range.any { it in usedIndices }) continue

            addLink(LinkAnnotation.Url(url = url, styles = linkStyles), range.first, range.last + 1)
            range.forEach { usedIndices.add(it) }
        }
    }
