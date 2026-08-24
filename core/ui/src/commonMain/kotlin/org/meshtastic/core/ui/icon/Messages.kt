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
import org.meshtastic.core.resources.ic_add_link
import org.meshtastic.core.resources.ic_chat_bubble_outline
import org.meshtastic.core.resources.ic_fast_forward
import org.meshtastic.core.resources.ic_filter_list
import org.meshtastic.core.resources.ic_filter_list_off
import org.meshtastic.core.resources.ic_format_quote
import org.meshtastic.core.resources.ic_forum
import org.meshtastic.core.resources.ic_link
import org.meshtastic.core.resources.ic_message
import org.meshtastic.core.resources.ic_visibility
import org.meshtastic.core.resources.ic_visibility_off

// Messaging UI icons
val MeshtasticIcons.ChatBubbleOutline: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_chat_bubble_outline)
val MeshtasticIcons.FormatQuote: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_format_quote)
val MeshtasticIcons.FilterList: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_filter_list)
val MeshtasticIcons.FilterListOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_filter_list_off)
val MeshtasticIcons.FastForward: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_fast_forward)
val MeshtasticIcons.Visibility: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_visibility)
val MeshtasticIcons.VisibilityOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_visibility_off)
val MeshtasticIcons.AddLink: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_add_link)
val MeshtasticIcons.LinkIcon: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_link)
val MeshtasticIcons.Message: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_message)
val MeshtasticIcons.Conversations: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_forum)
