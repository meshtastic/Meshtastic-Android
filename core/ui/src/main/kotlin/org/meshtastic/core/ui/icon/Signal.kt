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
import androidx.compose.material.icons.filled.CrueltyFree
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SsidChart
import androidx.compose.material.icons.filled.WifiChannel
import androidx.compose.material.icons.rounded.Route
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val MeshtasticIcons.Hops: ImageVector
    get() = Icons.Default.CrueltyFree
val MeshtasticIcons.Route: ImageVector
    get() = Icons.Rounded.Route
val MeshtasticIcons.Channel: ImageVector
    get() = Icons.Default.WifiChannel
val MeshtasticIcons.ChannelUtilization: ImageVector
    get() = Icons.Default.SignalCellularAlt
val MeshtasticIcons.AirUtilization: ImageVector
    get() = Icons.Default.SsidChart

val MeshtasticIcons.SignalCellular0Bar: ImageVector
    get() {
        if (signalCellular0Bar != null) {
            return signalCellular0Bar!!
        }
        signalCellular0Bar =
            ImageVector.Builder(
                name = "SignalCellular0Bar",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color(0xFFE3E3E3))) {
                        moveTo(177f, 880f)
                        quadToRelative(-27f, 0f, -37.5f, -24.5f)
                        reflectiveQuadTo(148f, 812f)
                        lineToRelative(664f, -664f)
                        quadToRelative(19f, -19f, 43.5f, -8.5f)
                        reflectiveQuadTo(880f, 177f)
                        verticalLineToRelative(643f)
                        quadToRelative(0f, 25f, -17.5f, 42.5f)
                        reflectiveQuadTo(820f, 880f)
                        lineTo(177f, 880f)
                        close()
                        moveTo(273f, 800f)
                        horizontalLineToRelative(527f)
                        verticalLineToRelative(-526f)
                        lineTo(273f, 800f)
                        close()
                    }
                }
                .build()

        return signalCellular0Bar!!
    }

private var signalCellular0Bar: ImageVector? = null

val MeshtasticIcons.SignalCellular1Bar: ImageVector
    get() {
        if (signalCellular1Bar != null) {
            return signalCellular1Bar!!
        }
        signalCellular1Bar =
            ImageVector.Builder(
                name = "SignalCellular1Bar",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color(0xFFE3E3E3))) {
                        moveTo(177f, 880f)
                        quadToRelative(-18f, 0f, -29.5f, -12f)
                        reflectiveQuadTo(136f, 840f)
                        quadToRelative(0f, -8f, 3f, -15f)
                        reflectiveQuadToRelative(9f, -13f)
                        lineToRelative(664f, -664f)
                        quadToRelative(6f, -6f, 13f, -9f)
                        reflectiveQuadToRelative(15f, -3f)
                        quadToRelative(16f, 0f, 28f, 11.5f)
                        reflectiveQuadToRelative(12f, 29.5f)
                        verticalLineToRelative(643f)
                        quadToRelative(0f, 25f, -17.5f, 42.5f)
                        reflectiveQuadTo(820f, 880f)
                        lineTo(177f, 880f)
                        close()
                        moveTo(400f, 800f)
                        horizontalLineToRelative(400f)
                        verticalLineToRelative(-526f)
                        lineTo(400f, 674f)
                        verticalLineToRelative(126f)
                        close()
                    }
                }
                .build()

        return signalCellular1Bar!!
    }

private var signalCellular1Bar: ImageVector? = null

val MeshtasticIcons.SignalCellular2Bar: ImageVector
    get() {
        if (signalCellular2Bar != null) {
            return signalCellular2Bar!!
        }
        signalCellular2Bar =
            ImageVector.Builder(
                name = "SignalCellular2Bar",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color(0xFFE3E3E3))) {
                        moveTo(177f, 880f)
                        quadToRelative(-18f, 0f, -29.5f, -12f)
                        reflectiveQuadTo(136f, 840f)
                        quadToRelative(0f, -8f, 3f, -15f)
                        reflectiveQuadToRelative(9f, -13f)
                        lineToRelative(664f, -664f)
                        quadToRelative(6f, -6f, 13f, -9f)
                        reflectiveQuadToRelative(15f, -3f)
                        quadToRelative(16f, 0f, 28f, 11.5f)
                        reflectiveQuadToRelative(12f, 29.5f)
                        verticalLineToRelative(643f)
                        quadToRelative(0f, 25f, -17.5f, 42.5f)
                        reflectiveQuadTo(820f, 880f)
                        lineTo(177f, 880f)
                        close()
                        moveTo(520f, 800f)
                        horizontalLineToRelative(280f)
                        verticalLineToRelative(-526f)
                        lineTo(520f, 554f)
                        verticalLineToRelative(246f)
                        close()
                    }
                }
                .build()

        return signalCellular2Bar!!
    }

private var signalCellular2Bar: ImageVector? = null

val MeshtasticIcons.SignalCellular3Bar: ImageVector
    get() {
        if (signalCellular3Bar != null) {
            return signalCellular3Bar!!
        }
        signalCellular3Bar =
            ImageVector.Builder(
                name = "SignalCellular3Bar",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color(0xFFE3E3E3))) {
                        moveTo(177f, 880f)
                        quadToRelative(-18f, 0f, -29.5f, -12f)
                        reflectiveQuadTo(136f, 840f)
                        quadToRelative(0f, -8f, 3f, -15f)
                        reflectiveQuadToRelative(9f, -13f)
                        lineToRelative(664f, -664f)
                        quadToRelative(6f, -6f, 13f, -9f)
                        reflectiveQuadToRelative(15f, -3f)
                        quadToRelative(16f, 0f, 28f, 11.5f)
                        reflectiveQuadToRelative(12f, 29.5f)
                        verticalLineToRelative(643f)
                        quadToRelative(0f, 25f, -17.5f, 42.5f)
                        reflectiveQuadTo(820f, 880f)
                        lineTo(177f, 880f)
                        close()
                        moveTo(600f, 800f)
                        horizontalLineToRelative(200f)
                        verticalLineToRelative(-526f)
                        lineTo(600f, 474f)
                        verticalLineToRelative(326f)
                        close()
                    }
                }
                .build()

        return signalCellular3Bar!!
    }

private var signalCellular3Bar: ImageVector? = null

val MeshtasticIcons.SignalCellular4Bar: ImageVector
    get() {
        if (signalCellular4Bar != null) {
            return signalCellular4Bar!!
        }
        signalCellular4Bar =
            ImageVector.Builder(
                name = "SignalCellular4Bar",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color(0xFFE3E3E3))) {
                        moveTo(177f, 880f)
                        quadToRelative(-18f, 0f, -29.5f, -12f)
                        reflectiveQuadTo(136f, 840f)
                        quadToRelative(0f, -8f, 3f, -15f)
                        reflectiveQuadToRelative(9f, -13f)
                        lineToRelative(664f, -664f)
                        quadToRelative(6f, -6f, 13f, -9f)
                        reflectiveQuadToRelative(15f, -3f)
                        quadToRelative(16f, 0f, 28f, 11.5f)
                        reflectiveQuadToRelative(12f, 29.5f)
                        verticalLineToRelative(643f)
                        quadToRelative(0f, 25f, -17.5f, 42.5f)
                        reflectiveQuadTo(820f, 880f)
                        lineTo(177f, 880f)
                        close()
                    }
                }
                .build()

        return signalCellular4Bar!!
    }

private var signalCellular4Bar: ImageVector? = null
