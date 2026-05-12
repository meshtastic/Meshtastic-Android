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
package org.meshtastic.wear.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
//  Theme State
// ─────────────────────────────────────────────────────────────
val LocalHighContrastMode = compositionLocalOf { false }

// ─────────────────────────────────────────────────────────────
//  Design tokens
// ─────────────────────────────────────────────────────────────
val COLOR_TEAL = Color(0xFF00E5CC)
val COLOR_TEAL_DIM = Color(0xFF00897B)
val COLOR_TEAL_DEEP = Color(0xFF00695C)
val COLOR_AMBER = Color(0xFFFFB74D)
val COLOR_NEON_GREEN = Color(0xFF39FF14)
val COLOR_OFFLINE_GRAY = Color(0xFF4A4A4A)

// Theme-aware colors
val COLOR_BG_DEEP @Composable get() = if (LocalHighContrastMode.current) Color(0xFF0D0D0D) else Color.Black
val COLOR_SURFACE1 @Composable get() = if (LocalHighContrastMode.current) Color(0xFF1A1A1A) else Color.Black
val COLOR_SURFACE2 @Composable get() = if (LocalHighContrastMode.current) Color(0xFF242424) else Color.Black
val COLOR_TEXT_PRIMARY @Composable get() = if (LocalHighContrastMode.current) Color(0xFFEEEEEE) else Color.White
val COLOR_TEXT_SECONDARY @Composable get() = if (LocalHighContrastMode.current) Color(0xFF888888) else Color.White

const val COLOR_NODES_BG = 0xFF004D40
const val BATTERY_HIGH_THRESHOLD = 60
const val BATTERY_LOW_THRESHOLD = 25
val COLOR_ERROR_RED = Color(0xFFEF5350)
const val PULSE_DURATION = 900
