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
package com.geeksville.mesh.ui.sharing

import android.os.RemoteException
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.twotone.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.util.getChannelUrl
import org.meshtastic.core.model.util.qrCode
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.add
import org.meshtastic.core.strings.apply
import org.meshtastic.core.strings.are_you_sure_change_default
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.cant_change_no_radio
import org.meshtastic.core.strings.edit
import org.meshtastic.core.strings.generate_qr_code
import org.meshtastic.core.strings.modem_preset
import org.meshtastic.core.strings.navigate_into_label
import org.meshtastic.core.strings.replace
import org.meshtastic.core.strings.reset
import org.meshtastic.core.strings.reset_to_defaults
import org.meshtastic.core.strings.share_channels_qr
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.ChannelSelection
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.component.PreferenceFooter
import org.meshtastic.core.ui.component.QrDialog
import org.meshtastic.core.ui.qr.ScannedQrCodeDialog
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.getNavRouteFrom
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.component.PacketResponseStateDialog
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

/**
 * Composable screen for managing and sharing Meshtastic channels. Allows users to view, edit, and share channel
 * configurations via QR codes or URLs.
 */
@Composable
@Suppress("LongMethod")
fun ChannelScreen(
    viewModel: ChannelViewModel = hiltViewModel(),
    radioConfigViewModel: RadioConfigViewModel = hiltViewModel(),
    onNavigate: (Route) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val radioConfigState by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()

    val enabled = connectionState == ConnectionState.Connected && !viewModel.isManaged

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    var channelSet by remember(channels) { mutableStateOf(channels) }
    val modemPresetName by
        remember(channels) { mutableStateOf(Channel(loraConfig = channels.lora_config ?: Config.LoRaConfig()).name) }

    var showResetDialog by remember { mutableStateOf(false) }

    var shouldAddChannelsState by remember { mutableStateOf(true) }

    val requestChannelSet by viewModel.requestChannelSet.collectAsStateWithLifecycle()

    /* Animate waiting for the configurations */
    var isWaiting by remember { mutableStateOf(false) }
    if (isWaiting) {
        PacketResponseStateDialog(
            state = radioConfigState.responseState,
            onDismiss = {
                isWaiting = false
                radioConfigViewModel.clearPacketResponse()
            },
            onComplete = {
                getNavRouteFrom(radioConfigState.route)?.let { route ->
                    isWaiting = false
                    radioConfigViewModel.clearPacketResponse()
                    onNavigate(route)
                }
            },
        )
    }

    /* Holds selections made by the user for QR generation. */
    val channelSelections =
        rememberSaveable(
            saver =
            listSaver<SnapshotStateList<Boolean>, Boolean>(
                save = { it.toList() },
                restore = { it.toMutableStateList() },
            ),
        ) {
            mutableStateListOf(true, true, true, true, true, true, true, true)
        }

    val selectedChannelSet =
        channelSet.copy(settings = channelSet.settings.filterIndexed { i, _ -> channelSelections.getOrNull(i) == true })

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Send new channel settings to the device
    fun installSettings(newChannelSet: ChannelSet) {
        // Try to change the radio, if it fails, tell the user why and throw away their edits
        try {
            viewModel.setChannels(newChannelSet)
            // Since we are writing to DeviceConfig, that will trigger the rest of the GUI update (QR code etc)
        } catch (ex: RemoteException) {
            Logger.e(ex) { "ignoring channel problem" }

            channelSet = channels // Throw away user edits

            // Tell the user to try again
            scope.launch { context.showToast(Res.string.cant_change_no_radio) }
        }
    }

    fun installSettings(newChannel: ChannelSettings, newLoRaConfig: Config.LoRaConfig) {
        val newSet = ChannelSet(settings = listOf(newChannel), lora_config = newLoRaConfig)
        installSettings(newSet)
    }

    if (showResetDialog) {
        MeshtasticDialog(
            onDismiss = {
                channelSet = channels // throw away any edits
                showResetDialog = false
            },
            titleRes = Res.string.reset_to_defaults,
            messageRes = Res.string.are_you_sure_change_default,
            onConfirm = {
                Logger.d { "Switching back to default channel" }
                val lora =
                    (Channel.default.loraConfig).copy(region = viewModel.region, tx_enabled = viewModel.txEnabled)
                installSettings(Channel.default.settings, lora)
                showResetDialog = false
            },
            confirmTextRes = Res.string.apply,
            dismissTextRes = Res.string.cancel,
        )
    }

    requestChannelSet?.let { ScannedQrCodeDialog(it, onDismiss = { viewModel.clearRequestChannelUrl() }) }

    var showShareDialog by remember { mutableStateOf(false) }

    if (showShareDialog) {
        ChannelShareDialog(
            channelSet = selectedChannelSet,
            shouldAddChannel = shouldAddChannelsState,
            onDismiss = { showShareDialog = false },
        )
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            item {
                ChannelListView(
                    enabled = enabled,
                    channelSet = channelSet,
                    modemPresetName = modemPresetName,
                    channelSelections = channelSelections,
                    onClickEdit = {
                        isWaiting = true
                        radioConfigViewModel.setResponseStateLoading(ConfigRoute.CHANNELS)
                    },
                    onClickShare = { showShareDialog = true },
                )
            }
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    SegmentedButton(
                        label = { Text(text = stringResource(Res.string.replace)) },
                        onClick = { shouldAddChannelsState = false },
                        selected = !shouldAddChannelsState,
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    )
                    SegmentedButton(
                        label = { Text(text = stringResource(Res.string.add)) },
                        onClick = { shouldAddChannelsState = true },
                        selected = shouldAddChannelsState,
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    )
                }
            }
            item {
                ModemPresetInfo(
                    modemPresetName = modemPresetName,
                    onClick = {
                        isWaiting = true
                        radioConfigViewModel.setResponseStateLoading(ConfigRoute.LORA)
                    },
                )
            }
            item {
                PreferenceFooter(
                    modifier = Modifier,
                    enabled = enabled,
                    negativeText = stringResource(Res.string.reset),
                    onNegativeClicked = {
                        focusManager.clearFocus()
                        showResetDialog = true
                    },
                    positiveText = null,
                    onPositiveClicked = {},
                )
            }
        }
    }
}

@Composable
private fun ChannelShareDialog(channelSet: ChannelSet, shouldAddChannel: Boolean, onDismiss: () -> Unit) {
    val url = channelSet.getChannelUrl(shouldAddChannel)
    QrDialog(
        title = stringResource(Res.string.share_channels_qr),
        uri = url,
        qrCode = channelSet.qrCode(shouldAddChannel),
        onDismiss = onDismiss,
    )
}

@Composable
private fun ChannelListView(
    enabled: Boolean,
    channelSet: ChannelSet,
    modemPresetName: String,
    channelSelections: SnapshotStateList<Boolean>,
    onClickEdit: () -> Unit = {},
    onClickShare: () -> Unit = {},
) {
    val selectedChannelSet =
        channelSet.copy(settings = channelSet.settings.filterIndexed { i, _ -> channelSelections.getOrNull(i) == true })

    AdaptiveTwoPane(
        first = {
            channelSet.settings.forEachIndexed { index, channel ->
                val channelObj = Channel(channel, channelSet.lora_config ?: Config.LoRaConfig())
                val displayTitle = if (channel.name.isEmpty()) modemPresetName else channel.name

                ChannelSelection(
                    index = index,
                    title = displayTitle,
                    enabled = enabled,
                    isSelected = channelSelections[index],
                    onSelected = {
                        if (it || selectedChannelSet.settings.size > 1) {
                            channelSelections[index] = it
                        }
                    },
                    channel = channelObj,
                )
            }
            OutlinedButton(
                onClick = onClickEdit,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Text(text = stringResource(Res.string.edit))
            }
        },
        second = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = onClickShare, modifier = Modifier.padding(16.dp), enabled = enabled) {
                    Icon(imageVector = Icons.TwoTone.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(Res.string.generate_qr_code))
                }
            }
        },
    )
}

@Composable
private fun ModemPresetInfo(modemPresetName: String, onClick: () -> Unit) {
    Row(
        modifier =
        Modifier.padding(top = 12.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
            Text(text = stringResource(Res.string.modem_preset), fontSize = 16.sp)
            Text(text = modemPresetName, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = stringResource(Res.string.navigate_into_label),
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ModemPresetInfoPreview() {
    ModemPresetInfo(modemPresetName = "Long Fast", onClick = {})
}

@PreviewScreenSizes
@Composable
private fun ChannelScreenPreview() {
    ChannelListView(
        enabled = true,
        channelSet = ChannelSet(settings = listOf(Channel.default.settings), lora_config = Channel.default.loraConfig),
        modemPresetName = Channel.default.name,
        channelSelections = listOf(true).toMutableStateList(),
    )
}
