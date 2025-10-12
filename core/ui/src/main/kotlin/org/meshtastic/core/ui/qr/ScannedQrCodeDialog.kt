/*
 * Copyright (c) 2025 Meshtastic LLC
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.model.Channel
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.ChannelSelection
import org.meshtastic.proto.AppOnlyProtos.ChannelSet
import org.meshtastic.proto.ConfigProtos.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.channelSet
import org.meshtastic.proto.copy

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
    var shouldReplace by remember { mutableStateOf(incoming.hasLoraConfig()) }

    val channelSet =
        remember(shouldReplace) {
            if (shouldReplace) {
                incoming.copy { loraConfig = loraConfig.copy { configOkToMqtt = channels.loraConfig.configOkToMqtt } }
            } else {
                channels.copy {
                    // To guarantee consistent ordering, using a LinkedHashSet which iterates through
                    // its entries according to the order an item was *first* inserted.
                    // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-linked-hash-set/
                    val result = LinkedHashSet(settings + incoming.settingsList)
                    settings.clear()
                    settings.addAll(result)
                }
            }
        }

    val modemPresetName = Channel(loraConfig = channelSet.loraConfig).name

    /* Holds selections made by the user */
    val channelSelections =
        remember(channelSet) { mutableStateListOf(elements = Array(size = channelSet.settingsCount, init = { true })) }

    val selectedChannelSet =
        channelSet.copy {
            val result = settings.filterIndexed { i, _ -> channelSelections.getOrNull(i) == true }
            settings.clear()
            settings.addAll(result)
        }

    // Compute LoRa configuration changes when in replace mode
    val loraChanges =
        remember(shouldReplace, channels, incoming) {
            if (shouldReplace && incoming.hasLoraConfig()) {
                val current = channels.loraConfig
                val new = incoming.loraConfig
                val changes = mutableListOf<String>()

                if (current.hopLimit != new.hopLimit) {
                    changes.add("Hop Limit: ${current.hopLimit} -> ${new.hopLimit}")
                }
                if (current.getRegion() != new.getRegion()) {
                    val currentRegionDesc = current.getRegion()?.name ?: "Unknown"
                    val newRegionDesc = new.getRegion()?.name ?: "Unknown"
                    changes.add("Region: $currentRegionDesc -> $newRegionDesc")
                }
                if (current.modemPreset != new.modemPreset) {
                    val currentPresetDesc = ModemPreset.forNumber(current.modemPreset.number)?.name ?: "Unknown"
                    val newPresetDesc = ModemPreset.forNumber(new.modemPreset.number)?.name ?: "Unknown"
                    changes.add("Modem Preset: $currentPresetDesc -> $newPresetDesc")
                }
                if (current.usePreset != new.usePreset) {
                    changes.add("Use Preset: ${current.usePreset} -> ${new.usePreset}")
                }
                if (current.txEnabled != new.txEnabled) {
                    changes.add("Transmit Enabled: ${current.txEnabled} -> ${new.txEnabled}")
                }
                if (current.txPower != new.txPower) {
                    changes.add("Transmit Power: ${current.txPower}dBm -> ${new.txPower}dBm")
                }
                if (current.channelNum != new.channelNum) {
                    changes.add("Channel Number: ${current.channelNum} -> ${new.channelNum}")
                }
                if (current.bandwidth != new.bandwidth) {
                    changes.add("Bandwidth: ${current.bandwidth} -> ${new.bandwidth}")
                }
                if (current.codingRate != new.codingRate) {
                    changes.add("Coding Rate: ${current.codingRate} -> ${new.codingRate}")
                }
                if (current.spreadFactor != new.spreadFactor) {
                    changes.add("Spread Factor: ${current.spreadFactor} -> ${new.spreadFactor}")
                }
                if (current.sx126XRxBoostedGain != new.sx126XRxBoostedGain) {
                    changes.add("RX Boosted Gain: ${current.sx126XRxBoostedGain} -> ${new.sx126XRxBoostedGain}")
                }
                if (current.overrideFrequency != new.overrideFrequency) {
                    changes.add("Override Frequency: ${current.overrideFrequency} -> ${new.overrideFrequency}")
                }
                if (current.ignoreMqtt != new.ignoreMqtt) {
                    changes.add("Ignore MQTT: ${current.ignoreMqtt} -> ${new.ignoreMqtt}")
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
                        text = stringResource(id = R.string.new_channel_rcvd),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                itemsIndexed(channelSet.settingsList) { index, channel ->
                    val channelObj = Channel(channel, channelSet.loraConfig)
                    ChannelSelection(
                        index = index,
                        title = channel.name.ifEmpty { modemPresetName },
                        enabled = true,
                        isSelected = channelSelections[index],
                        onSelected = {
                            if (it || selectedChannelSet.settingsCount > 1) {
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
                            Text(text = stringResource(R.string.add))
                        }

                        OutlinedButton(
                            onClick = { shouldReplace = true },
                            modifier = Modifier.height(48.dp).weight(1f),
                            enabled = incoming.hasLoraConfig(),
                            colors = if (shouldReplace) selectedColors else unselectedColors,
                        ) {
                            Text(text = stringResource(R.string.replace))
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
                                text = stringResource(id = R.string.cancel),
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
                            enabled = selectedChannelSet.settingsCount in 1..8,
                        ) {
                            Text(
                                text = stringResource(id = R.string.accept),
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
        channels =
        channelSet {
            settings.add(Channel.default.settings)
            loraConfig = Channel.default.loraConfig
        },
        incoming =
        channelSet {
            settings.add(Channel.default.settings)
            loraConfig = Channel.default.loraConfig
        },
        onDismiss = {},
        onConfirm = {},
    )
}
