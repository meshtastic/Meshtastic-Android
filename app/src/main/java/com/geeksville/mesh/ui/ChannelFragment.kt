package com.geeksville.mesh.ui

import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
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
import com.geeksville.mesh.android.Logging
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
import com.geeksville.mesh.ui.components.AdaptiveTwoPane
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.config.ChannelCard
import com.geeksville.mesh.ui.components.config.ChannelSelection
import com.geeksville.mesh.ui.components.config.EditChannelDialog
import com.geeksville.mesh.ui.components.dragContainer
import com.geeksville.mesh.ui.components.dragDropItemsIndexed
import com.geeksville.mesh.ui.components.rememberDragDropState
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChannelFragment : ScreenFragment("Channel"), Logging {

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppCompatTheme {
                    ChannelScreen(model)
                }
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun ChannelScreen(
    viewModel: UIViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val enabled = connectionState == MeshService.ConnectionState.CONNECTED && !viewModel.isManaged

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    var channelSet by remember(channels) { mutableStateOf(channels) }
    var showChannelEditor by rememberSaveable { mutableStateOf(false) }
    val isEditing = channelSet != channels || showChannelEditor

    /* Holds selections made by the user for QR generation. */
    val channelSelections = rememberSaveable(
        saver = listSaver(
            save = { stateList -> stateList.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf(elements = Array(size = 8, init = { true })) }

    val channelUrl = channelSet.getChannelUrl()
    val modemPresetName = Channel(loraConfig = channelSet.loraConfig).name

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            try {
                viewModel.requestChannelSet(Uri.parse(result.contents).toChannelSet())
            } catch (ex: Throwable) {
                errormsg("Channel url error: ${ex.message}")
                viewModel.showSnackbar(R.string.channel_invalid)
            }
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

    fun requestPermissionAndScan() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.camera_required)
            .setMessage(R.string.why_camera_required)
            .setNeutralButton(R.string.cancel) { _, _ ->
                debug("Camera permission denied")
            }
            .setPositiveButton(R.string.accept) { _, _ ->
                requestPermissionAndScanLauncher.launch(context.getCameraPermissions())
            }
            .show()
    }

    // Send new channel settings to the device
    fun installSettings(
        newChannelSet: ChannelSet
    ) {
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

    fun resetButton() {
        // User just locked it, we should warn and then apply changes to radio
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.reset_to_defaults)
            .setMessage(R.string.are_you_sure_change_default)
            .setNeutralButton(R.string.cancel) { _, _ ->
                channelSet = channels // throw away any edits
            }
            .setPositiveButton(R.string.apply) { _, _ ->
                debug("Switching back to default channel")
                installSettings(
                    Channel.default.settings,
                    Channel.default.loraConfig.copy {
                        region = viewModel.region
                        txEnabled = viewModel.txEnabled
                    }
                )
            }
            .show()
    }

    fun sendButton() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.change_channel)
            .setMessage(R.string.are_you_sure_channel)
            .setNeutralButton(R.string.cancel) { _, _ ->
                showChannelEditor = false
                channelSet = channels
            }
            .setPositiveButton(R.string.accept) { _, _ ->
                installSettings(channelSet)
            }
            .show()
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
                    if (settingsCount > index) channelSet = copy { settings[index] = it }
                    else channelSet = copy { settings.add(it) }
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
            }
        } else {
            dragDropItemsIndexed(
                items = channelSet.settingsList,
                dragDropState = dragDropState,
            ) { index, channel, isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 4.dp, label = "drag")
                ChannelCard(
                    elevation = elevation,
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
                    colors = ButtonDefaults.buttonColors(
                        disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                    )
                ) { Text(text = stringResource(R.string.add)) }
            }
        }

        item {
            var valueState by remember(channelUrl) { mutableStateOf(channelUrl) }
            val isError = valueState != channelUrl

            OutlinedTextField(
                value = valueState.toString(),
                onValueChange = {
                    try {
                        valueState = Uri.parse(it)
                        channelSet = valueState.toChannelSet()
                    } catch (ex: Throwable) {
                        // channelSet failed to update, isError true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text("URL") },
                isError = isError,
                trailingIcon = {
                    val isUrlEqual = channelUrl == channels.getChannelUrl()
                    IconButton(onClick = {
                        when {
                            isError -> valueState = channelUrl
                            !isUrlEqual -> viewModel.requestChannelSet(channels)
                            else -> {
                                // track how many times users share channels
                                GeeksvilleApplication.analytics.track(
                                    "share",
                                    DataPair("content_type", "channel")
                                )
                                clipboardManager.setText(AnnotatedString(channelUrl.toString()))
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
                                isError -> "Error"
                                !isUrlEqual -> stringResource(R.string.send)
                                else -> "Copy"
                            },
                            tint = if (isError) {
                                MaterialTheme.colors.error
                            } else {
                                LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
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

        item {
            DropDownPreference(title = stringResource(id = R.string.channel_options),
                enabled = enabled,
                items = ChannelOption.entries
                    .map { it.modemPreset to stringResource(it.configRes) },
                selectedItem = channelSet.loraConfig.modemPreset,
                onItemSelected = {
                    val lora = channelSet.loraConfig.copy { modemPreset = it }
                    channelSet = channelSet.copy { loraConfig = lora }
                })
        }

        if (isEditing) item {
            PreferenceFooter(
                enabled = enabled,
                onCancelClicked = {
                    focusManager.clearFocus()
                    showChannelEditor = false
                    channelSet = channels
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    // viewModel.setRequestChannelUrl(channelUrl)
                    sendButton()
                })
        } else {
            item {
                PreferenceFooter(
                    enabled = enabled,
                    negativeText = R.string.reset,
                    onNegativeClicked = {
                        focusManager.clearFocus()
                        resetButton()
                    },
                    positiveText = R.string.scan,
                    onPositiveClicked = {
                        focusManager.clearFocus()
                        // viewModel.setRequestChannelUrl(channelUrl)
                        if (context.hasCameraPermission()) zxingScan() else requestPermissionAndScan()
                    })
            }
        }
    }
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
    alpha = if (enabled) 1.0f else ContentAlpha.disabled,
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
                    contentColor = MaterialTheme.colors.onSurface,
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
