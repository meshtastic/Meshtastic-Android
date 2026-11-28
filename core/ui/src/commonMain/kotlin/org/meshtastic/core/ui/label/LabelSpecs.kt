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
package org.meshtastic.core.ui.label

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.label_print_adds_channels
import org.meshtastic.core.resources.label_print_band_channel
import org.meshtastic.core.resources.label_print_band_name_tag
import org.meshtastic.core.resources.label_print_default_channel
import org.meshtastic.core.resources.label_print_replaces_channels
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.SharedContact

/** The name tag for [node], whose QR encodes [contact], the contact Share Contact shows. */
@Composable
fun nameTagSpec(node: Node, contact: SharedContact): LabelSpec = LabelSpec.NameTag(
    content =
    NameTagContent(
        shortName = node.user.short_name,
        longName = node.user.long_name,
        nodeId = node.user.id.ifEmpty { NodeAddress.numToDefaultId(node.num) },
    ),
    contact = contact,
    band = stringResource(Res.string.label_print_band_name_tag),
)

/**
 * The channel label for [channelSet], whose QR encodes [url], the link Share Channels shows. [shouldAdd] is whether
 * scanning adds the channels or replaces the scanner's.
 */
@Composable
fun channelLabelSpec(channelSet: ChannelSet, url: String, shouldAdd: Boolean, modemPresetName: String): LabelSpec =
    LabelSpec.Channels(
        content =
        channelLabelContent(
            channelSet = channelSet,
            modemPresetName = modemPresetName,
            defaultChannel = stringResource(Res.string.label_print_default_channel),
            action =
            if (shouldAdd) {
                pluralStringResource(
                    Res.plurals.label_print_adds_channels,
                    channelSet.settings.size,
                    channelSet.settings.size,
                )
            } else {
                stringResource(Res.string.label_print_replaces_channels)
            },
        ),
        url = url,
        band = stringResource(Res.string.label_print_band_channel),
    )
