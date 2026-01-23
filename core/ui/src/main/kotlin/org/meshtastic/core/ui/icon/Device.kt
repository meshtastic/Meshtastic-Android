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
package org.meshtastic.core.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val MeshtasticIcons.HardwareModel: ImageVector
    get() = Icons.Default.Router
val MeshtasticIcons.Role: ImageVector
    get() = Icons.Default.Work
val MeshtasticIcons.NodeId: ImageVector
    get() = Icons.Default.Fingerprint

/**
 * This is from Material Symbols.
 *
 * @see
 *   [router](https://fonts.google.com/icons?selected=Material+Symbols+Rounded:router:FILL@0;wght@400;GRAD@0;opsz@24&icon.query=router&icon.size=24&icon.color=%23e3e3e3&icon.platform=android&icon.style=Rounded)
 */
val MeshtasticIcons.Device: ImageVector
    get() {
        if (device != null) {
            return device!!
        }
        device =
            ImageVector.Builder(
                name = "Outlined.Device",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color(0xFFE3E3E3))) {
                        moveTo(200f, 840f)
                        quadToRelative(-33f, 0f, -56.5f, -23.5f)
                        reflectiveQuadTo(120f, 760f)
                        verticalLineToRelative(-160f)
                        quadToRelative(0f, -33f, 23.5f, -56.5f)
                        reflectiveQuadTo(200f, 520f)
                        horizontalLineToRelative(400f)
                        verticalLineToRelative(-120f)
                        quadToRelative(0f, -17f, 11.5f, -28.5f)
                        reflectiveQuadTo(640f, 360f)
                        quadToRelative(17f, 0f, 28.5f, 11.5f)
                        reflectiveQuadTo(680f, 400f)
                        verticalLineToRelative(120f)
                        horizontalLineToRelative(80f)
                        quadToRelative(33f, 0f, 56.5f, 23.5f)
                        reflectiveQuadTo(840f, 600f)
                        verticalLineToRelative(160f)
                        quadToRelative(0f, 33f, -23.5f, 56.5f)
                        reflectiveQuadTo(760f, 840f)
                        lineTo(200f, 840f)
                        close()
                        moveTo(200f, 760f)
                        horizontalLineToRelative(560f)
                        verticalLineToRelative(-160f)
                        lineTo(200f, 600f)
                        verticalLineToRelative(160f)
                        close()
                        moveTo(280f, 720f)
                        quadToRelative(17f, 0f, 28.5f, -11.5f)
                        reflectiveQuadTo(320f, 680f)
                        quadToRelative(0f, -17f, -11.5f, -28.5f)
                        reflectiveQuadTo(280f, 640f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(240f, 680f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(280f, 720f)
                        close()
                        moveTo(420f, 720f)
                        quadToRelative(17f, 0f, 28.5f, -11.5f)
                        reflectiveQuadTo(460f, 680f)
                        quadToRelative(0f, -17f, -11.5f, -28.5f)
                        reflectiveQuadTo(420f, 640f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(380f, 680f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(420f, 720f)
                        close()
                        moveTo(560f, 720f)
                        quadToRelative(17f, 0f, 28.5f, -11.5f)
                        reflectiveQuadTo(600f, 680f)
                        quadToRelative(0f, -17f, -11.5f, -28.5f)
                        reflectiveQuadTo(560f, 640f)
                        quadToRelative(-17f, 0f, -28.5f, 11.5f)
                        reflectiveQuadTo(520f, 680f)
                        quadToRelative(0f, 17f, 11.5f, 28.5f)
                        reflectiveQuadTo(560f, 720f)
                        close()
                        moveTo(640f, 300f)
                        quadToRelative(-11f, 0f, -20f, 2f)
                        reflectiveQuadToRelative(-18f, 6f)
                        quadToRelative(-16f, 7f, -32.5f, 6f)
                        reflectiveQuadTo(541f, 301f)
                        quadToRelative(-12f, -12f, -11.5f, -29f)
                        reflectiveQuadToRelative(14.5f, -25f)
                        quadToRelative(21f, -13f, 45.5f, -20f)
                        reflectiveQuadToRelative(50.5f, -7f)
                        quadToRelative(27f, 0f, 51f, 7f)
                        reflectiveQuadToRelative(45f, 20f)
                        quadToRelative(14f, 8f, 14.5f, 25f)
                        reflectiveQuadTo(739f, 301f)
                        quadToRelative(-12f, 12f, -29f, 13f)
                        reflectiveQuadToRelative(-33f, -6f)
                        quadToRelative(-8f, -4f, -17.5f, -6f)
                        reflectiveQuadToRelative(-19.5f, -2f)
                        close()
                        moveTo(640f, 160f)
                        quadToRelative(-39f, 0f, -74.5f, 11.5f)
                        reflectiveQuadTo(500f, 205f)
                        quadToRelative(-14f, 10f, -30.5f, 9f)
                        reflectiveQuadTo(442f, 202f)
                        quadToRelative(-12f, -12f, -12f, -28f)
                        reflectiveQuadToRelative(13f, -26f)
                        quadToRelative(41f, -32f, 91f, -50f)
                        reflectiveQuadToRelative(106f, -18f)
                        quadToRelative(56f, 0f, 106f, 18f)
                        reflectiveQuadToRelative(91f, 50f)
                        quadToRelative(13f, 10f, 13f, 26f)
                        reflectiveQuadToRelative(-12f, 28f)
                        quadToRelative(-11f, 11f, -27.5f, 12f)
                        reflectiveQuadToRelative(-30.5f, -9f)
                        quadToRelative(-30f, -22f, -65.5f, -33.5f)
                        reflectiveQuadTo(640f, 160f)
                        close()
                        moveTo(200f, 760f)
                        verticalLineToRelative(-160f)
                        verticalLineToRelative(160f)
                        close()
                    }
                }
                .build()

        return device!!
    }

private var device: ImageVector? = null
