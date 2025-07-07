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

package com.geeksville.mesh.ui.radioconfig.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ChannelProtos.ChannelSettings
import com.geeksville.mesh.ConfigProtos.Config.LoRaConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.dragContainer
import com.geeksville.mesh.ui.common.components.dragDropItemsIndexed
import com.geeksville.mesh.ui.common.components.rememberDragDropState
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel
import com.geeksville.mesh.ui.common.components.SecurityIcon

@Composable
private fun ChannelItem(
    index: Int,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = enabled) { onClick() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        ) {

            AssistChip(onClick = onClick, label = {
                Text(
                    text = "$index",
                )
            })
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge,
            )
            content()
        }
    }
}

@Composable
fun ChannelCard(
    index: Int,
    title: String,
    enabled: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    channel: Channel,
) = ChannelItem(
    index = index,
    title = title,
    enabled = enabled,
    onClick = onEditClick,
) {
    SecurityIcon(channel)
    Spacer(modifier = Modifier.width(10.dp))
    IconButton(onClick = { onDeleteClick() }) {
        Icon(
            imageVector = Icons.TwoTone.Close,
            contentDescription = stringResource(R.string.delete),
            modifier = Modifier.wrapContentSize(),
        )
    }
}

@Composable
fun ChannelSelection(
    index: Int,
    title: String,
    enabled: Boolean,
    isSelected: Boolean,
    onSelected: (Boolean) -> Unit,
    channel: Channel,
) = ChannelItem(
    index = index,
    title = title,
    enabled = enabled,
    onClick = {},
) {
    SecurityIcon(channel)
    Spacer(modifier = Modifier.width(10.dp))
    Checkbox(
        enabled = enabled,
        checked = isSelected,
        onCheckedChange = onSelected,
    )
}

@Composable
fun ChannelConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    ChannelSettingsItemList(
        settingsList = state.channelList,
        loraConfig = state.radioConfig.lora,
        enabled = state.connected,
        maxChannels = viewModel.maxChannels,
        onPositiveClicked = { channelListInput ->
            viewModel.updateChannels(channelListInput, state.channelList)
        },
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ChannelSettingsItemList(
    settingsList: List<ChannelSettings>,
    loraConfig: LoRaConfig,
    maxChannels: Int = 8,
    enabled: Boolean,
    onNegativeClicked: () -> Unit = { },
    onPositiveClicked: (List<ChannelSettings>) -> Unit,
) {
    val primarySettings = settingsList.getOrNull(0) ?: return
    val primaryChannel by remember(loraConfig) {
        mutableStateOf(Channel(primarySettings, loraConfig))
    }

    val focusManager = LocalFocusManager.current
    val settingsListInput = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { settingsList.toMutableStateList() }

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState, headerCount = 1) { fromIndex, toIndex ->
        if (toIndex in settingsListInput.indices && fromIndex in settingsListInput.indices) {
            settingsListInput.apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    val isEditing: Boolean = settingsList.size != settingsListInput.size ||
            settingsList.zip(settingsListInput).any { (item1, item2) -> item1 != item2 }

    var showEditChannelDialog: Int? by rememberSaveable { mutableStateOf(null) }

    if (showEditChannelDialog != null) {
        val index = showEditChannelDialog ?: return
        EditChannelDialog(
            channelSettings = with(settingsListInput) {
                if (size > index) get(index) else channelSettings { }
            },
            modemPresetName = primaryChannel.name,
            onAddClick = {
                if (settingsListInput.size > index) {
                    settingsListInput[index] = it
                } else {
                    settingsListInput.add(it)
                }
                showEditChannelDialog = null
            },
            onDismissRequest = { showEditChannelDialog = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = { }, enabled = false)
    ) {
        Column {

            ChannelsConfigHeader(
                frequency = if (loraConfig.overrideFrequency != 0f) {
                    loraConfig.overrideFrequency
                } else {
                    primaryChannel.radioFreq
                },
                slot = if (loraConfig.channelNum != 0) {
                    loraConfig.channelNum
                } else {
                    primaryChannel.channelNum
                }
            )
            Text(
                text = stringResource(R.string.press_and_drag),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 16.dp)
            )
            LazyColumn(
                modifier = Modifier.dragContainer(
                    dragDropState = dragDropState,
                    haptics = LocalHapticFeedback.current,
                ),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.primary),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                dragDropItemsIndexed(
                    items = settingsListInput,
                    dragDropState = dragDropState,
                ) { index, channel, isDragging ->
                    val channelObj = Channel(channel, loraConfig)
                    ChannelCard(
                        index = index,
                        title = channel.name.ifEmpty { primaryChannel.name },
                        enabled = enabled,
                        onEditClick = { showEditChannelDialog = index },
                        onDeleteClick = { settingsListInput.removeAt(index) },
                        channel = channelObj
                    )
                    if (index == 0 && !isDragging) {
                        Text(
                            text = stringResource(R.string.primary_channel_feature),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.secondary),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.secondary_no_telemetry),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 10.sp,
                        )
                        Text(
                            text = stringResource(R.string.manual_position_request),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 10.sp,
                        )
                    }
                }
                item {
                    PreferenceFooter(
                        enabled = enabled && isEditing,
                        negativeText = R.string.cancel,
                        onNegativeClicked = {
                            focusManager.clearFocus()
                            settingsListInput.clear()
                            settingsListInput.addAll(settingsList)
                            onNegativeClicked()
                        },
                        positiveText = R.string.send,
                        onPositiveClicked = {
                            focusManager.clearFocus()
                            onPositiveClicked(settingsListInput)
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = maxChannels > settingsListInput.size,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        ) {
            FloatingActionButton(
                onClick = {
                    if (maxChannels > settingsListInput.size) {
                        settingsListInput.add(
                            channelSettings {
                            psk = Channel.default.settings.psk
                        }
                        )
                        showEditChannelDialog = settingsListInput.lastIndex
                    }
                },
                modifier = Modifier.padding(16.dp)
            ) { Icon(Icons.TwoTone.Add, stringResource(R.string.add)) }
        }
    }
}

@Composable
private fun ChannelsConfigHeader(
    frequency: Float,
    slot: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PreferenceCategory(text = stringResource(R.string.channels))
        Column {
            Text(
                text = "${stringResource(R.string.freq)}: ${frequency}MHz",
                fontSize = 11.sp,
            )
            Text(
                text = "${stringResource(R.string.slot)}: $slot",
                fontSize = 11.sp,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChannelSettingsPreview() {
    ChannelSettingsItemList(
        settingsList = listOf(
            channelSettings {
                psk = Channel.default.settings.psk
                name = Channel.default.name
            },
            channelSettings {
                name = stringResource(R.string.channel_name)
            },
        ),
        loraConfig = Channel.default.loraConfig,
        enabled = true,
        onPositiveClicked = { },
    )
}
