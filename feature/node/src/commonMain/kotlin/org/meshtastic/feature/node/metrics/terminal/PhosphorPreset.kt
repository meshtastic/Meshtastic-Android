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

import androidx.compose.ui.graphics.Color

/**
 * Phosphor colour presets for the RetroShell terminal.
 * - [GREEN] — P1 phosphor, VT100 / IBM 3101 style (#33FF33 on near-black)
 * - [AMBER] — P3 phosphor, IBM 3278 / Televideo 925 style (#FFB000 on near-black)
 * - [WHITE] — P4 phosphor, DEC VT220 / paper-white style (#E0E0E0 on near-black)
 */
@Suppress("MagicNumber")
enum class PhosphorPreset(
    /** The main text / glyph colour. */
    val fg: Color,
    /** The terminal background — deliberately slightly off-black to avoid pure-black crush. */
    val bg: Color,
    /** A dimmer variant used for scanline fill and unfocused UI elements. */
    val dim: Color,
    /** Glow halo colour — same hue as [fg] but very transparent. */
    val glow: Color,
) {
    GREEN(fg = Color(0xFF33FF33), bg = Color(0xFF0A0F0A), dim = Color(0xFF0D2B0D), glow = Color(0x4033FF33)),
    AMBER(fg = Color(0xFFFFB000), bg = Color(0xFF100C00), dim = Color(0xFF2E1E00), glow = Color(0x40FFB000)),
    WHITE(fg = Color(0xFFE0E0E0), bg = Color(0xFF0C0C0C), dim = Color(0xFF222222), glow = Color(0x40E0E0E0)),
}
