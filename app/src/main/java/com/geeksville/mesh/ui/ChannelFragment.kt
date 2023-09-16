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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.geeksville.mesh.model.ChannelSet
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.components.ClickableTextField
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.config.ChannelSettingsItemList
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
    val connected = connectionState == MeshService.ConnectionState.CONNECTED
    val enabled = connected && !viewModel.isManaged

    val channels by viewModel.channels.collectAsStateWithLifecycle()
    var channelSet by remember(channels) { mutableStateOf(channels.protobuf) }
    val isEditing = channelSet != channels.protobuf

    val primaryChannel = ChannelSet(channelSet).primaryChannel
    val channelUrl = ChannelSet(channelSet).getChannelUrl()
    val modemPresetName = Channel(loraConfig = channelSet.loraConfig).name

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
        val newSet = ChannelSet(newChannelSet)
        // Try to change the radio, if it fails, tell the user why and throw away their edits
        try {
            viewModel.setChannels(newSet)
            // Since we are writing to DeviceConfig, that will trigger the rest of the GUI update (QR code etc)
        } catch (ex: RemoteException) {
            errormsg("ignoring channel problem", ex)

            channelSet = channels.protobuf // Throw away user edits

            // Tell the user to try again
            showSnackbar(context.getString(R.string.radio_sleeping))
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
                channelSet = channels.protobuf // throw away any edits
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
        primaryChannel?.let { primaryChannel ->
            val humanName = primaryChannel.humanName
            val message = buildString {
                append(context.getString(R.string.are_you_sure_channel))
                if (primaryChannel.settings == Channel.default.settings)
                    append("\n\n" + context.getString(R.string.warning_default_psk, humanName))
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.change_channel)
                .setMessage(message)
                .setNeutralButton(R.string.cancel) { _, _ ->
                    channelSet = channels.protobuf
                }
                .setPositiveButton(R.string.accept) { _, _ ->
                    installSettings(channelSet)
                }
                .show()
        }
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

    var showChannelEditor by remember { mutableStateOf(false) }
    if (showChannelEditor) ChannelSettingsItemList(
        settingsList = channelSet.settingsList,
        modemPresetName = modemPresetName,
        enabled = enabled,
        onNegativeClicked = {
            showChannelEditor = false
        },
        positiveText = R.string.save,
        onPositiveClicked = {
            showChannelEditor = false
            channelSet = channelSet.toBuilder().clearSettings().addAllSettings(it).build()
        }
    )

    if (!showChannelEditor) LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        item {
            ClickableTextField(
                label = R.string.channel_name,
                value = primaryChannel?.humanName.orEmpty(),
                onClick = { showChannelEditor = true },
                enabled = enabled,
                trailingIcon = Icons.TwoTone.Edit,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!isEditing) item {
            Image(
                painter = ChannelSet(channelSet).qrCode?.let { BitmapPainter(it.asImageBitmap()) }
                    ?: painterResource(id = R.drawable.qrcode),
                contentDescription = stringResource(R.string.qr_code),
                contentScale = ContentScale.FillWidth,
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
                        channelSet = ChannelSet(valueState).protobuf
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
                    channelSet = channels.protobuf
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

@Preview(showBackground = true)
@Composable
private fun ChannelScreenPreview() {
    // ChannelScreen()
}
