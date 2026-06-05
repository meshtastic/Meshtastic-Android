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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Brand Colors (Design Standards v1.3) ───
val MeshtasticGreen = Color(0xFF67EA94) // Green 500 — Brand Accent
val MeshtasticAlt = Color(0xFF2C2D3C) // Neutral 800 — Brand Primary

// ─── Neutral Scale ───
object NeutralPalette {
    val N950 = Color(0xFF0F1017)
    val N900 = Color(0xFF1A1B26)
    val N800 = Color(0xFF2C2D3C)
    val N700 = Color(0xFF3D3E50)
    val N600 = Color(0xFF555668)
    val N500 = Color(0xFF6E7082)
    val N400 = Color(0xFF9496A6)
    val N300 = Color(0xFFB8BAC8)
    val N200 = Color(0xFFD5D6E0)
    val N100 = Color(0xFFECEDF3)
    val N50 = Color(0xFFF5F6FA)
}

// ─── Neutral Variant Scale (§7.3) ───
object NeutralVariantPalette {
    val NV900 = Color(0xFF1D1E2B)
    val NV800 = Color(0xFF303245)
    val NV700 = Color(0xFF444660)
    val NV600 = Color(0xFF5C5E78)
    val NV500 = Color(0xFF767892)
    val NV400 = Color(0xFF9698B0)
    val NV300 = Color(0xFFBDBFCF)
    val NV200 = Color(0xFFDADBE7)
    val NV100 = Color(0xFFEDEEF6)
    val NV50 = Color(0xFFF6F7FC)
}

// ─── Green Scale (§7.4) ───
object GreenPalette {
    val G950 = Color(0xFF002E13)
    val G900 = Color(0xFF003D1A)
    val G800 = Color(0xFF005C2E)
    val G700 = Color(0xFF2D8F52)
    val G600 = Color(0xFF3FB86D)
    val G500 = Color(0xFF67EA94)
    val G400 = Color(0xFF8FF0B2)
    val G300 = Color(0xFFB5F5CE)
    val G200 = Color(0xFFCCFADD)
    val G100 = Color(0xFFE5FCEE)
    val G50 = Color(0xFFF0FEF5)
}

// ─── Accent Blue Scale (§7.5) ───
object BluePalette {
    val B950 = Color(0xFF001849)
    val B900 = Color(0xFF002366)
    val B800 = Color(0xFF1A3F8C)
    val B700 = Color(0xFF2855A8)
    val B600 = Color(0xFF5C6BC0)
    val B500 = Color(0xFF7B8AD0)
    val B400 = Color(0xFF9BA8E0)
    val B300 = Color(0xFFB0BFF0)
    val B200 = Color(0xFFD0D8F5)
    val B100 = Color(0xFFE0E3F8)
    val B50 = Color(0xFFE8EAF6)
}

// ─── Error Scale (§7.6) ───
object ErrorPalette {
    val E900 = Color(0xFF410002)
    val E800 = Color(0xFF690005)
    val E700 = Color(0xFF93000A)
    val E600 = Color(0xFFBA1A1A)
    val E500 = Color(0xFFE05252)
    val E400 = Color(0xFFFF897D)
    val E300 = Color(0xFFFFB4AB)
    val E200 = Color(0xFFFFDAD6)
    val E100 = Color(0xFFFDEAEA)
}

// ─── Semantic Colors (§7.7) ───
object SemanticColors {
    val Accent = Color(0xFF2855A8) // Blue 700
    val AccentLight = Color(0xFFE0E3F8) // Blue 100
    val Info = Color(0xFF5C6BC0) // Blue 600
    val InfoLight = Color(0xFFE8EAF6) // Blue 50
    val Warning = Color(0xFFE8A33E)
    val WarningLight = Color(0xFFFFF3E0)
    val Error = Color(0xFFE05252) // Error 500 — non-text indicators only
    val ErrorLight = Color(0xFFFDEAEA) // Error 100
    val Success = Color(0xFF3FB86D) // Green 600
    val SuccessLight = Color(0xFFE5FCEE) // Green 100
}

val HyperlinkBlue = Color(0xFF5C6BC0) // Blue 600 (Info)
val AnnotationColor = Color(0xFF2855A8) // Blue 700 (Accent)

object TracerouteColors {
    // High-contrast pair that stays legible on light/dark tiles and for most color-blind users.
    // Use partial alpha so polylines don’t overpower markers/tiles.
    val OutgoingRoute = Color(0xCCE86A00) // orange @ ~80% opacity
    val ReturnRoute = Color(0xCC0081C7) // cyan @ ~80% opacity
}

object IAQColors {
    val IAQExcellent = Color(0xFF00E400)
    val IAQGood = Color(0xFF92D050)
    val IAQLightlyPolluted = Color(0xFFFFFF00)
    val IAQModeratelyPolluted = Color(0xFFFF7300)
    val IAQHeavilyPolluted = Color(0xFFFF0000)
    val IAQSeverelyPolluted = Color(0xFF99004C)
    val IAQExtremelyPolluted = Color(0xFF663300)
    val IAQDangerouslyPolluted = Color(0xFF663300)
}

object GraphColors {
    val InfantryBlue = Color(red = 75, green = 119, blue = 190)
    val LightGreen = Color(0xFF4BF0BE)
    val Purple = Color(0xFF9C27B0)
    val Pink = Color(red = 255, green = 102, blue = 204)
    val Orange = Color(0xFFFF8800)
    val Gold = Color(0xFFFFD700)
    val Cyan = Color(0xFF00BCD4)
    val Red = Color(0xFFE91E63)
    val Blue = Color(0xFF2196F3)
    val Green = Color(0xFF4CAF50)
    val Teal = Color(0xFF009688)
    val Amber = Color(0xFFFFC107)
    val Lime = Color(0xFFCDDC39)
    val Indigo = Color(0xFF3F51B5)
    val DeepOrange = Color(0xFFFF5722)
    val Magenta = Color(0xFFE040FB)
    val SkyBlue = Color(0xFF03A9F4)
    val Chartreuse = Color(0xFF76FF03)
    val Coral = Color(0xFFFF6E40)
}

object StatusColors {
    val ColorScheme.StatusGreen: Color
        @Composable
        get() =
            if (isSystemInDarkTheme()) {
                Color(0xFF3FB86D) // Green 600
            } else {
                Color(0xFF3FB86D) // Green 600 (Success)
            }

    val ColorScheme.StatusYellow: Color
        @Composable
        get() =
            if (isSystemInDarkTheme()) {
                Color(0xFFE8A33E) // Warning
            } else {
                Color(0xFFE8A33E) // Warning
            }

    val ColorScheme.StatusOrange: Color
        @Composable
        get() =
            if (isSystemInDarkTheme()) {
                Color(0xFFE07000)
            } else {
                Color(0xFFFF8800)
            }

    val ColorScheme.StatusRed: Color
        @Composable
        get() =
            if (isSystemInDarkTheme()) {
                Color(0xFFE05252) // Error
            } else {
                Color(0xFFE05252) // Error
            }

    val ColorScheme.StatusBlue: Color
        @Composable
        get() =
            if (isSystemInDarkTheme()) {
                Color(0xFF5C6BC0) // Info
            } else {
                Color(0xFF5C6BC0) // Info
            }
}

@Suppress("MagicNumber")
object DiscoveryMapColors {
    val DirectNode = Color(0xFF4CAF50)
    val MeshNode = Color(0xFF2196F3)
    val UserPosition = Color(0xFFFF9800)
    val DirectLine = Color(0x804CAF50)
}

object MessageItemColors {
    val Red = Color(0x4DFF0000)
}
