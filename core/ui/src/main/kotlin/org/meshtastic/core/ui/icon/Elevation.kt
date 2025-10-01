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

package org.meshtastic.core.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * This is from Material Symbols.
 *
 * @see
 *   [elevation](https://fonts.google.com/icons?selected=Material+Symbols+Rounded:elevation:FILL@0;wght@400;GRAD@0;opsz@24&icon.query=elevation&icon.size=24&icon.color=%23e3e3e3&icon.platform=android&icon.style=Rounded)
 */
val MeshtasticIcons.Elevation: ImageVector
    get() {
        if (elevation != null) {
            return elevation!!
        }
        elevation =
            ImageVector.Builder(
                name = "Rounded.Elevation",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color(0xFFE3E3E3))) {
                        moveTo(760f, 840f)
                        lineTo(160f, 840f)
                        quadToRelative(-25f, 0f, -35.5f, -21.5f)
                        reflectiveQuadTo(128f, 777f)
                        lineToRelative(188f, -264f)
                        quadToRelative(11f, -16f, 28f, -24.5f)
                        reflectiveQuadToRelative(37f, -8.5f)
                        horizontalLineToRelative(161f)
                        lineToRelative(228f, -266f)
                        quadToRelative(18f, -21f, 44f, -11.5f)
                        reflectiveQuadToRelative(26f, 37.5f)
                        verticalLineToRelative(520f)
                        quadToRelative(0f, 33f, -23.5f, 56.5f)
                        reflectiveQuadTo(760f, 840f)
                        close()
                        moveTo(300f, 400f)
                        lineTo(176f, 575f)
                        quadToRelative(-10f, 14f, -26f, 16.5f)
                        reflectiveQuadToRelative(-30f, -7.5f)
                        quadToRelative(-14f, -10f, -16.5f, -26f)
                        reflectiveQuadToRelative(7.5f, -30f)
                        lineToRelative(125f, -174f)
                        quadToRelative(11f, -16f, 28f, -25f)
                        reflectiveQuadToRelative(37f, -9f)
                        horizontalLineToRelative(161f)
                        lineToRelative(162f, -189f)
                        quadToRelative(11f, -13f, 27f, -14f)
                        reflectiveQuadToRelative(29f, 10f)
                        quadToRelative(13f, 11f, 14f, 27f)
                        reflectiveQuadToRelative(-10f, 29f)
                        lineTo(522f, 372f)
                        quadToRelative(-11f, 14f, -27f, 21f)
                        reflectiveQuadToRelative(-33f, 7f)
                        lineTo(300f, 400f)
                        close()
                        moveTo(238f, 760f)
                        horizontalLineToRelative(522f)
                        verticalLineToRelative(-412f)
                        lineTo(602f, 532f)
                        quadToRelative(-11f, 14f, -27f, 21f)
                        reflectiveQuadToRelative(-33f, 7f)
                        lineTo(380f, 560f)
                        lineTo(238f, 760f)
                        close()
                        moveTo(760f, 760f)
                        close()
                    }
                }
                .build()

        return elevation!!
    }

private var elevation: ImageVector? = null
