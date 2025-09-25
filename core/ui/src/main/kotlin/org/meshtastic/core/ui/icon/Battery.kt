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
 *   [battery_android_0](https://fonts.google.com/icons?icon.query=battery+android+0&icon.size=24&icon.color=%23e3e3e3&icon.style=Rounded)
 */
val MeshtasticIcons.BatteryEmpty: ImageVector
    get() {
        if (batteryEmpty != null) {
            return batteryEmpty!!
        }
        batteryEmpty =
            ImageVector.Builder(
                name = "BatteryEmpty",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(160f, 720f)
                        quadToRelative(-50f, 0f, -85f, -35f)
                        reflectiveQuadToRelative(-35f, -85f)
                        verticalLineToRelative(-240f)
                        quadToRelative(0f, -50f, 35f, -85f)
                        reflectiveQuadToRelative(85f, -35f)
                        horizontalLineToRelative(540f)
                        quadToRelative(50f, 0f, 85f, 35f)
                        reflectiveQuadToRelative(35f, 85f)
                        verticalLineToRelative(240f)
                        quadToRelative(0f, 50f, -35f, 85f)
                        reflectiveQuadToRelative(-85f, 35f)
                        lineTo(160f, 720f)
                        close()
                        moveTo(160f, 640f)
                        horizontalLineToRelative(540f)
                        quadToRelative(17f, 0f, 28.5f, -11.5f)
                        reflectiveQuadTo(740f, 600f)
                        verticalLineToRelative(-240f)
                        quadToRelative(0f, -17f, -11.5f, -28.5f)
                        reflectiveQuadTo(700f, 320f)
                        lineTo(160f, 320f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(120f, 360f)
                        verticalLineToRelative(240f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(160f, 640f)
                        close()
                        moveTo(860f, 580f)
                        verticalLineToRelative(-200f)
                        horizontalLineToRelative(20f)
                        quadToRelative(17f, 0f, 28.5f, 11.5f)
                        reflectiveQuadTo(920f, 420f)
                        verticalLineToRelative(120f)
                        quadToRelative(0f, 17f, -11.5f, 28.5f)
                        reflectiveQuadTo(880f, 580f)
                        horizontalLineToRelative(-20f)
                        close()
                        moveTo(120f, 640f)
                        verticalLineToRelative(-320f)
                        verticalLineToRelative(320f)
                        close()
                    }
                }
                .build()

        return batteryEmpty!!
    }

private var batteryEmpty: ImageVector? = null

/**
 * This is from Material Symbols.
 *
 * @see
 *   [battery_android_question](https://fonts.google.com/icons?icon.query=battery+android+question&icon.size=24&icon.color=%23e3e3e3&icon.style=Rounded)
 */
val MeshtasticIcons.BatteryUnknown: ImageVector
    get() {
        if (batteryUnknown != null) {
            return batteryUnknown!!
        }
        batteryUnknown =
            ImageVector.Builder(
                name = "BatteryUnknown",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(120f, 640f)
                        verticalLineToRelative(-320f)
                        verticalLineToRelative(320f)
                        close()
                        moveTo(726f, 720f)
                        lineTo(160f, 720f)
                        quadToRelative(-50f, 0f, -85f, -35f)
                        reflectiveQuadToRelative(-35f, -85f)
                        verticalLineToRelative(-240f)
                        quadToRelative(0f, -50f, 35f, -85f)
                        reflectiveQuadToRelative(85f, -35f)
                        horizontalLineToRelative(521f)
                        quadToRelative(-20f, 16f, -35f, 36f)
                        reflectiveQuadToRelative(-25f, 44f)
                        lineTo(160f, 320f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(120f, 360f)
                        verticalLineToRelative(240f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(160f, 640f)
                        horizontalLineToRelative(520f)
                        quadToRelative(2f, 25f, 14.5f, 45.5f)
                        reflectiveQuadTo(726f, 720f)
                        close()
                        moveTo(800f, 660f)
                        quadToRelative(17f, 0f, 28.5f, -11.5f)
                        reflectiveQuadTo(840f, 620f)
                        quadToRelative(0f, -17f, -11.5f, -28.5f)
                        reflectiveQuadTo(800f, 580f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(760f, 620f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(800f, 660f)
                        close()
                        moveTo(772f, 538f)
                        horizontalLineToRelative(57f)
                        verticalLineToRelative(-21f)
                        quadToRelative(0f, -10f, 5f, -19f)
                        quadToRelative(6f, -13f, 15.5f, -22f)
                        reflectiveQuadToRelative(19.5f, -19f)
                        quadToRelative(17f, -17f, 28.5f, -37f)
                        reflectiveQuadToRelative(11.5f, -43f)
                        quadToRelative(0f, -42f, -32.5f, -69.5f)
                        reflectiveQuadTo(800f, 280f)
                        quadToRelative(-38f, 0f, -68f, 22f)
                        reflectiveQuadToRelative(-40f, 58f)
                        lineToRelative(51f, 21f)
                        quadToRelative(6f, -20f, 21.5f, -33f)
                        reflectiveQuadToRelative(35.5f, -13f)
                        quadToRelative(21f, 0f, 36.5f, 12f)
                        reflectiveQuadToRelative(15.5f, 32f)
                        quadToRelative(0f, 17f, -10f, 30.5f)
                        reflectiveQuadTo(820f, 434f)
                        quadToRelative(-11f, 11f, -22.5f, 21.5f)
                        reflectiveQuadTo(779f, 480f)
                        quadToRelative(-6f, 14f, -6.5f, 28.5f)
                        reflectiveQuadTo(772f, 538f)
                        close()
                    }
                }
                .build()

        return batteryUnknown!!
    }

private var batteryUnknown: ImageVector? = null
