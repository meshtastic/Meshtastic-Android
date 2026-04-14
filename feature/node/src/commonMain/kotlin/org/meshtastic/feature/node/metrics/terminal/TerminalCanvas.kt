/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/** Extra vertical gap between lines to mimic CRT inter-scan spacing (px). */
@Suppress("MagicNumber")
private const val LINE_SPACING_PX = 2f

/** Terminal glyph size in SP. */
@Suppress("MagicNumber")
private const val TERMINAL_FONT_SIZE_SP = 13

/**
 * Draws [lines] of terminal output as a monospace character grid with a phosphor bloom effect.
 *
 * Each confirmed text row is rendered in two passes:
 * 1. A glow pass (two translucent offset copies) using [PhosphorPreset.glow] to produce the phosphor halo.
 * 2. A sharp pass with [PhosphorPreset.fg] for crisp readable glyphs on top.
 *
 * The last row additionally renders any [pendingInput] text as a dim suffix using [PhosphorPreset.dim]. Pending
 * characters are keystrokes that have been typed but not yet flushed to the mesh — they appear immediately for
 * responsiveness but at reduced brightness to signal their "in-flight" status. On flush [pendingInput] is `""` and the
 * dim suffix disappears instantly.
 *
 * Only Compose/KMP APIs are used here — no `android.*` imports.
 *
 * @param lines Ordered confirmed output lines (oldest first).
 * @param pendingInput Unflushed keystrokes to render as a dim suffix on the last line.
 * @param preset Active [PhosphorPreset].
 * @param flickerAlpha Animated alpha from [FlickerEffect].
 * @param showCursor Whether to render the blinking block cursor after [pendingInput].
 */
@Suppress("LongMethod", "MagicNumber")
@Composable
fun TerminalCanvas(
    lines: List<String>,
    pendingInput: String,
    preset: PhosphorPreset,
    flickerAlpha: Float,
    showCursor: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()

    val baseStyle =
        remember(preset) { TextStyle(fontFamily = FontFamily.Monospace, fontSize = TERMINAL_FONT_SIZE_SP.sp) }
    val fgStyle = remember(preset) { baseStyle.copy(color = preset.fg) }
    val glowStyle = remember(preset) { baseStyle.copy(color = preset.glow) }
    val dimStyle = remember(preset) { baseStyle.copy(color = preset.dim) }

    val charWidthPx = remember(preset) { measurer.measure("M", fgStyle).size.width.toFloat() }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = preset.bg)

        val fontSizePx = TERMINAL_FONT_SIZE_SP.sp.toPx()
        val lineHeightPx = fontSizePx + LINE_SPACING_PX

        val visibleLines = (size.height / lineHeightPx).toInt().coerceAtLeast(1)
        val displayLines = lines.takeLast(visibleLines)

        displayLines.forEachIndexed { index, line ->
            val y = index * lineHeightPx
            val isLastLine = index == displayLines.lastIndex

            // --- Glow pass ---
            drawText(
                textMeasurer = measurer,
                text = line,
                style = glowStyle.copy(color = preset.glow.copy(alpha = flickerAlpha * 0.6f)),
                topLeft = Offset(-1f, y - 1f),
            )
            drawText(
                textMeasurer = measurer,
                text = line,
                style = glowStyle.copy(color = preset.glow.copy(alpha = flickerAlpha * 0.4f)),
                topLeft = Offset(1f, y + 1f),
            )

            // --- Sharp foreground pass ---
            drawText(
                textMeasurer = measurer,
                text = line,
                style = fgStyle.copy(color = preset.fg.copy(alpha = flickerAlpha)),
                topLeft = Offset(0f, y),
            )

            // --- Pending input suffix (last line only) ---
            if (isLastLine && pendingInput.isNotEmpty()) {
                val confirmedWidthPx = measurer.measure(line, fgStyle).size.width.toFloat()
                drawText(
                    textMeasurer = measurer,
                    text = pendingInput,
                    style = dimStyle.copy(color = preset.dim.copy(alpha = flickerAlpha)),
                    topLeft = Offset(confirmedWidthPx, y),
                )
            }
        }

        // If there are no confirmed lines yet but there is pending input, draw it on row 0.
        if (displayLines.isEmpty() && pendingInput.isNotEmpty()) {
            drawText(
                textMeasurer = measurer,
                text = pendingInput,
                style = dimStyle.copy(color = preset.dim.copy(alpha = flickerAlpha)),
                topLeft = Offset.Zero,
            )
        }

        // --- Block cursor (positioned after pending input, or after the last confirmed line) ---
        if (showCursor) {
            val lastConfirmed = displayLines.lastOrNull() ?: ""
            val cursorRow = displayLines.lastIndex.coerceAtLeast(0)
            val confirmedWidth =
                if (lastConfirmed.isNotEmpty()) {
                    measurer.measure(lastConfirmed, fgStyle).size.width.toFloat()
                } else {
                    0f
                }
            val pendingWidth =
                if (pendingInput.isNotEmpty()) {
                    measurer.measure(pendingInput, dimStyle).size.width.toFloat()
                } else {
                    0f
                }
            val cursorX = confirmedWidth + pendingWidth
            val cursorY = cursorRow * lineHeightPx
            drawRect(
                color = preset.fg.copy(alpha = flickerAlpha),
                topLeft = Offset(cursorX, cursorY),
                size = Size(charWidthPx, lineHeightPx - LINE_SPACING_PX),
            )
        }
    }
}
