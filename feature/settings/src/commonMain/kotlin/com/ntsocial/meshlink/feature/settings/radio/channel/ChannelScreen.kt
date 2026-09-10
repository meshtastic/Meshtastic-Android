/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.feature.settings.radio.channel

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.model.Channel
import com.ntsocial.meshlink.core.model.ConnectionState
import com.ntsocial.meshlink.core.model.util.getChannelUrl
import com.ntsocial.meshlink.core.navigation.Route
import com.ntsocial.meshlink.core.repository.ChannelReliabilityResult
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.add
import com.ntsocial.meshlink.core.resources.apply
import com.ntsocial.meshlink.core.resources.are_you_sure_change_default
import com.ntsocial.meshlink.core.resources.cancel
import com.ntsocial.meshlink.core.resources.channel_apply_verified
import com.ntsocial.meshlink.core.resources.channel_invalid
import com.ntsocial.meshlink.core.resources.channel_protection_disabled
import com.ntsocial.meshlink.core.resources.channel_protection_save
import com.ntsocial.meshlink.core.resources.channel_protection_saved
import com.ntsocial.meshlink.core.resources.channel_protection_stop
import com.ntsocial.meshlink.core.resources.channel_protection_summary
import com.ntsocial.meshlink.core.resources.channel_protection_title
import com.ntsocial.meshlink.core.resources.channel_protection_update
import com.ntsocial.meshlink.core.resources.edit
import com.ntsocial.meshlink.core.resources.generate_qr_code
import com.ntsocial.meshlink.core.resources.modem_preset
import com.ntsocial.meshlink.core.resources.navigate_into_label
import com.ntsocial.meshlink.core.resources.replace
import com.ntsocial.meshlink.core.resources.reset
import com.ntsocial.meshlink.core.resources.reset_to_defaults
import com.ntsocial.meshlink.core.resources.scan_channels_qr
import com.ntsocial.meshlink.core.resources.share_channels_qr
import com.ntsocial.meshlink.core.ui.component.AdaptiveTwoPane
import com.ntsocial.meshlink.core.ui.component.ChannelApplyStatus
import com.ntsocial.meshlink.core.ui.component.ChannelApplyUiState
import com.ntsocial.meshlink.core.ui.component.ChannelSelection
import com.ntsocial.meshlink.core.ui.component.MainAppBar
import com.ntsocial.meshlink.core.ui.component.MeshtasticDialog
import com.ntsocial.meshlink.core.ui.component.PreferenceFooter
import com.ntsocial.meshlink.core.ui.component.QrDialog
import com.ntsocial.meshlink.core.ui.icon.ChevronRight
import com.ntsocial.meshlink.core.ui.icon.MeshtasticIcons
import com.ntsocial.meshlink.core.ui.icon.QrCode
import com.ntsocial.meshlink.core.ui.icon.QrCodeScanner
import com.ntsocial.meshlink.core.ui.qr.ScannedQrCodeDialog
import com.ntsocial.meshlink.core.ui.util.ChannelsOnlyBarcodeScanner
import com.ntsocial.meshlink.core.ui.util.LocalBarcodeScannerProvider
import com.ntsocial.meshlink.core.ui.util.LocalBarcodeScannerSupported
import com.ntsocial.meshlink.core.ui.util.SnackbarManager
import com.ntsocial.meshlink.core.ui.util.rememberQrCodePainter
import com.ntsocial.meshlink.core.ui.util.rememberShowToastResource
import com.ntsocial.meshlink.feature.settings.channel.ChannelViewModel
import com.ntsocial.meshlink.feature.settings.navigation.ConfigRoute
import com.ntsocial.meshlink.feature.settings.navigation.getNavRouteFrom
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel
import com.ntsocial.meshlink.feature.settings.radio.component.PacketResponseStateDialog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

/**
 * Composable screen for managing and sharing Meshtastic channels. Allows users to view, edit, and share channel
 * configurations via QR codes or URLs.
 */
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun ChannelScreen(
    onNavigate: (Route) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = koinViewModel(),
    radioConfigViewModel: RadioConfigViewModel = koinViewModel(),
) {
    val focusManager = LocalFocusManager.current

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val radioConfigState by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()

    val enabled = connectionState == ConnectionState.Connected && !viewModel.isManaged

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val protectionEnabled by viewModel.isChannelProtectionEnabled.collectAsStateWithLifecycle()
    val channelOperationResult by viewModel.channelOperationResult.collectAsStateWithLifecycle()
    val channelApplyState by viewModel.channelApplyState.collectAsStateWithLifecycle()
    var channelSet by remember(channels) { mutableStateOf(channels) }
    val modemPresetName by
        remember(channels) { mutableStateOf(Channel(loraConfig = channels.lora_config ?: Config.LoRaConfig()).name) }

    var showResetDialog by rememberSaveable { mutableStateOf(false) }

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

    val showToast = rememberShowToastResource()
    val snackbarManager = koinInject<SnackbarManager>()
    val invalidChannelMessage = stringResource(Res.string.channel_invalid)
    val barcodeScanner =
        LocalBarcodeScannerProvider.current { contents ->
            contents?.let { url ->
                viewModel.requestChannelUrl(url) { snackbarManager.showSnackbar(invalidChannelMessage) }
            }
        }

    val channelsOnlyScanner = barcodeScanner as? ChannelsOnlyBarcodeScanner
    val isBarcodeScannerSupported = LocalBarcodeScannerSupported.current || channelsOnlyScanner?.isSupported == true

    LaunchedEffect(channelOperationResult) {
        val result = channelOperationResult ?: return@LaunchedEffect
        when (result) {
            ChannelReliabilityResult.PROTECTED -> showToast(Res.string.channel_protection_saved)
            ChannelReliabilityResult.PROTECTION_DISABLED -> showToast(Res.string.channel_protection_disabled)
            else -> Unit
        }
        viewModel.clearChannelOperationResult()
    }

    LaunchedEffect(channelApplyState) {
        when (channelApplyState) {
            ChannelApplyUiState.Verified -> {
                showToast(Res.string.channel_apply_verified)
                viewModel.clearChannelApplyState()
            }

            is ChannelApplyUiState.Failed -> viewModel.clearChannelApplyState()

            else -> Unit
        }
    }

    // Send new channel settings to the device
    @Suppress("TooGenericExceptionCaught")
    fun installSettings(newChannelSet: ChannelSet) {
        viewModel.setChannels(newChannelSet)
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

    var showShareDialog by rememberSaveable { mutableStateOf(false) }

    if (showShareDialog) {
        ChannelShareDialog(
            channelSet = selectedChannelSet,
            shouldAddChannel = shouldAddChannelsState,
            onDismiss = { showShareDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (isBarcodeScannerSupported) {
                        IconButton(
                            onClick = {
                                if (channelsOnlyScanner != null) {
                                    channelsOnlyScanner.startChannelScan()
                                } else {
                                    barcodeScanner.startScan()
                                }
                            },
                            enabled = enabled,
                        ) {
                            Icon(
                                imageVector = MeshtasticIcons.QrCodeScanner,
                                contentDescription = stringResource(Res.string.scan_channels_qr),
                            )
                        }
                    }
                },
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
            if (
                channelApplyState == ChannelApplyUiState.Applying ||
                channelApplyState == ChannelApplyUiState.WaitingForReconnect ||
                channelApplyState == ChannelApplyUiState.InvalidSettings
            ) {
                item { ChannelApplyStatus(state = channelApplyState, modifier = Modifier.padding(bottom = 12.dp)) }
            }
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
                ChannelProtectionControls(
                    enabled = enabled,
                    protected = protectionEnabled,
                    onSave = viewModel::protectCurrentChannels,
                    onDisable = viewModel::disableChannelProtection,
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
private fun ChannelProtectionControls(enabled: Boolean, protected: Boolean, onSave: () -> Unit, onDisable: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = stringResource(Res.string.channel_protection_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(Res.string.channel_protection_summary),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        OutlinedButton(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (protected) Res.string.channel_protection_update else Res.string.channel_protection_save,
                ),
            )
        }
        if (protected) {
            TextButton(onClick = onDisable, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.channel_protection_stop))
            }
        }
    }
}

private const val QR_CODE_SIZE = 960

@Composable
private fun ChannelShareDialog(channelSet: ChannelSet, shouldAddChannel: Boolean, onDismiss: () -> Unit) {
    val commonUri = channelSet.getChannelUrl(false, shouldAddChannel)
    val uriString = commonUri.toString()
    val qrPainter = rememberQrCodePainter(uriString, QR_CODE_SIZE)
    QrDialog(
        title = stringResource(Res.string.share_channels_qr),
        uriString = uriString,
        qrPainter = qrPainter,
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
                    Icon(imageVector = MeshtasticIcons.QrCode, contentDescription = null)
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
            imageVector = MeshtasticIcons.ChevronRight,
            contentDescription = stringResource(Res.string.navigate_into_label),
            modifier = Modifier.padding(end = 16.dp),
        )
    }
}
