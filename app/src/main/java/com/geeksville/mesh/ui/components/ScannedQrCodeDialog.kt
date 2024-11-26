/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.components

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
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
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
import com.geeksville.mesh.AppOnlyProtos.ChannelSet
import com.geeksville.mesh.R
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.ui.components.config.ChannelSelection

/**
 * Enables the user to select which channels to accept after scanning a QR code.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
fun ScannedQrCodeDialog(
    channels: ChannelSet,
    incoming: ChannelSet,
    onDismiss: () -> Unit,
    onConfirm: (ChannelSet) -> Unit
) {
    var shouldReplace by remember { mutableStateOf(incoming.hasLoraConfig()) }

    val channelSet = remember(shouldReplace) {
        if (shouldReplace) {
            incoming
        } else {
            channels.copy {
                // To guarantee consistent ordering, using a LinkedHashSet which iterates through
                // it's entries according to the order an item was *first* inserted.
                // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-linked-hash-set/
                val result = LinkedHashSet(settings + incoming.settingsList)
                settings.clear()
                settings.addAll(result)
            }
        }
    }

    val modemPresetName = Channel(loraConfig = channelSet.loraConfig).name

    /* Holds selections made by the user */
    val channelSelections = remember(channelSet) {
        mutableStateListOf(elements = Array(size = channelSet.settingsCount, init = { true }))
    }

    val selectedChannelSet = channelSet.copy {
        val result = settings.filterIndexed { i, _ -> channelSelections.getOrNull(i) == true }
        settings.clear()
        settings.addAll(result)
    }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colors.background
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text(
                        text = stringResource(id = R.string.new_channel_rcvd),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.h6,
                    )
                }
                itemsIndexed(channelSet.settingsList) { index, channel ->
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
                    )
                }

                item {
                    Row(
                        modifier = Modifier.padding(vertical = 20.dp),
                    ) {
                        val selectedColors = ButtonDefaults.buttonColors()
                        val unselectedColors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colors.onSurface,
                        )

                        OutlinedButton(
                            onClick = { shouldReplace = false },
                            modifier = Modifier
                                .height(48.dp)
                                .weight(1f),
                            colors = if (!shouldReplace) selectedColors else unselectedColors,
                        ) { Text(text = stringResource(R.string.add)) }

                        OutlinedButton(
                            onClick = { shouldReplace = true },
                            modifier = Modifier
                                .height(48.dp)
                                .weight(1f),
                            enabled = incoming.hasLoraConfig(),
                            colors = if (shouldReplace) selectedColors else unselectedColors,
                        ) { Text(text = stringResource(R.string.replace)) }
                    }
                }

                /* User Actions via buttons */
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        TextButton(
                            onClick = {
                                onDismiss()
                            },
                        ) {
                            Text(
                                text = stringResource(id = R.string.cancel),
                                color = MaterialTheme.colors.onSurface,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.body1,
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
                                color = MaterialTheme.colors.onSurface,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                style = MaterialTheme.typography.body1,
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
        channels = channelSet {
            settings.add(Channel.default.settings)
            loraConfig = Channel.default.loraConfig
        },
        incoming = channelSet {
            settings.add(Channel.default.settings)
            loraConfig = Channel.default.loraConfig
        },
        onDismiss = {},
        onConfirm = {},
    )
}
