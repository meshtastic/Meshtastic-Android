package com.geeksville.mesh.ui

import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Chip
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.analytics.DataPair
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.geeksville.mesh.android.getCameraPermissions
import com.geeksville.mesh.android.hasCameraPermission
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.ChannelOption
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.getChannelUrl
import com.geeksville.mesh.model.primaryChannel
import com.geeksville.mesh.model.qrCode
import com.geeksville.mesh.model.toChannelSet
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.components.ClickableTextField
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.config.ChannelCard
import com.geeksville.mesh.ui.components.config.EditChannelDialog
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
                val scope = rememberCoroutineScope()
                val snackbarHostState = remember { SnackbarHostState() }

                AppCompatTheme {
                    Scaffold(
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                        ) {
                            ChannelScreen(model) { text ->
                                scope.launch { snackbarHostState.showSnackbar(text) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelScreen(
    viewModel: UIViewModel = viewModel(),
    showSnackbar: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

    val connectionState by viewModel.connectionState.observeAsState()
    val enabled = connectionState == MeshService.ConnectionState.CONNECTED && !viewModel.isManaged

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    var channelSet by remember(channels) { mutableStateOf(channels) }
    var showChannelEditor by rememberSaveable { mutableStateOf(false) }
    val isEditing = channelSet != channels || showChannelEditor

    val primaryChannel = channelSet.primaryChannel
    val channelUrl = channelSet.getChannelUrl()
    val modemPresetName = Channel(loraConfig = channelSet.loraConfig).name

    /* Holds selections made by the user for QR generation. */
    val channelSelections by rememberSaveable { mutableStateOf(
        MutableList(size = 8, init = { true })
    ) }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.setRequestChannelUrl(Uri.parse(result.contents))
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

    /// Send new channel settings to the device
    fun installSettings(
        newChannelSet: AppOnlyProtos.ChannelSet
    ) {
        // Try to change the radio, if it fails, tell the user why and throw away their edits
        try {
            viewModel.setChannels(newChannelSet)
            // Since we are writing to DeviceConfig, that will trigger the rest of the GUI update (QR code etc)
        } catch (ex: RemoteException) {
            errormsg("ignoring channel problem", ex)

            channelSet = channels // Throw away user edits

            // Tell the user to try again
            showSnackbar(context.getString(R.string.radio_sleeping))
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        if (!showChannelEditor) {
            item {
                ClickableTextField(
                    label = R.string.channel_name,
                    value = primaryChannel?.name.orEmpty(),
                    onClick = { showChannelEditor = true },
                    enabled = enabled,
                    trailingIcon = Icons.TwoTone.Edit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Generate QR Code",
                    style = MaterialTheme.typography.body1,
                    color = if (!enabled) MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled) else Color.Unspecified,
                    )
                Text(
                    text = "The Current LoRa configuration will also be shared.",
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.body1,
                    color = if (!enabled) MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled) else Color.Unspecified,
                )
            }
            itemsIndexed(channelSet.settingsList) { index, channel ->
                ChannelSelection(
                    index = index,
                    title = channel.name.ifEmpty { modemPresetName },
                    enabled = enabled,
                    isSelected = channelSelections[index],
                    onSelected = {
                        channelSelections[index] = it
                    }
                )
            }
        } else {
            itemsIndexed(channelSet.settingsList) { index, channel ->
                ChannelCard(
                    index = index,
                    title = channel.name.ifEmpty { modemPresetName },
                    enabled = enabled,
                    onEditClick = { showEditChannelDialog = index },
                    onDeleteClick = {
                        channelSet = channelSet.copy {
                            settings.clear()
                            settings.addAll(channelSet.settingsList.filterIndexed { i, _ -> i != index })
                        }
                    }
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
                    enabled = viewModel.maxChannels > channelSet.settingsCount,
                    colors = ButtonDefaults.buttonColors(
                        disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                    )
                ) { Text(text = stringResource(R.string.add)) }
            }
        }

        if (!isEditing) item {
            Image(
                painter = channelSet.qrCode?.let { BitmapPainter(it.asImageBitmap()) }
                    ?: painterResource(id = R.drawable.qrcode),
                contentDescription = stringResource(R.string.qr_code),
                contentScale = ContentScale.Inside,
                alpha = if (enabled) 1f else 0.25f,
                // colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp)
            )
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
                            !isUrlEqual -> viewModel.setRequestChannelUrl(channelUrl)
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
                            painter = when {
                                isError -> rememberVectorPainter(Icons.TwoTone.Close)
                                !isUrlEqual -> rememberVectorPainter(Icons.TwoTone.Check)
                                else -> painterResource(R.drawable.ic_twotone_content_copy_24)
                            },
                            contentDescription = when {
                                isError -> "Error"
                                !isUrlEqual -> stringResource(R.string.send)
                                else -> "Copy"
                            },
                            tint = if (isError) MaterialTheme.colors.error
                            else LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
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

/**
 * Enables the user to select what channels are used for QR generation.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ChannelSelection(
    index: Int,
    title: String,
    enabled: Boolean,
    isSelected: Boolean,
    onSelected: (Boolean) -> Unit
) {
    var checked by remember { mutableStateOf(isSelected) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        elevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Chip(onClick = { }) { Text("$index") }
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                color = if (!enabled) MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled) else Color.Unspecified,
                modifier = Modifier.weight(1f)
            )
            Checkbox(
                checked = checked,
                onCheckedChange = {
                    onSelected.invoke(it)
                    checked = it
                }
                // TODO need to trigger regeneration of the qr code
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChannelScreenPreview() {
    // ChannelScreen()
}
