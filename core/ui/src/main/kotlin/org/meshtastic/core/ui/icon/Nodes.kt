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
 *   [graph_3](https://fonts.google.com/icons?icon.query=graph+3&icon.size=24&icon.color=%23e3e3e3&icon.style=Rounded)
 */
val MeshtasticIcons.Nodes: ImageVector
    get() {
        if (nodes != null) {
            return nodes!!
        }
        nodes =
            ImageVector.Builder(
                name = "Outlined.Nodes",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(480f, 880f)
                        quadToRelative(-50f, 0f, -85f, -35f)
                        reflectiveQuadToRelative(-35f, -85f)
                        quadToRelative(0f, -5f, 0.5f, -11f)
                        reflectiveQuadToRelative(1.5f, -11f)
                        lineToRelative(-83f, -47f)
                        quadToRelative(-16f, 14f, -36f, 21.5f)
                        reflectiveQuadToRelative(-43f, 7.5f)
                        quadToRelative(-50f, 0f, -85f, -35f)
                        reflectiveQuadToRelative(-35f, -85f)
                        quadToRelative(0f, -50f, 35f, -85f)
                        reflectiveQuadToRelative(85f, -35f)
                        quadToRelative(24f, 0f, 45f, 9f)
                        reflectiveQuadToRelative(38f, 25f)
                        lineToRelative(119f, -60f)
                        quadToRelative(-3f, -23f, 2.5f, -45f)
                        reflectiveQuadToRelative(19.5f, -41f)
                        lineToRelative(-34f, -52f)
                        quadToRelative(-7f, 2f, -14.5f, 3f)
                        reflectiveQuadToRelative(-15.5f, 1f)
                        quadToRelative(-50f, 0f, -85f, -35f)
                        reflectiveQuadToRelative(-35f, -85f)
                        quadToRelative(0f, -50f, 35f, -85f)
                        reflectiveQuadToRelative(85f, -35f)
                        quadToRelative(50f, 0f, 85f, 35f)
                        reflectiveQuadToRelative(35f, 85f)
                        quadToRelative(0f, 20f, -6.5f, 38.5f)
                        reflectiveQuadTo(456f, 272f)
                        lineToRelative(35f, 52f)
                        quadToRelative(8f, -2f, 15f, -3f)
                        reflectiveQuadToRelative(15f, -1f)
                        quadToRelative(17f, 0f, 32f, 4f)
                        reflectiveQuadToRelative(29f, 12f)
                        lineToRelative(66f, -54f)
                        quadToRelative(-4f, -10f, -6f, -20.5f)
                        reflectiveQuadToRelative(-2f, -21.5f)
                        quadToRelative(0f, -50f, 35f, -85f)
                        reflectiveQuadToRelative(85f, -35f)
                        quadToRelative(50f, 0f, 85f, 35f)
                        reflectiveQuadToRelative(35f, 85f)
                        quadToRelative(0f, 50f, -35f, 85f)
                        reflectiveQuadToRelative(-85f, 35f)
                        quadToRelative(-17f, 0f, -32f, -4.5f)
                        reflectiveQuadTo(699f, 343f)
                        lineToRelative(-66f, 55f)
                        quadToRelative(4f, 10f, 6f, 20.5f)
                        reflectiveQuadToRelative(2f, 21.5f)
                        quadToRelative(0f, 50f, -35f, 85f)
                        reflectiveQuadToRelative(-85f, 35f)
                        quadToRelative(-24f, 0f, -45.5f, -9f)
                        reflectiveQuadTo(437f, 526f)
                        lineToRelative(-118f, 59f)
                        quadToRelative(2f, 9f, 1.5f, 18f)
                        reflectiveQuadToRelative(-2.5f, 18f)
                        lineToRelative(84f, 48f)
                        quadToRelative(16f, -14f, 35.5f, -21.5f)
                        reflectiveQuadTo(480f, 640f)
                        quadToRelative(50f, 0f, 85f, 35f)
                        reflectiveQuadToRelative(35f, 85f)
                        quadToRelative(0f, 50f, -35f, 85f)
                        reflectiveQuadToRelative(-85f, 35f)
                        close()
                        moveTo(200f, 640f)
                        quadToRelative(17f, 0f, 28.5f, -11.5f)
                        reflectiveQuadTo(240f, 600f)
                        quadToRelative(0f, -17f, -11.5f, -28.5f)
                        reflectiveQuadTo(200f, 560f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(160f, 600f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(200f, 640f)
                        close()
                        moveTo(360f, 240f)
                        quadToRelative(17f, 0f, 28.5f, -11.5f)
                        reflectiveQuadTo(400f, 200f)
                        quadToRelative(0f, -17f, -11.5f, -28.5f)
                        reflectiveQuadTo(360f, 160f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(320f, 200f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(360f, 240f)
                        close()
                        moveTo(480f, 800f)
                        quadToRelative(17f, 0f, 28.5f, -11.5f)
                        reflectiveQuadTo(520f, 760f)
                        quadToRelative(0f, -17f, -11.5f, -28.5f)
                        reflectiveQuadTo(480f, 720f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(440f, 760f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(480f, 800f)
                        close()
                        moveTo(520f, 480f)
                        quadToRelative(17f, 0f, 28.5f, -11.5f)
                        reflectiveQuadTo(560f, 440f)
                        quadToRelative(0f, -17f, -11.5f, -28.5f)
                        reflectiveQuadTo(520f, 400f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(480f, 440f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(520f, 480f)
                        close()
                        moveTo(760f, 280f)
                        quadToRelative(17f, 0f, 28.5f, -11.5f)
                        reflectiveQuadTo(800f, 240f)
                        quadToRelative(0f, -17f, -11.5f, -28.5f)
                        reflectiveQuadTo(760f, 200f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(720f, 240f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(760f, 280f)
                        close()
                    }
                }
                .build()

        return nodes!!
    }

private var nodes: ImageVector? = null
