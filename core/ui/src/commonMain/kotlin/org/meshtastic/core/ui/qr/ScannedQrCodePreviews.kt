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
import org.meshtastic.core.ui.component.PRECISE_POSITION_BITS
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.ModuleSettings

private fun namedChannel(name: String, key: Byte) = ChannelSettings.Builder()
    .also { wb ->
        wb.name = name
        wb.psk = byteArrayOf(key).toByteString()
    }
    .build()

/** A private channel (a full-length key) that forwards to and from MQTT and shares the sender's precise position. */
private fun sharingChannel(name: String, key: Byte) = namedChannel(name, key)
    .newBuilder()
    .also { wb ->
        wb.psk = ByteArray(KEY_BYTES) { key }.toByteString()
        wb.uplink_enabled = true
        wb.downlink_enabled = true
        wb.module_settings = ModuleSettings.Builder().also { it.position_precision = PRECISE_POSITION_BITS }.build()
    }
    .build()

private const val KEY_BYTES = 32
private const val SAMPLE_NODE_NUM = 0x12345678
private const val SAMPLE_FREQUENCY_MHZ = 915.5f
private const val SAMPLE_SLOT = 20
private const val SAMPLE_HOP_LIMIT = 5

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

private val currentRadio =
    ChannelSet.Builder()
        .also { wb ->
            wb.settings = listOf(Channel.default.settings, namedChannel("Team 2", 2))
            wb.lora_config = Channel.default.loraConfig
        }
        .build()

/** A link that changes every kind of LoRa setting, including all three that affect sending and receiving. */
private val manyChangesLink =
    ChannelSet.Builder()
        .also { wb ->
            wb.settings = listOf(sharingChannel("Field", 5), namedChannel("Ops", 6))
            wb.lora_config =
                Channel.default.loraConfig
                    .newBuilder()
                    .also { lora ->
                        lora.region = LoRaConfig.RegionCode.EU_868
                        lora.modem_preset = LoRaConfig.ModemPreset.SHORT_FAST
                        lora.hop_limit = SAMPLE_HOP_LIMIT
                        lora.channel_num = SAMPLE_SLOT
                        lora.tx_enabled = false
                        lora.override_duty_cycle = true
                        lora.sx126x_rx_boosted_gain = true
                        lora.override_frequency = SAMPLE_FREQUENCY_MHZ
                        lora.ignore_incoming = listOf(SAMPLE_NODE_NUM)
                    }
                    .build()
        }
        .build()

private val fewChangesLink =
    ChannelSet.Builder()
        .also { wb ->
            wb.settings = listOf(namedChannel("Field", 5))
            wb.lora_config =
                Channel.default.loraConfig.newBuilder().also { lora -> lora.hop_limit = SAMPLE_HOP_LIMIT }.build()
        }
        .build()

@PreviewLightDark
@Composable
fun PreviewScannedQrCodeAddToFullRadio() {
    AppTheme { ScannedQrCodeDialog(channels = fullRadio, incoming = addOnlyLink, onDismiss = {}, onConfirm = {}) }
}

/** Add mode with free slots, for a link that carries channels only. */
@PreviewLightDark
@Composable
fun PreviewScannedQrCodeAddWithRoom() {
    AppTheme {
        ScannedQrCodeDialog(
            channels = currentRadio,
            incoming = ChannelSet.Builder().also { wb -> wb.settings = listOf(sharingChannel("Field", 5)) }.build(),
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@PreviewLightDark
@Composable
fun PreviewScannedQrCodeReplaceManyChanges() {
    AppTheme {
        ScannedQrCodeDialog(channels = currentRadio, incoming = manyChangesLink, onDismiss = {}, onConfirm = {})
    }
}

@PreviewLightDark
@Composable
fun PreviewScannedQrCodeReplaceFewChanges() {
    AppTheme { ScannedQrCodeDialog(channels = currentRadio, incoming = fewChangesLink, onDismiss = {}, onConfirm = {}) }
}
