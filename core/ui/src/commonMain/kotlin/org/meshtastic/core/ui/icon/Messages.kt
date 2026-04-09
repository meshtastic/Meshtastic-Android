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
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * This is from Material Symbols.
 *
 * @see
 *   [forum](https://fonts.google.com/icons?selected=Material+Symbols+Rounded:forum:FILL@0;wght@400;GRAD@0;opsz@24&icon.query=forum&icon.size=24&icon.color=%23e3e3e3&icon.platform=android&icon.style=Rounded)
 */
val MeshtasticIcons.Conversations: ImageVector
    get() {
        if (conversations != null) {
            return conversations!!
        }
        conversations =
            ImageVector.Builder(
                name = "Outlined.Conversations",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 960f,
                viewportHeight = 960f,
            )
                .apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(840f, 824f)
                        quadToRelative(-8f, 0f, -15f, -3f)
                        reflectiveQuadToRelative(-13f, -9f)
                        lineToRelative(-92f, -92f)
                        lineTo(320f, 720f)
                        quadToRelative(-33f, 0f, -56.5f, -23.5f)
                        reflectiveQuadTo(240f, 640f)
                        verticalLineToRelative(-40f)
                        horizontalLineToRelative(440f)
                        quadToRelative(33f, 0f, 56.5f, -23.5f)
                        reflectiveQuadTo(760f, 520f)
                        verticalLineToRelative(-280f)
                        horizontalLineToRelative(40f)
                        quadToRelative(33f, 0f, 56.5f, 23.5f)
                        reflectiveQuadTo(880f, 320f)
                        verticalLineToRelative(463f)
                        quadToRelative(0f, 18f, -12f, 29.5f)
                        reflectiveQuadTo(840f, 824f)
                        close()
                        moveTo(160f, 487f)
                        lineToRelative(47f, -47f)
                        horizontalLineToRelative(393f)
                        verticalLineToRelative(-280f)
                        lineTo(160f, 160f)
                        verticalLineToRelative(327f)
                        close()
                        moveTo(120f, 624f)
                        quadToRelative(-16f, 0f, -28f, -11.5f)
                        reflectiveQuadTo(80f, 583f)
                        verticalLineToRelative(-423f)
                        quadToRelative(0f, -33f, 23.5f, -56.5f)
                        reflectiveQuadTo(160f, 80f)
                        horizontalLineToRelative(440f)
                        quadToRelative(33f, 0f, 56.5f, 23.5f)
                        reflectiveQuadTo(680f, 160f)
                        verticalLineToRelative(280f)
                        quadToRelative(0f, 33f, -23.5f, 56.5f)
                        reflectiveQuadTo(600f, 520f)
                        lineTo(240f, 520f)
                        lineToRelative(-92f, 92f)
                        quadToRelative(-6f, 6f, -13f, 9f)
                        reflectiveQuadToRelative(-15f, 3f)
                        close()
                        moveTo(160f, 440f)
                        verticalLineToRelative(-280f)
                        verticalLineToRelative(280f)
                        close()
                    }
                }
                .build()

        return conversations!!
    }

private var conversations: ImageVector? = null

// Messaging UI icons
val MeshtasticIcons.ChatBubbleOutline: ImageVector
    get() = Icons.Rounded.ChatBubbleOutline
val MeshtasticIcons.FormatQuote: ImageVector
    get() = Icons.Rounded.FormatQuote
val MeshtasticIcons.FilterList: ImageVector
    get() = Icons.Rounded.FilterList
val MeshtasticIcons.FilterListOff: ImageVector
    get() = Icons.Rounded.FilterListOff
val MeshtasticIcons.FastForward: ImageVector
    get() = Icons.Rounded.FastForward
val MeshtasticIcons.Message: ImageVector
    get() = Icons.AutoMirrored.Rounded.Message
val MeshtasticIcons.Visibility: ImageVector
    get() = Icons.Rounded.Visibility
val MeshtasticIcons.VisibilityOff: ImageVector
    get() = Icons.Rounded.VisibilityOff
val MeshtasticIcons.AddLink: ImageVector
    get() = Icons.Rounded.AddLink
val MeshtasticIcons.LinkIcon: ImageVector
    get() = Icons.Rounded.Link
