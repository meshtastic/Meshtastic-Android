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
package org.meshtastic.core.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_code
import org.meshtastic.core.resources.ic_format_bold
import org.meshtastic.core.resources.ic_format_italic
import org.meshtastic.core.resources.ic_format_strikethrough
import org.meshtastic.core.resources.ic_link

val MeshtasticIcons.Bold: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_format_bold)
val MeshtasticIcons.Italic: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_format_italic)
val MeshtasticIcons.Strikethrough: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_format_strikethrough)
val MeshtasticIcons.Code: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_code)
val MeshtasticIcons.Link: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_link)
