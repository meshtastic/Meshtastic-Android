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

package com.geeksville.mesh.ui.sharing

import android.content.ClipData
import android.net.Uri
import android.os.RemoteException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.AppOnlyProtos.ChannelSet
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.analytics.DataPair
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.getCameraPermissions
import com.geeksville.mesh.android.hasCameraPermission
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.getChannelUrl
import com.geeksville.mesh.model.qrCode
import com.geeksville.mesh.model.toChannelSet
import com.geeksville.mesh.navigation.ConfigRoute
import com.geeksville.mesh.navigation.Route
import com.geeksville.mesh.navigation.getNavRouteFrom
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel
import com.geeksville.mesh.ui.common.components.AdaptiveTwoPane
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.radioconfig.components.ChannelSelection
import com.geeksville.mesh.ui.radioconfig.components.PacketResponseStateDialog
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ChannelScreen(
    viewModel: UIViewModel = hiltViewModel(),
    radioConfigViewModel: RadioConfigViewModel = hiltViewModel(),
    onNavigate: (Route) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val radioConfigState by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()

    val enabled = connectionState == MeshService.ConnectionState.CONNECTED && !viewModel.isManaged

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    var channelSet by remember(channels) { mutableStateOf(channels) }
    val modemPresetName by remember(channels) {
        mutableStateOf(Channel(loraConfig = channels.loraConfig).name)
    }

    var showResetDialog by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }

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
    val channelSelections = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf(elements = Array(size = 8, init = { true })) }

    val selectedChannelSet = channelSet.copy {
        val result = settings.filterIndexed { i, _ -> channelSelections.getOrNull(i) == true }
        settings.clear()
        settings.addAll(result)
    }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.requestChannelUrl(result.contents.toUri())
        }
    }

    fun zxingScan() {
        debug("Starting zxing QR code scanner")
        val zxingScan = ScanOptions()
        zxingScan.setCameraId(0)
        zxingScan.setPrompt("")
        zxingScan.setBeepEnabled(false)
        zxingScan.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        barcodeLauncher.launch(zxingScan)
    }

    val requestPermissionAndScanLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.all { it.value }) zxingScan()
        }

    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = {
                debug("Camera permission denied")
                showScanDialog = false
            },
            title = { Text(text = stringResource(id = R.string.camera_required)) },
            text = { Text(text = stringResource(id = R.string.why_camera_required)) },
            confirmButton = {
                TextButton(onClick = { requestPermissionAndScanLauncher.launch(context.getCameraPermissions()) }) {
                    Text(text = stringResource(id = R.string.accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { debug("Camera permission denied") }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // Send new channel settings to the device
    fun installSettings(newChannelSet: ChannelSet) {
        // Try to change the radio, if it fails, tell the user why and throw away their edits
        try {
            viewModel.setChannels(newChannelSet)
            // Since we are writing to DeviceConfig, that will trigger the rest of the GUI update (QR code etc)
        } catch (ex: RemoteException) {
            errormsg("ignoring channel problem", ex)

            channelSet = channels // Throw away user edits

            // Tell the user to try again
            viewModel.showSnackbar(R.string.cant_change_no_radio)
        }
    }

    fun installSettings(
        newChannel: ChannelProtos.ChannelSettings,
        newLoRaConfig: ConfigProtos.Config.LoRaConfig
    ) {
        val newSet = channelSet {
            settings.add(newChannel)
            loraConfig = newLoRaConfig
        }
        installSettings(newSet)
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = {
                channelSet = channels // throw away any edits
                showResetDialog = false
            },
            title = { Text(text = stringResource(id = R.string.reset_to_defaults)) },
            text = { Text(text = stringResource(id = R.string.are_you_sure_change_default)) },
            confirmButton = {
                TextButton(onClick = {
                    debug("Switching back to default channel")
                    installSettings(
                        Channel.default.settings,
                        Channel.default.loraConfig.copy {
                            region = viewModel.region
                            txEnabled = viewModel.txEnabled
                        }
                    )
                    showResetDialog = false
                }) { Text(text = stringResource(id = R.string.apply)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    channelSet = channels // throw away any edits
                    showResetDialog = false
                }) { Text(text = stringResource(id = R.string.cancel)) }
            }
        )
    }

    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        item {
            ChannelListView(
                enabled = enabled,
                channelSet = channelSet,
                modemPresetName = modemPresetName,
                channelSelections = channelSelections,
                onClick = {
                    isWaiting = true
                    radioConfigViewModel.setResponseStateLoading(ConfigRoute.CHANNELS)
                }
            )
            EditChannelUrl(
                enabled = enabled,
                channelUrl = selectedChannelSet.getChannelUrl(),
                onConfirm = viewModel::requestChannelUrl
            )
        }
        item {
           ModemPresetInfo(
               modemPresetName = modemPresetName,
               onClick = {
                   isWaiting = true
                   radioConfigViewModel.setResponseStateLoading(ConfigRoute.LORA)
               }
           )
        }
        item {
            PreferenceFooter(
                enabled = enabled,
                negativeText = R.string.reset,
                onNegativeClicked = {
                    focusManager.clearFocus()
                    showResetDialog = true
                },
                positiveText = R.string.scan,
                onPositiveClicked = {
                    focusManager.clearFocus()
                    if (context.hasCameraPermission()) zxingScan() else showScanDialog = true
                }
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun EditChannelUrl(
    enabled: Boolean,
    channelUrl: Uri,
    modifier: Modifier = Modifier,
    onConfirm: (Uri) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    var valueState by remember(channelUrl) { mutableStateOf(channelUrl) }
    var isError by remember { mutableStateOf(false) }

    // Trigger dialog automatically when users paste a new valid URL
    LaunchedEffect(valueState, isError) {
        if (!isError && valueState != channelUrl) {
            onConfirm(valueState)
        }
    }

    OutlinedTextField(
        value = valueState.toString(),
        onValueChange = {
            isError = runCatching {
                valueState = it.toUri()
                valueState.toChannelSet()
            }.isFailure
        },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.url)) },
        isError = isError,
        shape = RoundedCornerShape(8.dp),
        trailingIcon = {
            val label = stringResource(R.string.url)
            val isUrlEqual = valueState == channelUrl
            IconButton(onClick = {
                when {
                    isError -> {
                        isError = false
                        valueState = channelUrl
                    }

                    !isUrlEqual -> {
                        onConfirm(valueState)
                        valueState = channelUrl
                    }

                    else -> {
                        // track how many times users share channels
                        GeeksvilleApplication.analytics.track(
                            "share", DataPair("content_type", "channel")
                        )
                        coroutineScope.launch {
                            clipboardManager.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        label,
                                        valueState.toString()
                                    )
                                )
                            )
                        }
                    }
                }
            }) {
                Icon(
                    imageVector = when {
                        isError -> Icons.TwoTone.Close
                        !isUrlEqual -> Icons.TwoTone.Check
                        else -> Icons.TwoTone.ContentCopy
                    },
                    contentDescription = when {
                        isError -> stringResource(R.string.copy)
                        !isUrlEqual -> stringResource(R.string.send)
                        else -> stringResource(R.string.copy)
                    },
                    tint = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        LocalContentColor.current
                    }
                )
            }
        },
        maxLines = 1,
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

@Composable
private fun QrCodeImage(
    enabled: Boolean,
    channelSet: ChannelSet,
    modifier: Modifier = Modifier,
) = Image(
    painter = channelSet.qrCode
        ?.let { BitmapPainter(it.asImageBitmap()) }
        ?: painterResource(id = R.drawable.qrcode),
    contentDescription = stringResource(R.string.qr_code),
    modifier = modifier,
    contentScale = ContentScale.Inside,
    alpha = if (enabled) 1.0f else 0.7f
    // colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
)

@Composable
private fun ChannelListView(
    enabled: Boolean,
    channelSet: ChannelSet,
    modemPresetName: String,
    channelSelections: SnapshotStateList<Boolean>,
    onClick: () -> Unit = {},
) {
    val selectedChannelSet = channelSet.copy {
        val result = settings.filterIndexed { i, _ -> channelSelections.getOrNull(i) == true }
        settings.clear()
        settings.addAll(result)
    }

    AdaptiveTwoPane(
        first = {
            channelSet.settingsList.forEachIndexed { index, channel ->
                val channelObj = Channel(channel, channelSet.loraConfig)
                val displayTitle = channel.name.ifEmpty { modemPresetName }

                ChannelSelection(
                    index = index,
                    title = displayTitle,
                    enabled = enabled,
                    isSelected = channelSelections[index],
                    onSelected = {
                        if (it || selectedChannelSet.settingsCount > 1) {
                            channelSelections[index] = it
                        }
                    },
                    channel = channelObj
                )
            }
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text(text = stringResource(R.string.edit)) }
        },
        second = {
            QrCodeImage(
                enabled = enabled,
                channelSet = selectedChannelSet,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        },
    )
}

@Composable
private fun ModemPresetInfo(
    modemPresetName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                1.dp,
                MaterialTheme.colorScheme.onBackground,
                RoundedCornerShape(8.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.modem_preset),
                fontSize = 16.sp,
            )
            Text(
                text = modemPresetName,
                fontSize = 14.sp,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = stringResource(R.string.navigate_into_label),
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ModemPresetInfoPreview() {
    ModemPresetInfo(
        modemPresetName = "Long Fast",
        onClick = {}
    )
}

@PreviewScreenSizes
@Composable
private fun ChannelScreenPreview() {
    ChannelListView(
        enabled = true,
        channelSet = channelSet {
            settings.add(Channel.default.settings)
            loraConfig = Channel.default.loraConfig
        },
        modemPresetName = Channel.default.name,
        channelSelections = listOf(true).toMutableStateList(),
    )
}
