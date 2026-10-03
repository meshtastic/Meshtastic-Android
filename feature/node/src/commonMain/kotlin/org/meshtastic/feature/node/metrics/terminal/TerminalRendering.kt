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
package org.meshtastic.feature.node.metrics.terminal

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/** Block drawn at the cursor. */
internal const val CURSOR_GLYPH = "█"

private const val ANSI_BRIGHT_OFFSET = 8
private const val DIM_ALPHA = 0.6f
private const val UNSENT_ALPHA = 0.45f
private const val LUMINANCE_DARK = 0.5f
private const val CUBE_BASE = 16
private const val CUBE_SIDE = 6
private const val GRAY_BASE = 232
private const val CUBE_FIRST_STEP = 55
private const val CUBE_STEP = 40
private const val GRAY_FIRST = 8
private const val GRAY_STEP = 10
private const val RGB_MASK = 0xFFFFFF
private const val OPAQUE = 0xFF000000L
private const val BYTE = 8

/** The 16 ANSI colours for the theme's surface: Tango-derived, with a darker set for light themes. */
@Immutable
internal class TerminalPalette(
    val ansi: List<Color>,
    val foreground: Color,
    val background: Color,
    val notice: Color,
) {
    /** Colour for a [CellStyle] colour value, or null for the default. */
    fun resolve(value: Int): Color? = when {
        value == DEFAULT_COLOR -> null
        value and TRUECOLOR_FLAG != 0 -> Color(OPAQUE or (value and RGB_MASK).toLong())
        value < CUBE_BASE -> ansi[value]
        value < GRAY_BASE -> cube(value - CUBE_BASE)
        else -> gray(value - GRAY_BASE)
    }

    private fun cube(index: Int): Color {
        fun step(v: Int) = if (v == 0) 0 else CUBE_FIRST_STEP + v * CUBE_STEP
        val r = step(index / (CUBE_SIDE * CUBE_SIDE))
        val g = step(index / CUBE_SIDE % CUBE_SIDE)
        val b = step(index % CUBE_SIDE)
        return Color(OPAQUE or (r.toLong() shl (2 * BYTE)) or (g.toLong() shl BYTE) or b.toLong())
    }

    private fun gray(index: Int): Color {
        val v = (GRAY_FIRST + index * GRAY_STEP).toLong()
        return Color(OPAQUE or (v shl (2 * BYTE)) or (v shl BYTE) or v)
    }

    companion object {
        private val DARK =
            listOf(
                0xFF2E3436,
                0xFFEF5350,
                0xFF8AE234,
                0xFFFCE94F,
                0xFF729FCF,
                0xFFC397D8,
                0xFF34E2E2,
                0xFFD3D7CF,
                0xFF888A85,
                0xFFFF7B7B,
                0xFFB5F27A,
                0xFFFFF59D,
                0xFF9EC3F0,
                0xFFE0B8F0,
                0xFF8AF0F0,
                0xFFEEEEEC,
            )
                .map(::Color)

        private val LIGHT =
            listOf(
                0xFF2E3436,
                0xFFB71C1C,
                0xFF2E7D32,
                0xFF8D6E00,
                0xFF1E5AA8,
                0xFF7B3F9E,
                0xFF00796B,
                0xFF5F6368,
                0xFF555753,
                0xFFC62828,
                0xFF1B5E20,
                0xFF795548,
                0xFF0D47A1,
                0xFF6A1B9A,
                0xFF00695C,
                0xFF202124,
            )
                .map(::Color)

        fun from(scheme: ColorScheme): TerminalPalette = TerminalPalette(
            ansi = if (scheme.surface.luminance() < LUMINANCE_DARK) DARK else LIGHT,
            foreground = scheme.onSurface,
            background = scheme.surface,
            notice = scheme.onSurfaceVariant,
        )
    }
}

internal fun CellStyle.toSpanStyle(palette: TerminalPalette): SpanStyle {
    var fg = palette.resolve(fg)
    var bg = palette.resolve(bg)
    // Bold brightens the eight base colours, as xterm's boldColors does.
    if (bold && this.fg in 0 until ANSI_BRIGHT_OFFSET) fg = palette.ansi[this.fg + ANSI_BRIGHT_OFFSET]
    if (inverse) {
        val swappedFg = bg ?: palette.background
        bg = fg ?: palette.foreground
        fg = swappedFg
    }
    if (dim) fg = (fg ?: palette.foreground).copy(alpha = DIM_ALPHA)
    return SpanStyle(
        color = fg ?: Color.Unspecified,
        background = bg ?: Color.Unspecified,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )
}

/** One output line with its style runs applied. */
internal fun TerminalLine.toAnnotatedString(palette: TerminalPalette): AnnotatedString = buildAnnotatedString {
    if (isNotice) {
        withStyle(SpanStyle(color = palette.notice, fontStyle = FontStyle.Italic)) { append(text) }
        return@buildAnnotatedString
    }
    append(text)
    runs.forEach { addStyle(it.style.toSpanStyle(palette), it.start, it.end) }
}

/**
 * The line being written, with what is still in flight drawn at the cursor: [predicted] (sent, awaiting echo)
 * underlined in the text colour, [unsent] (still in the keystroke debounce) underlined and faded. Text already right of
 * the cursor follows, as the remote line would show it once the echo lands.
 */
internal fun TerminalLine.withCursor(
    cursorColumn: Int,
    predicted: String,
    unsent: String,
    showCursor: Boolean,
    palette: TerminalPalette,
): AnnotatedString = buildAnnotatedString {
    val split = cursorColumn.coerceIn(0, text.length)
    val full = toAnnotatedString(palette)
    append(full.subSequence(0, split))
    if (cursorColumn > text.length) append(" ".repeat(cursorColumn - text.length))
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(predicted) }
    withStyle(
        SpanStyle(color = palette.foreground.copy(alpha = UNSENT_ALPHA), textDecoration = TextDecoration.Underline),
    ) {
        append(unsent)
    }
    if (showCursor) append(CURSOR_GLYPH)
    val after = split + predicted.length + unsent.length
    if (after < text.length) append(full.subSequence(after, text.length))
}
