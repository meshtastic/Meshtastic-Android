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
package org.meshtastic.feature.settings.radio.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.Channel
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.add
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.channel_name
import org.meshtastic.core.strings.channels
import org.meshtastic.core.strings.press_and_drag
import org.meshtastic.core.strings.send
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.PreferenceFooter
import org.meshtastic.core.ui.component.dragContainer
import org.meshtastic.core.ui.component.dragDropItemsIndexed
import org.meshtastic.core.ui.component.rememberDragDropState
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.channel.component.ChannelCard
import org.meshtastic.feature.settings.radio.channel.component.ChannelConfigHeader
import org.meshtastic.feature.settings.radio.channel.component.ChannelLegend
import org.meshtastic.feature.settings.radio.channel.component.ChannelLegendDialog
import org.meshtastic.feature.settings.radio.channel.component.EditChannelDialog
import org.meshtastic.feature.settings.radio.component.PacketResponseStateDialog
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

@Composable
fun ChannelConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(state = state.responseState, onDismiss = viewModel::clearPacketResponse)
    }

    ChannelConfigScreen(
        title = stringResource(Res.string.channels),
        onBack = onBack,
        settingsList = state.channelList,
        loraConfig = state.radioConfig.lora ?: Config.LoRaConfig(),
        maxChannels = viewModel.maxChannels,
        firmwareVersion = state.metadata?.firmware_version ?: "0.0.0",
        enabled = state.connected,
        onPositiveClicked = { channelListInput -> viewModel.updateChannels(channelListInput, state.channelList) },
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun ChannelConfigScreen(
    title: String,
    onBack: () -> Unit,
    settingsList: List<ChannelSettings>,
    loraConfig: Config.LoRaConfig,
    maxChannels: Int = 8,
    firmwareVersion: String,
    enabled: Boolean,
    onPositiveClicked: (List<ChannelSettings>) -> Unit,
) {
    val primarySettings = settingsList.getOrNull(0) ?: return
    val modemPresetName by remember(loraConfig) { mutableStateOf(Channel(loraConfig = loraConfig).name) }
    val primaryChannel by remember(loraConfig) { mutableStateOf(Channel(primarySettings, loraConfig)) }
    val capabilities by remember(firmwareVersion) { mutableStateOf(Capabilities(firmwareVersion)) }

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
            channelSettings = with(settingsListInput) { if (size > index) get(index) else ChannelSettings() },
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
        ChannelLegendDialog(capabilities) { showChannelLegendDialog = false }
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = title,
                canNavigateUp = true,
                onNavigateUp = onBack,
                ourNode = null,
                showNodeChip = false,
                actions = {},
                onClickChip = {},
            )
        },
        floatingActionButton = {
            if (maxChannels > settingsListInput.size) {
                FloatingActionButton(
                    onClick = {
                        if (maxChannels > settingsListInput.size) {
                            settingsListInput.add(ChannelSettings(psk = Channel.default.settings.psk))
                            showEditChannelDialog = settingsListInput.lastIndex
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(Icons.TwoTone.Add, stringResource(Res.string.add))
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column {
                ChannelConfigHeader(
                    frequency =
                    if (loraConfig.override_frequency != 0f) {
                        loraConfig.override_frequency
                    } else {
                        primaryChannel.radioFreq
                    },
                    slot =
                    if (loraConfig.channel_num != 0) {
                        loraConfig.channel_num
                    } else {
                        primaryChannel.channelNum
                    },
                )
                Text(
                    text = stringResource(Res.string.press_and_drag),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 16.dp),
                )

                ChannelLegend { showChannelLegendDialog = true }

                val locationChannel = determineLocationSharingChannel(capabilities, settingsListInput.toList())

                LazyColumn(
                    modifier =
                    Modifier.dragContainer(dragDropState = dragDropState, haptics = LocalHapticFeedback.current),
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
                    item { Spacer(modifier = Modifier.weight(1f)) }
                    item {
                        PreferenceFooter(
                            enabled = enabled && isEditing,
                            negativeText = stringResource(Res.string.cancel),
                            onNegativeClicked = {
                                focusManager.clearFocus()
                                settingsListInput.clear()
                                settingsListInput.addAll(settingsList)
                            },
                            positiveText = stringResource(Res.string.send),
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
            ) {}
        }
    }
}

/**
 * Determines what [Channel] if any is enabled to conduct automatic location sharing.
 *
 * @param capabilities of the connected node.
 * @param settingsList Current list of channels on the node.
 * @return the index of the channel within `settingsList`.
 */
private fun determineLocationSharingChannel(capabilities: Capabilities, settingsList: List<ChannelSettings>): Int {
    var output = -1
    if (capabilities.supportsSecondaryChannelLocation) {
        /* Essentially the first index with the setting enabled */
        for ((i, settings) in settingsList.withIndex()) {
            if ((settings.module_settings?.position_precision ?: 0) > 0) {
                output = i
                break
            }
        }
    } else {
        /* Only the primary channel at index 0 can share locations automatically */
        val primary = settingsList[0]
        if ((primary.module_settings?.position_precision ?: 0) > 0) {
            output = 0
        }
    }
    return output
}

@Preview(showBackground = true)
@Composable
private fun ChannelConfigScreenPreview() {
    ChannelConfigScreen(
        title = "Channels",
        onBack = {},
        settingsList =
        listOf(
            ChannelSettings(psk = Channel.default.settings.psk, name = Channel.default.name),
            ChannelSettings(name = stringResource(Res.string.channel_name)),
        ),
        loraConfig = Channel.default.loraConfig,
        firmwareVersion = "1.3.2",
        enabled = true,
        onPositiveClicked = {},
    )
}
