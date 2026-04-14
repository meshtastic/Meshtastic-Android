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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/** Pitch between scanlines in pixels (one dark line every N px). */
@Suppress("MagicNumber")
private const val SCANLINE_PITCH_PX = 2f

/** Opacity of each dark scanline stripe. Higher = more pronounced CRT effect. */
@Suppress("MagicNumber")
private const val SCANLINE_ALPHA = 0.18f

/**
 * Draws horizontal semi-transparent dark stripes over its entire bounds to simulate the inter-scan dark bands of a CRT
 * phosphor screen.
 *
 * Render this as a transparent overlay on top of [TerminalCanvas]. The [flickerAlpha] modulates the stripe opacity so
 * the scanlines breathe in sync with the phosphor flicker.
 *
 * @param preset Active [PhosphorPreset] (used for scanline tint colour).
 * @param flickerAlpha Animated alpha from [FlickerEffect]; modulates stripe visibility.
 */
@Suppress("MagicNumber")
@Composable
fun ScanlinesOverlay(preset: PhosphorPreset, flickerAlpha: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val stripeColor = preset.bg.copy(alpha = SCANLINE_ALPHA * flickerAlpha)
        var y = 0f
        while (y < size.height) {
            drawLine(color = stripeColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
            y += SCANLINE_PITCH_PX
        }
    }
}
