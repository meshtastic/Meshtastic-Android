package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.geeksville.mesh.AppOnlyProtos.ChannelSet
import com.geeksville.mesh.R
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.ui.components.config.ChannelCard
import com.geeksville.mesh.ui.components.config.ChannelSelection

/**
 * Enables the user to select which channels to accept after scanning a QR code.
 */
@Suppress("LongMethod")
@Composable
fun ScannedQrCodeDialog(
    channels: ChannelSet,
    incoming: ChannelSet,
    onDismiss: () -> Unit,
    onConfirm: (ChannelSet) -> Unit
) {
    var currentChannelSet by remember(channels) { mutableStateOf(channels) }
    val modemPresetName = Channel(loraConfig = currentChannelSet.loraConfig).name

    /* Holds selections made by the user */
    val channelSelections = remember { mutableStateListOf(elements = Array(size = 8, init = { true })) }

    /* The save button is enabled based on this count */
    var totalCount = currentChannelSet.settingsList.size
    for ((index, isSelected) in channelSelections.withIndex()) {
        if (index >= incoming.settingsList.size)
            break
        if (isSelected)
            totalCount++
    }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colors.background
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /* Incoming ChannelSet */
                item {
                    Text(
                        style = MaterialTheme.typography.body1,
                        text = stringResource(id = R.string.scanned_channels)
                    )
                }
                itemsIndexed(incoming.settingsList) { index, channel ->
                    ChannelSelection(
                        index = index,
                        title = channel.name.ifEmpty { modemPresetName },
                        enabled = true,
                        isSelected = channelSelections[index],
                        onSelected = { channelSelections[index] = it }
                    )
                }

                /* Current ChannelSet */
                item {
                    Text(
                        style = MaterialTheme.typography.body1,
                        text = stringResource(id = R.string.current_channels)
                    )
                }
                itemsIndexed(currentChannelSet.settingsList) { index, channel ->
                    ChannelCard(
                        index = index,
                        title = channel.name.ifEmpty { modemPresetName },
                        enabled = true,
                        onEditClick = { /* Currently we don't enable editing from this dialog. */ },
                        onDeleteClick = {
                            val list = currentChannelSet.settingsList.toMutableList()
                            list.removeAt(index)
                            currentChannelSet = currentChannelSet.copy {
                                settings.clear()
                                settings.addAll(list)
                            }
                        }
                    )
                }

                /* User Actions via buttons */
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        /* Cancel */
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .weight(1f)
                                .padding(3.dp)
                        ) {
                            Text(
                                style = MaterialTheme.typography.body1,
                                text = stringResource(id = R.string.cancel)
                            )
                        }

                        /* Add - Appends incoming selected channels to the current set */
                        Button(
                            enabled = totalCount <= 8,
                            onClick = {
                                val appended = currentChannelSet.copy {
                                    val result = incoming.settingsList.filterIndexed { i, _ ->
                                        channelSelections.getOrNull(i) == true
                                    }
                                    settings.addAll(result)
                                }
                                onDismiss.invoke()
                                onConfirm.invoke(appended)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .weight(1f)
                                .padding(3.dp)
                        ) {
                            Text(
                                style = MaterialTheme.typography.body1,
                                text = stringResource(id = R.string.add)
                            )
                        }

                        /* Replace - Replaces the previous set with the scanned channel set */
                        Button(
                            onClick = {
                                onDismiss.invoke()
                                onConfirm.invoke(incoming)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .weight(1f)
                                .padding(3.dp)
                        ) {
                            Text(
                                style = MaterialTheme.typography.body1,
                                text = stringResource(id = R.string.replace)
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
