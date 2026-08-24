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
import org.meshtastic.core.resources.ic_arrow_back
import org.meshtastic.core.resources.ic_arrow_downward
import org.meshtastic.core.resources.ic_chevron_right
import org.meshtastic.core.resources.ic_expand_less
import org.meshtastic.core.resources.ic_expand_more
import org.meshtastic.core.resources.ic_keyboard_arrow_down
import org.meshtastic.core.resources.ic_keyboard_arrow_up

val MeshtasticIcons.ArrowBack: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_arrow_back)
val MeshtasticIcons.ChevronRight: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_chevron_right)
val MeshtasticIcons.KeyboardArrowDown: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_keyboard_arrow_down)
val MeshtasticIcons.KeyboardArrowUp: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_keyboard_arrow_up)
val MeshtasticIcons.ArrowDownward: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_arrow_downward)
val MeshtasticIcons.ExpandMore: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_expand_more)
val MeshtasticIcons.ExpandLess: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_expand_less)
