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
package org.meshtastic.screenshot.marketing

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.screenshot.marketing.resources.Res
import org.meshtastic.screenshot.marketing.resources.marketing_caption_channels_description
import org.meshtastic.screenshot.marketing.resources.marketing_caption_channels_title
import org.meshtastic.screenshot.marketing.resources.marketing_caption_map_description
import org.meshtastic.screenshot.marketing.resources.marketing_caption_map_title
import org.meshtastic.screenshot.marketing.resources.marketing_caption_messages_description
import org.meshtastic.screenshot.marketing.resources.marketing_caption_messages_title
import org.meshtastic.screenshot.marketing.resources.marketing_caption_node_detail_description
import org.meshtastic.screenshot.marketing.resources.marketing_caption_node_detail_title
import org.meshtastic.screenshot.marketing.resources.marketing_caption_nodes_description
import org.meshtastic.screenshot.marketing.resources.marketing_caption_nodes_title

/**
 * The five listing shots in listing order; file names sort into that order on Play and F-Droid alike. The caption is
 * the headline and body copy the framed variant draws above the phone, as string resources so Crowdin carries them.
 */
internal enum class Shot(
    val fileName: String,
    val captionTitle: StringResource,
    val captionDescription: StringResource,
) {
    Messages(
        "1_messages",
        Res.string.marketing_caption_messages_title,
        Res.string.marketing_caption_messages_description,
    ),
    Nodes("2_nodes", Res.string.marketing_caption_nodes_title, Res.string.marketing_caption_nodes_description),
    Map("3_map", Res.string.marketing_caption_map_title, Res.string.marketing_caption_map_description),
    NodeDetail(
        "4_node_detail",
        Res.string.marketing_caption_node_detail_title,
        Res.string.marketing_caption_node_detail_description,
    ),
    Channels(
        "5_channels",
        Res.string.marketing_caption_channels_title,
        Res.string.marketing_caption_channels_description,
    ),
}
