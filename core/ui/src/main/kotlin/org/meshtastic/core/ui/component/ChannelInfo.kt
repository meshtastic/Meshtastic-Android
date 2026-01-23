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
package org.meshtastic.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.channel
import org.meshtastic.core.ui.icon.Channel
import org.meshtastic.core.ui.icon.Counter0
import org.meshtastic.core.ui.icon.Counter1
import org.meshtastic.core.ui.icon.Counter2
import org.meshtastic.core.ui.icon.Counter3
import org.meshtastic.core.ui.icon.Counter4
import org.meshtastic.core.ui.icon.Counter5
import org.meshtastic.core.ui.icon.Counter6
import org.meshtastic.core.ui.icon.Counter7
import org.meshtastic.core.ui.icon.Counter8
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun ChannelInfo(
    channel: Int,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val icon =
        when (channel) {
            0 -> MeshtasticIcons.Counter0
            1 -> MeshtasticIcons.Counter1
            2 -> MeshtasticIcons.Counter2
            3 -> MeshtasticIcons.Counter3
            4 -> MeshtasticIcons.Counter4
            5 -> MeshtasticIcons.Counter5
            6 -> MeshtasticIcons.Counter6
            7 -> MeshtasticIcons.Counter7
            8 -> MeshtasticIcons.Counter8
            else -> MeshtasticIcons.Channel
        }

    IconInfo(
        modifier = modifier,
        icon = icon,
        contentDescription = stringResource(Res.string.channel),
        text = stringResource(Res.string.channel),
        contentColor = contentColor,
    )
}

@PreviewLightDark
@Composable
private fun ChannelInfoPreview() {
    AppTheme { ChannelInfo(channel = 2) }
}
