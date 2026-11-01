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

// ─── Meshtastic Design Standards v1.3 ───
// Primary: Green 700 #2D8F52 | Secondary: Neutral 600 #555668
// Tertiary: Blue 700 #2855A8 | Neutral: #2C2D3C | Neutral Variant: #303245
// See: standards/meshtastic_design_standards_v1_3.md §8

// ─── Light Scheme (§8.2) ───
val primaryLight = Color(0xFFA6B889) // Green 700
val onPrimaryLight = Color(0xFF182016)
val primaryContainerLight = Color(0xFFD4DEC5) // Green 300
val onPrimaryContainerLight = Color(0xFF25301F) // Green 950
val secondaryLight = Color(0xFF59634F) // Neutral 600
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFDCE2D3) // Neutral 200
val onSecondaryContainerLight = Color(0xFF252D20) // Neutral 800
val tertiaryLight = Color(0xFF796B43) // Blue 700
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFEEE2B9) // Blue 50
val onTertiaryContainerLight = Color(0xFF302A17) // Blue 950
val errorLight = Color(0xFFBA1A1A) // Error 600 (WCAG-safe on white)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFF9DEDC) // Error 100
val onErrorContainerLight = Color(0xFF410E0B) // Error 900
val backgroundLight = Color(0xFFE7E9DE) // Neutral 50
val onBackgroundLight = Color(0xFF20251C) // Neutral 800
val surfaceLight = Color(0xFFE7E9DE) // Neutral 50
val onSurfaceLight = Color(0xFF20251C) // Neutral 800
val surfaceVariantLight = Color(0xFFD9DECF) // NV 200
val onSurfaceVariantLight = Color(0xFF454D3F) // NV 600
val outlineLight = Color(0xFF747C6D) // NV 500
val outlineVariantLight = Color(0xFFBEC5B4) // NV 300
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF30372B) // Neutral 700
val inverseOnSurfaceLight = Color(0xFFEEF1E8) // Neutral 100
val inversePrimaryLight = Color(0xFFC1D2A8) // Green 500
val surfaceDimLight = Color(0xFFD2D8C9) // Neutral 200
val surfaceBrightLight = Color(0xFFF7F8F2) // Neutral 50
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF1F3EA) // Neutral 50
val surfaceContainerLight = Color(0xFFEBEEE3) // Neutral 100
val surfaceContainerHighLight = Color(0xFFE2E6D9) // Interpolated 100↔200
val surfaceContainerHighestLight = Color(0xFFDCE2D3) // Neutral 200

// ─── Dark Scheme (§8.3) ───
val primaryDark = Color(0xFFB6C99A) // Green 500
val onPrimaryDark = Color(0xFF20271B) // Neutral 950
val primaryContainerDark = Color(0xFF3D4A32) // Green 700
val onPrimaryContainerDark = Color(0xFFD5E3BE) // Green 300
val secondaryDark = Color(0xFFC1C9B6) // Neutral 300
val onSecondaryDark = Color(0xFF283023) // Neutral 900
val secondaryContainerDark = Color(0xFF414B38) // Neutral 700
val onSecondaryContainerDark = Color(0xFFDCE4D2) // Neutral 200
val tertiaryDark = Color(0xFFD7C58B) // Blue 300
val onTertiaryDark = Color(0xFF393017) // Blue 950
val tertiaryContainerDark = Color(0xFF574B2B) // Blue 700
val onTertiaryContainerDark = Color(0xFFF2E4B8) // Blue 50
val errorDark = Color(0xFFFFB4AB) // Error 300
val onErrorDark = Color(0xFF690005) // Error 800
val errorContainerDark = Color(0xFF93000A) // Error 700
val onErrorContainerDark = Color(0xFFFDEAEA) // Error 100
val backgroundDark = Color(0xFF111713) // Neutral 900
val onBackgroundDark = Color(0xFFE4E9DC) // Neutral 100
val surfaceDark = Color(0xFF151C16) // Neutral 900
val onSurfaceDark = Color(0xFFE4E9DC) // Neutral 100
val surfaceVariantDark = Color(0xFF303A2E) // NV 700
val onSurfaceVariantDark = Color(0xFFC0C9B8) // NV 300
val outlineDark = Color(0xFF899482) // NV 500
val outlineVariantDark = Color(0xFF414C3F) // NV 700
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFE4E9DC) // Neutral 100
val inverseOnSurfaceDark = Color(0xFF252D20) // Neutral 800
val inversePrimaryDark = Color(0xFF526342) // Green 700
val surfaceDimDark = Color(0xFF0D120E) // Neutral 950
val surfaceBrightDark = Color(0xFF354032) // Neutral 700
val surfaceContainerLowestDark = Color(0xFF0B100C) // Neutral 950
val surfaceContainerLowDark = Color(0xFF151C16) // Neutral 900
val surfaceContainerDark = Color(0xFF1D261E) // Interpolated 900↔800
val surfaceContainerHighDark = Color(0xFF252F25) // Neutral 800
val surfaceContainerHighestDark = Color(0xFF303A2E) // Neutral 700
