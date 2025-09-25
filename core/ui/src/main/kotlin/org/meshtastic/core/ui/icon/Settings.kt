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
 *   [settings](https://fonts.google.com/icons?selected=Material+Symbols+Rounded:settings:FILL@0;wght@400;GRAD@0;opsz@24&icon.style=Rounded&icon.query=settings&icon.set=Material+Symbols&icon.size=24&icon.color=%23e3e3e3&icon.platform=android)
 */
val MeshtasticIcons.Settings: ImageVector
    get() {
        if (settings != null) {
            return settings!!
        }
        settings =
            ImageVector.Builder(
                name = "Settings",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color(0xFFE3E3E3))) {
                        moveTo(433f, 880f)
                        quadToRelative(-27f, 0f, -46.5f, -18f)
                        reflectiveQuadTo(363f, 818f)
                        lineToRelative(-9f, -66f)
                        quadToRelative(-13f, -5f, -24.5f, -12f)
                        reflectiveQuadTo(307f, 725f)
                        lineToRelative(-62f, 26f)
                        quadToRelative(-25f, 11f, -50f, 2f)
                        reflectiveQuadToRelative(-39f, -32f)
                        lineToRelative(-47f, -82f)
                        quadToRelative(-14f, -23f, -8f, -49f)
                        reflectiveQuadToRelative(27f, -43f)
                        lineToRelative(53f, -40f)
                        quadToRelative(-1f, -7f, -1f, -13.5f)
                        verticalLineToRelative(-27f)
                        quadToRelative(0f, -6.5f, 1f, -13.5f)
                        lineToRelative(-53f, -40f)
                        quadToRelative(-21f, -17f, -27f, -43f)
                        reflectiveQuadToRelative(8f, -49f)
                        lineToRelative(47f, -82f)
                        quadToRelative(14f, -23f, 39f, -32f)
                        reflectiveQuadToRelative(50f, 2f)
                        lineToRelative(62f, 26f)
                        quadToRelative(11f, -8f, 23f, -15f)
                        reflectiveQuadToRelative(24f, -12f)
                        lineToRelative(9f, -66f)
                        quadToRelative(4f, -26f, 23.5f, -44f)
                        reflectiveQuadToRelative(46.5f, -18f)
                        horizontalLineToRelative(94f)
                        quadToRelative(27f, 0f, 46.5f, 18f)
                        reflectiveQuadToRelative(23.5f, 44f)
                        lineToRelative(9f, 66f)
                        quadToRelative(13f, 5f, 24.5f, 12f)
                        reflectiveQuadToRelative(22.5f, 15f)
                        lineToRelative(62f, -26f)
                        quadToRelative(25f, -11f, 50f, -2f)
                        reflectiveQuadToRelative(39f, 32f)
                        lineToRelative(47f, 82f)
                        quadToRelative(14f, 23f, 8f, 49f)
                        reflectiveQuadToRelative(-27f, 43f)
                        lineToRelative(-53f, 40f)
                        quadToRelative(1f, 7f, 1f, 13.5f)
                        verticalLineToRelative(27f)
                        quadToRelative(0f, 6.5f, -2f, 13.5f)
                        lineToRelative(53f, 40f)
                        quadToRelative(21f, 17f, 27f, 43f)
                        reflectiveQuadToRelative(-8f, 49f)
                        lineToRelative(-48f, 82f)
                        quadToRelative(-14f, 23f, -39f, 32f)
                        reflectiveQuadToRelative(-50f, -2f)
                        lineToRelative(-60f, -26f)
                        quadToRelative(-11f, 8f, -23f, 15f)
                        reflectiveQuadToRelative(-24f, 12f)
                        lineToRelative(-9f, 66f)
                        quadToRelative(-4f, 26f, -23.5f, 44f)
                        reflectiveQuadTo(527f, 880f)
                        horizontalLineToRelative(-94f)
                        close()
                        moveTo(440f, 800f)
                        horizontalLineToRelative(79f)
                        lineToRelative(14f, -106f)
                        quadToRelative(31f, -8f, 57.5f, -23.5f)
                        reflectiveQuadTo(639f, 633f)
                        lineToRelative(99f, 41f)
                        lineToRelative(39f, -68f)
                        lineToRelative(-86f, -65f)
                        quadToRelative(5f, -14f, 7f, -29.5f)
                        reflectiveQuadToRelative(2f, -31.5f)
                        quadToRelative(0f, -16f, -2f, -31.5f)
                        reflectiveQuadToRelative(-7f, -29.5f)
                        lineToRelative(86f, -65f)
                        lineToRelative(-39f, -68f)
                        lineToRelative(-99f, 42f)
                        quadToRelative(-22f, -23f, -48.5f, -38.5f)
                        reflectiveQuadTo(533f, 266f)
                        lineToRelative(-13f, -106f)
                        horizontalLineToRelative(-79f)
                        lineToRelative(-14f, 106f)
                        quadToRelative(-31f, 8f, -57.5f, 23.5f)
                        reflectiveQuadTo(321f, 327f)
                        lineToRelative(-99f, -41f)
                        lineToRelative(-39f, 68f)
                        lineToRelative(86f, 64f)
                        quadToRelative(-5f, 15f, -7f, 30f)
                        reflectiveQuadToRelative(-2f, 32f)
                        quadToRelative(0f, 16f, 2f, 31f)
                        reflectiveQuadToRelative(7f, 30f)
                        lineToRelative(-86f, 65f)
                        lineToRelative(39f, 68f)
                        lineToRelative(99f, -42f)
                        quadToRelative(22f, 23f, 48.5f, 38.5f)
                        reflectiveQuadTo(427f, 694f)
                        lineToRelative(13f, 106f)
                        close()
                        moveTo(482f, 620f)
                        quadToRelative(58f, 0f, 99f, -41f)
                        reflectiveQuadToRelative(41f, -99f)
                        quadToRelative(0f, -58f, -41f, -99f)
                        reflectiveQuadToRelative(-99f, -41f)
                        quadToRelative(-59f, 0f, -99.5f, 41f)
                        reflectiveQuadTo(342f, 480f)
                        quadToRelative(0f, 58f, 40.5f, 99f)
                        reflectiveQuadToRelative(99.5f, 41f)
                        close()
                        moveTo(480f, 480f)
                        close()
                    }
                }
                .build()

        return settings!!
    }

private var settings: ImageVector? = null
