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

package com.geeksville.mesh.ui.settings.radio.components

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
import androidx.compose.ui.graphics.Color
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
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SecurityIcon
import com.geeksville.mesh.ui.common.components.dragContainer
import com.geeksville.mesh.ui.common.components.dragDropItemsIndexed
import com.geeksville.mesh.ui.common.components.rememberDragDropState
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel

@Composable
private fun ChannelItem(
    index: Int,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit,
) {
    val fontColor = if (index == 0) MaterialTheme.colorScheme.primary else Color.Unspecified
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(enabled = enabled) { onClick() }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
        ) {
            AssistChip(onClick = onClick, label = { Text(text = "$index", color = fontColor) })
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge,
                color = fontColor,
            )
            content()
        }
    }
}

@Composable
private fun ChannelCard(
    index: Int,
    title: String,
    enabled: Boolean,
    channelSettings: ChannelSettings,
    loraConfig: LoRaConfig,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    sharesLocation: Boolean,
) = ChannelItem(index = index, title = title, enabled = enabled, onClick = onEditClick) {
    if (sharesLocation) {
        Icon(
            imageVector = ChannelIcons.LOCATION.icon,
            contentDescription = stringResource(ChannelIcons.LOCATION.descriptionResId),
            modifier = Modifier.wrapContentSize().padding(horizontal = 5.dp),
        )
    }
    if (channelSettings.uplinkEnabled) {
        Icon(
            imageVector = ChannelIcons.UPLINK.icon,
            contentDescription = stringResource(ChannelIcons.UPLINK.descriptionResId),
            modifier = Modifier.wrapContentSize().padding(horizontal = 5.dp),
        )
    }
    if (channelSettings.downlinkEnabled) {
        Icon(
            imageVector = ChannelIcons.DOWNLINK.icon,
            contentDescription = stringResource(ChannelIcons.DOWNLINK.descriptionResId),
            modifier = Modifier.wrapContentSize().padding(horizontal = 5.dp),
        )
    }
    SecurityIcon(channelSettings, loraConfig)
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
) = ChannelItem(index = index, title = title, enabled = enabled) {
    SecurityIcon(channel)
    Spacer(modifier = Modifier.width(10.dp))
    Checkbox(enabled = enabled, checked = isSelected, onCheckedChange = onSelected)
}

@Composable
fun ChannelConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(state = state.responseState, onDismiss = viewModel::clearPacketResponse)
    }

    ChannelSettingsItemList(
        settingsList = state.channelList,
        loraConfig = state.radioConfig.lora,
        maxChannels = viewModel.maxChannels,
        firmwareVersion = state.metadata?.firmwareVersion ?: "0.0.0",
        enabled = state.connected,
        onPositiveClicked = { channelListInput -> viewModel.updateChannels(channelListInput, state.channelList) },
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun ChannelSettingsItemList(
    settingsList: List<ChannelSettings>,
    loraConfig: LoRaConfig,
    maxChannels: Int = 8,
    firmwareVersion: String,
    enabled: Boolean,
    onPositiveClicked: (List<ChannelSettings>) -> Unit,
) {
    val primarySettings = settingsList.getOrNull(0) ?: return
    val modemPresetName by remember(loraConfig) { mutableStateOf(Channel(loraConfig = loraConfig).name) }
    val primaryChannel by remember(loraConfig) { mutableStateOf(Channel(primarySettings, loraConfig)) }
    val fwVersion by
        remember(firmwareVersion) { mutableStateOf(DeviceVersion(firmwareVersion.substringBeforeLast("."))) }

    val focusManager = LocalFocusManager.current
    val settingsListInput =
        rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
            settingsList.toMutableStateList()
        }

    val listState = rememberLazyListState()
    val dragDropState =
        rememberDragDropState(listState) { fromIndex, toIndex ->
            if (toIndex in settingsListInput.indices && fromIndex in settingsListInput.indices) {
                settingsListInput.apply { add(toIndex, removeAt(fromIndex)) }
            }
        }

    val isEditing: Boolean =
        settingsList.size != settingsListInput.size ||
            settingsList.zip(settingsListInput).any { (item1, item2) -> item1 != item2 }

    var showEditChannelDialog: Int? by rememberSaveable { mutableStateOf(null) }
    var showChannelLegendDialog by rememberSaveable { mutableStateOf(false) }

    if (showEditChannelDialog != null) {
        val index = showEditChannelDialog ?: return
        EditChannelDialog(
            channelSettings = with(settingsListInput) { if (size > index) get(index) else channelSettings {} },
            modemPresetName = modemPresetName,
            onAddClick = {
                if (settingsListInput.size > index) {
                    settingsListInput[index] = it
                } else {
                    settingsListInput.add(it)
                }
                showEditChannelDialog = null
            },
            onDismissRequest = { showEditChannelDialog = null },
        )
    }

    if (showChannelLegendDialog) {
        ChannelLegendDialog(fwVersion) { showChannelLegendDialog = false }
    }

    Box(modifier = Modifier.fillMaxSize().clickable(onClick = {}, enabled = false)) {
        Column {
            ChannelsConfigHeader(
                frequency =
                if (loraConfig.overrideFrequency != 0f) {
                    loraConfig.overrideFrequency
                } else {
                    primaryChannel.radioFreq
                },
                slot =
                if (loraConfig.channelNum != 0) {
                    loraConfig.channelNum
                } else {
                    primaryChannel.channelNum
                },
            )
            Text(
                text = stringResource(R.string.press_and_drag),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 16.dp),
            )

            ChannelLegend { showChannelLegendDialog = true }

            val locationChannel = determineLocationSharingChannel(fwVersion, settingsListInput.toList())

            LazyColumn(
                modifier = Modifier.dragContainer(dragDropState = dragDropState, haptics = LocalHapticFeedback.current),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                dragDropItemsIndexed(items = settingsListInput, dragDropState = dragDropState) {
                        index,
                        channel,
                        isDragging,
                    ->
                    ChannelCard(
                        index = index,
                        title = channel.name.ifEmpty { modemPresetName },
                        enabled = enabled,
                        channelSettings = channel,
                        loraConfig = loraConfig,
                        onEditClick = { showEditChannelDialog = index },
                        onDeleteClick = { settingsListInput.removeAt(index) },
                        sharesLocation = locationChannel == index,
                    )
                }
                item {
                    PreferenceFooter(
                        enabled = enabled && isEditing,
                        negativeText = R.string.cancel,
                        onNegativeClicked = {
                            focusManager.clearFocus()
                            settingsListInput.clear()
                            settingsListInput.addAll(settingsList)
                        },
                        positiveText = R.string.send,
                        onPositiveClicked = {
                            focusManager.clearFocus()
                            onPositiveClicked(settingsListInput)
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = maxChannels > settingsListInput.size,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter =
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            ),
            exit =
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            ),
        ) {
            FloatingActionButton(
                onClick = {
                    if (maxChannels > settingsListInput.size) {
                        settingsListInput.add(channelSettings { psk = Channel.default.settings.psk })
                        showEditChannelDialog = settingsListInput.lastIndex
                    }
                },
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(Icons.TwoTone.Add, stringResource(R.string.add))
            }
        }
    }
}

@Composable
private fun ChannelsConfigHeader(frequency: Float, slot: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        PreferenceCategory(text = stringResource(R.string.channels))
        Column {
            Text(text = "${stringResource(R.string.freq)}: ${frequency}MHz", fontSize = 11.sp)
            Text(text = "${stringResource(R.string.slot)}: $slot", fontSize = 11.sp)
        }
    }
}

/**
 * Determines what [Channel] if any is enabled to conduct automatic location sharing.
 *
 * @param firmwareVersion of the connected node.
 * @param settingsList Current list of channels on the node.
 * @return the index of the channel within `settingsList`.
 */
private fun determineLocationSharingChannel(firmwareVersion: DeviceVersion, settingsList: List<ChannelSettings>): Int {
    var output = -1
    if (firmwareVersion >= DeviceVersion(asString = SECONDARY_CHANNEL_EPOCH)) {
        /* Essentially the first index with the setting enabled */
        for ((i, settings) in settingsList.withIndex()) {
            if (settings.moduleSettings.positionPrecision > 0) {
                output = i
                break
            }
        }
    } else {
        /* Only the primary channel at index 0 can share locations automatically */
        val primary = settingsList[0]
        if (primary.moduleSettings.positionPrecision > 0) {
            output = 0
        }
    }
    return output
}

@Preview(showBackground = true)
@Composable
private fun ChannelSettingsPreview() {
    ChannelSettingsItemList(
        settingsList =
        listOf(
            channelSettings {
                psk = Channel.default.settings.psk
                name = Channel.default.name
            },
            channelSettings { name = stringResource(R.string.channel_name) },
        ),
        loraConfig = Channel.default.loraConfig,
        firmwareVersion = "1.3.2",
        enabled = true,
        onPositiveClicked = {},
    )
}
