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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import org.meshtastic.core.ui.theme.HyperlinkBlue
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography

@Composable
fun MDText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
) {
    val colors =
        DefaultMarkdownColors(
            text = color,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
            inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
            dividerColor = MaterialTheme.colorScheme.onSurface,
            tableBackground = MaterialTheme.colorScheme.surfaceContainer,
        )

    val typography =
        DefaultMarkdownTypography(
            // Restrict max size of the text
            h1 = MaterialTheme.typography.headlineMedium.copy(color = color),
            h2 = MaterialTheme.typography.headlineMedium.copy(color = color),
            h3 = MaterialTheme.typography.headlineSmall.copy(color = color),
            h4 = MaterialTheme.typography.titleLarge.copy(color = color),
            h5 = MaterialTheme.typography.titleMedium.copy(color = color),
            h6 = MaterialTheme.typography.titleSmall.copy(color = color),
            text = style,
            code =
            MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            inlineCode =
            MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                background = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            quote = MaterialTheme.typography.bodyLarge.copy(color = color),
            paragraph = MaterialTheme.typography.bodyMedium.copy(color = color),
            ordered = MaterialTheme.typography.bodyMedium.copy(color = color),
            bullet = MaterialTheme.typography.bodyMedium.copy(color = color),
            list = MaterialTheme.typography.bodyMedium.copy(color = color),
            textLink =
            TextLinkStyles(style = SpanStyle(color = HyperlinkBlue, textDecoration = TextDecoration.Underline)),
            table = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        )

    // Custom Markdown components to disable image rendering
    val customComponents = markdownComponents(image = { /* Empty composable to disable image rendering */ })

    Markdown(
        content = text,
        modifier = modifier,
        colors = colors,
        typography = typography,
        components = customComponents, // Use custom components
    )
}

@Preview(showBackground = true)
@Composable
private fun AutoLinkTextPreview() {
    MDText(
        "A text containing a link https://example.com **bold** _Italics_" +
            "\n # hello \n ## hello \n ### hello \n #### hello \n ##### hello \n ###### hello \n ```code```",
    )
}
