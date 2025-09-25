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
 *   [map](https://fonts.google.com/icons?selected=Material+Symbols+Rounded:map:FILL@0;wght@400;GRAD@0;opsz@24&icon.query=map&icon.size=24&icon.color=%23e3e3e3&icon.platform=android&icon.style=Rounded)
 */
val MeshtasticIcons.Map: ImageVector
    get() {
        if (map != null) {
            return map!!
        }
        map =
            ImageVector.Builder(
                name = "Outlined.Map",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveToRelative(574f, 831f)
                        lineToRelative(-214f, -75f)
                        lineToRelative(-186f, 72f)
                        quadToRelative(-10f, 4f, -19.5f, 2.5f)
                        reflectiveQuadTo(137f, 824f)
                        quadToRelative(-8f, -5f, -12.5f, -13.5f)
                        reflectiveQuadTo(120f, 791f)
                        verticalLineToRelative(-561f)
                        quadToRelative(0f, -13f, 7.5f, -23f)
                        reflectiveQuadToRelative(20.5f, -15f)
                        lineToRelative(186f, -63f)
                        quadToRelative(6f, -2f, 12.5f, -3f)
                        reflectiveQuadToRelative(13.5f, -1f)
                        quadToRelative(7f, 0f, 13.5f, 1f)
                        reflectiveQuadToRelative(12.5f, 3f)
                        lineToRelative(214f, 75f)
                        lineToRelative(186f, -72f)
                        quadToRelative(10f, -4f, 19.5f, -2.5f)
                        reflectiveQuadTo(823f, 136f)
                        quadToRelative(8f, 5f, 12.5f, 13.5f)
                        reflectiveQuadTo(840f, 169f)
                        verticalLineToRelative(561f)
                        quadToRelative(0f, 13f, -7.5f, 23f)
                        reflectiveQuadTo(812f, 768f)
                        lineToRelative(-186f, 63f)
                        quadToRelative(-6f, 2f, -12.5f, 3f)
                        reflectiveQuadToRelative(-13.5f, 1f)
                        quadToRelative(-7f, 0f, -13.5f, -1f)
                        reflectiveQuadToRelative(-12.5f, -3f)
                        close()
                        moveTo(560f, 742f)
                        verticalLineToRelative(-468f)
                        lineToRelative(-160f, -56f)
                        verticalLineToRelative(468f)
                        lineToRelative(160f, 56f)
                        close()
                        moveTo(640f, 742f)
                        lineTo(760f, 702f)
                        verticalLineToRelative(-474f)
                        lineToRelative(-120f, 46f)
                        verticalLineToRelative(468f)
                        close()
                        moveTo(200f, 732f)
                        lineTo(320f, 686f)
                        verticalLineToRelative(-468f)
                        lineToRelative(-120f, 40f)
                        verticalLineToRelative(474f)
                        close()
                        moveTo(640f, 274f)
                        verticalLineToRelative(468f)
                        verticalLineToRelative(-468f)
                        close()
                        moveTo(320f, 218f)
                        verticalLineToRelative(468f)
                        verticalLineToRelative(-468f)
                        close()
                    }
                }
                .build()

        return map!!
    }

private var map: ImageVector? = null
