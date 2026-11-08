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
package org.meshtastic.feature.settings.radio.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.model.Channel
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings

private val previewChannelSet =
    ChannelSet.Builder()
        .also { wb ->
            wb.settings =
                listOf(Channel.default.settings, ChannelSettings.Builder().also { it.name = "Hiking" }.build())
            wb.lora_config = Channel.default.loraConfig
        }
        .build()

@Composable
private fun ChannelSharePreview(shouldAdd: Boolean) {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            ChannelShareDialog(
                shareState = ChannelShareState().also { it.shouldAdd = shouldAdd },
                channelSet = previewChannelSet,
                modemPresetName = "LongFast",
                onDismiss = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ChannelShareReplacePreview() {
    ChannelSharePreview(shouldAdd = false)
}

@PreviewLightDark
@Composable
fun ChannelShareAddPreview() {
    ChannelSharePreview(shouldAdd = true)
}
