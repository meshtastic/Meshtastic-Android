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
package org.meshtastic.core.ui.qr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Channel
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.accept
import org.meshtastic.core.strings.add
import org.meshtastic.core.strings.add_channels_description
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.new_channel_rcvd
import org.meshtastic.core.strings.replace
import org.meshtastic.core.strings.replace_channels_and_settings_description
import org.meshtastic.core.ui.component.ChannelSelection
import org.meshtastic.proto.ChannelSet

@Composable
fun ScannedQrCodeDialog(
    incoming: ChannelSet,
    onDismiss: () -> Unit,
    viewModel: ScannedQrCodeViewModel = hiltViewModel(),
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()

    ScannedQrCodeDialog(
        channels = channels,
        incoming = incoming,
        onDismiss = onDismiss,
        onConfirm = viewModel::setChannels,
    )
}

/** Enables the user to select which channels to accept after scanning a QR code. */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ScannedQrCodeDialog(
    channels: ChannelSet,
    incoming: ChannelSet,
    onDismiss: () -> Unit,
    onConfirm: (ChannelSet) -> Unit,
) {
    var shouldReplace by remember { mutableStateOf(incoming.lora_config != null) }

    val channelSet =
        remember(shouldReplace) {
            if (shouldReplace) {
                // When replacing, apply the incoming LoRa configuration but preserve certain
                // locally safe fields such as MQTT flags and TX power. This prevents QR codes
                // from unintentionally overriding device-specific power limits (e.g. E22 caps).
                incoming.copy(
                    lora_config =
                    incoming.lora_config?.copy(
                        config_ok_to_mqtt = channels.lora_config?.config_ok_to_mqtt ?: false,
                        tx_power = channels.lora_config?.tx_power ?: 0,
                    ),
                )
            } else {
                // To guarantee consistent ordering, using a LinkedHashSet which iterates through
                // its entries according to the order an item was *first* inserted.
                val result = (channels.settings + incoming.settings).distinct()
                channels.copy(settings = result)
            }
        }

    val modemPresetName = Channel(loraConfig = channelSet.lora_config ?: Channel.default.loraConfig).name

    /* Holds selections made by the user */
    val channelSelections =
        remember(channelSet) { mutableStateListOf(elements = Array(size = channelSet.settings.size, init = { true })) }

    val selectedChannelSet =
        if (shouldReplace) {
            channelSet.copy(
                settings = channelSet.settings.filterIndexed { i, _ -> channelSelections.getOrNull(i) == true },
            )
        } else {
            channelSet.copy(
                settings =
                channelSet.settings.filterIndexed { i, _ ->
                    val isExisting = i < channels.settings.size
                    isExisting || channelSelections.getOrNull(i) == true
                },
            )
        }

    // Compute LoRa configuration changes when in replace mode
    val loraChanges =
        remember(shouldReplace, channels, incoming) {
            if (shouldReplace && incoming.lora_config != null) {
                val current = channels.lora_config
                val new = incoming.lora_config
                val changes = mutableListOf<String>()

                if (current?.hop_limit != new?.hop_limit) {
                    changes.add("Hop Limit: ${current?.hop_limit} -> ${new?.hop_limit}")
                }
                if (current?.region != new?.region) {
                    val currentRegionDesc = current?.region?.name ?: "Unknown"
                    val newRegionDesc = new?.region?.name ?: "Unknown"
                    changes.add("Region: $currentRegionDesc -> $newRegionDesc")
                }
                if (current?.modem_preset != new?.modem_preset) {
                    val currentPresetDesc = current?.modem_preset?.name ?: "Unknown"
                    val newPresetDesc = new?.modem_preset?.name ?: "Unknown"
                    changes.add("Modem Preset: $currentPresetDesc -> $newPresetDesc")
                }
                if (current?.use_preset != new?.use_preset) {
                    changes.add("Use Preset: ${current?.use_preset} -> ${new?.use_preset}")
                }

                changes
            } else {
                emptyList()
            }
        }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.new_channel_rcvd),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                item {
                    Text(
                        text =
                        stringResource(
                            if (shouldReplace) {
                                Res.string.replace_channels_and_settings_description
                            } else {
                                Res.string.add_channels_description
                            },
                        ),
                        modifier = Modifier.padding(bottom = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                itemsIndexed(channelSet.settings) { index, channel ->
                    val isExisting = !shouldReplace && index < channels.settings.size
                    val channelObj = Channel(channel, channelSet.lora_config ?: Channel.default.loraConfig)
                    ChannelSelection(
                        index = index,
                        title = channel.name.ifEmpty { modemPresetName },
                        enabled = !isExisting,
                        isSelected = if (isExisting) true else channelSelections[index],
                        onSelected = {
                            if (it || selectedChannelSet.settings.size > 1) {
                                channelSelections[index] = it
                            }
                        },
                        channel = channelObj,
                    )
                }

                // Display LoRa configuration changes when in replace mode
                if (shouldReplace && loraChanges.isNotEmpty()) {
                    item {
                        Text(
                            text = "LoRa Configuration Changes:",
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        loraChanges.forEach { change ->
                            Text(
                                text = "• $change",
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.padding(vertical = 20.dp)) {
                        val selectedColors = ButtonDefaults.buttonColors()
                        val unselectedColors =
                            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

                        OutlinedButton(
                            onClick = { shouldReplace = false },
                            modifier = Modifier.height(48.dp).weight(1f),
                            colors = if (!shouldReplace) selectedColors else unselectedColors,
                        ) {
                            Text(text = stringResource(Res.string.add))
                        }

                        OutlinedButton(
                            onClick = { shouldReplace = true },
                            modifier = Modifier.height(48.dp).weight(1f),
                            enabled = incoming.lora_config != null,
                            colors = if (shouldReplace) selectedColors else unselectedColors,
                        ) {
                            Text(text = stringResource(Res.string.replace))
                        }
                    }
                }

                /* User Actions via buttons */
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        TextButton(onClick = { onDismiss() }) {
                            Text(
                                text = stringResource(Res.string.cancel),
                                color = MaterialTheme.colorScheme.onSurface,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }

                        TextButton(
                            onClick = {
                                onDismiss()
                                onConfirm(selectedChannelSet)
                            },
                            enabled = selectedChannelSet.settings.size in 1..8,
                        ) {
                            Text(
                                text = stringResource(Res.string.accept),
                                color = MaterialTheme.colorScheme.onSurface,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun ScannedQrCodeDialogPreview() {
    ScannedQrCodeDialog(
        channels = ChannelSet(settings = listOf(Channel.default.settings), lora_config = Channel.default.loraConfig),
        incoming = ChannelSet(settings = listOf(Channel.default.settings), lora_config = Channel.default.loraConfig),
        onDismiss = {},
        onConfirm = {},
    )
}
