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
package org.meshtastic.core.ui.theme

import androidx.compose.ui.graphics.Color

// ─── KV Field Console Palette ───
// Primary (Brass): #C9B06B | Background: #0B0B0D | Surface: #16161A
// On Background (warm off-white): #E8E4DA | Secondary text (muted grey): #8A857A

// ─── Dark Scheme (forced — light scheme removed) ───
val primaryDark = Color(0xFFC9B06B) // Brass — buttons, highlights, active states
val onPrimaryDark = Color(0xFF0B0B0D) // Near-black on brass
val primaryContainerDark = Color(0xFF3D3018) // Dark brass container
val onPrimaryContainerDark = Color(0xFFF5E6C0) // Light brass tint on dark container
val secondaryDark = Color(0xFF8A857A) // Muted warm grey — labels, hints
val onSecondaryDark = Color(0xFF0B0B0D)
val secondaryContainerDark = Color(0xFF2A2A2E) // Slightly lifted surface
val onSecondaryContainerDark = Color(0xFFE8E4DA) // Warm off-white
val tertiaryDark = Color(0xFFA89050) // Darker brass accent
val onTertiaryDark = Color(0xFF0B0B0D)
val tertiaryContainerDark = Color(0xFF2A2010)
val onTertiaryContainerDark = Color(0xFFF5E6C0)
val errorDark = Color(0xFFCF6679)
val onErrorDark = Color(0xFF0B0B0D)
val errorContainerDark = Color(0xFF4D0019)
val onErrorContainerDark = Color(0xFFFFDAD9)
val backgroundDark = Color(0xFF0B0B0D) // Near-black app background
val onBackgroundDark = Color(0xFFE8E4DA) // Warm off-white primary text
val surfaceDark = Color(0xFF16161A) // Slightly lifted near-black — cards, sheets
val onSurfaceDark = Color(0xFFE8E4DA)
val surfaceVariantDark = Color(0xFF2A2A2E)
val onSurfaceVariantDark = Color(0xFF8A857A) // Muted warm grey
val outlineDark = Color(0xFF8A857A)
val outlineVariantDark = Color(0xFF3A3A3F)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFE8E4DA)
val inverseOnSurfaceDark = Color(0xFF0B0B0D)
val inversePrimaryDark = Color(0xFF5A4A1A) // Dark brass for inverse contexts
val surfaceDimDark = Color(0xFF0B0B0D)
val surfaceBrightDark = Color(0xFF2A2A2E)
val surfaceContainerLowestDark = Color(0xFF080809)
val surfaceContainerLowDark = Color(0xFF0F0F12)
val surfaceContainerDark = Color(0xFF16161A)
val surfaceContainerHighDark = Color(0xFF1F1F24)
val surfaceContainerHighestDark = Color(0xFF2A2A2E)
