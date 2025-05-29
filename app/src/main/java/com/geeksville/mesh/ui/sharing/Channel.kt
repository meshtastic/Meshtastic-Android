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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
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
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.getChannelUrl
import com.geeksville.mesh.model.qrCode
import com.geeksville.mesh.model.toChannelSet
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.common.components.AdaptiveTwoPane
import com.geeksville.mesh.ui.common.components.DropDownPreference
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.dragContainer
import com.geeksville.mesh.ui.common.components.dragDropItemsIndexed
import com.geeksville.mesh.ui.common.components.rememberDragDropState
import com.geeksville.mesh.ui.radioconfig.components.ChannelCard
import com.geeksville.mesh.ui.radioconfig.components.ChannelSelection
import com.geeksville.mesh.ui.radioconfig.components.EditChannelDialog
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ChannelScreen(
    viewModel: UIViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val enabled = connectionState == MeshService.ConnectionState.CONNECTED && !viewModel.isManaged

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    var channelSet by remember(channels) { mutableStateOf(channels) }
    var showChannelEditor by rememberSaveable { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }
    val isEditing = channelSet != channels || showChannelEditor

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
    val modemPresetName = Channel(loraConfig = channelSet.loraConfig).name

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.requestChannelUrl(result.contents.toUri())
        }
    }

    fun updateSettingsList(update: MutableList<ChannelProtos.ChannelSettings>.() -> Unit) {
        try {
            val list = channelSet.settingsList.toMutableList()
            list.update()
            channelSet = channelSet.copy {
                settings.clear()
                settings.addAll(list)
            }
        } catch (ex: Exception) {
            errormsg("Error updating ChannelSettings list:", ex)
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
        } finally {
            showChannelEditor = false
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

    if (showSendDialog) {
        AlertDialog(
            onDismissRequest = {
                showSendDialog = false
                showChannelEditor = false
                channelSet = channels
            },
            title = { Text(text = stringResource(id = R.string.change_channel)) },
            text = { Text(text = stringResource(id = R.string.are_you_sure_channel)) },
            confirmButton = {
                TextButton(onClick = {
                    installSettings(channelSet)
                    showSendDialog = false
                }) { Text(text = stringResource(id = R.string.accept)) }
                installSettings(channelSet)
            }
        )
    }

    var showEditChannelDialog: Int? by remember { mutableStateOf(null) }

    if (showEditChannelDialog != null) {
        val index = showEditChannelDialog ?: return
        EditChannelDialog(
            channelSettings = with(channelSet) {
                if (settingsCount > index) getSettings(index) else channelSettings { }
            },
            modemPresetName = modemPresetName,
            onAddClick = {
                with(channelSet) {
                    if (settingsCount > index) {
                        channelSet = copy { settings[index] = it }
                    } else {
                        channelSet = copy { settings.add(it) }
                    }
                }
                showEditChannelDialog = null
            },
            onDismissRequest = { showEditChannelDialog = null }
        )
    }

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { fromIndex, toIndex ->
        updateSettingsList { add(toIndex, removeAt(fromIndex)) }
    }

    LazyColumn(
        modifier = Modifier.dragContainer(
            dragDropState = dragDropState,
            haptics = LocalHapticFeedback.current,
        ),
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        if (!showChannelEditor) {
            item {
                ChannelListView(
                    enabled = enabled,
                    channelSet = channelSet,
                    modemPresetName = modemPresetName,
                    channelSelections = channelSelections,
                    onClick = { showChannelEditor = true }
                )
                EditChannelUrl(
                    enabled = enabled,
                    channelUrl = selectedChannelSet.getChannelUrl(),
                    onConfirm = viewModel::requestChannelUrl
                )
            }
        } else {
            dragDropItemsIndexed(
                items = channelSet.settingsList,
                dragDropState = dragDropState,
            ) { index, channel, isDragging ->
                ChannelCard(
                    index = index,
                    title = channel.name.ifEmpty { modemPresetName },
                    enabled = enabled,
                    onEditClick = { showEditChannelDialog = index },
                    onDeleteClick = { updateSettingsList { removeAt(index) } }
                )
            }
            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        channelSet = channelSet.copy {
                            settings.add(channelSettings { psk = Channel.default.settings.psk })
                        }
                        showEditChannelDialog = channelSet.settingsList.lastIndex
                    },
                    enabled = enabled && viewModel.maxChannels > channelSet.settingsCount,
                ) { Text(text = stringResource(R.string.add)) }
            }
        }

        item {
            DropDownPreference(
                title = stringResource(id = R.string.channel_options),
                enabled = enabled,
                items = ChannelOption.entries
                    .map { it.modemPreset to stringResource(it.configRes) },
                selectedItem = channelSet.loraConfig.modemPreset,
                onItemSelected = {
                    val lora = channelSet.loraConfig.copy { modemPreset = it }
                    channelSet = channelSet.copy { loraConfig = lora }
                }
            )
        }

        item {
            if (isEditing) {
                PreferenceFooter(
                    enabled = enabled,
                    onCancelClicked = {
                        focusManager.clearFocus()
                        showChannelEditor = false
                        channelSet = channels
                    },
                    onSaveClicked = {
                        focusManager.clearFocus()
                        showSendDialog = true
                    }
                )
            } else {
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
                ChannelSelection(
                    index = index,
                    title = channel.name.ifEmpty { modemPresetName },
                    enabled = enabled,
                    isSelected = channelSelections[index],
                    onSelected = {
                        if (it || selectedChannelSet.settingsCount > 1) {
                            channelSelections[index] = it
                        }
                    },
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
