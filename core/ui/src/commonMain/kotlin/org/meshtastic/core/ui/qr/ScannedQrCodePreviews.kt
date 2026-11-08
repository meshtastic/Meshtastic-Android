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
package org.meshtastic.core.ui.qr

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Channel
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings

private fun namedChannel(name: String, key: Byte) = ChannelSettings.Builder()
    .also { wb ->
        wb.name = name
        wb.psk = byteArrayOf(key).toByteString()
    }
    .build()

private val fullRadio =
    ChannelSet.Builder()
        .also { wb ->
            wb.settings =
                listOf(Channel.default.settings) + (2..8).map { namedChannel(name = "Team $it", key = it.toByte()) }
            wb.lora_config = Channel.default.loraConfig
        }
        .build()

/** An add-only link to a full radio: one channel is already there, one has no free slot. */
private val addOnlyLink =
    ChannelSet.Builder()
        .also { wb -> wb.settings = listOf(namedChannel("Team 2", 2), namedChannel("Hiking", 9)) }
        .build()

@PreviewLightDark
@Composable
fun PreviewScannedQrCodeAddToFullRadio() {
    AppTheme { ScannedQrCodeDialog(channels = fullRadio, incoming = addOnlyLink, onDismiss = {}, onConfirm = {}) }
}
