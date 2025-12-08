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

@file:Suppress("TooManyFunctions")
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package org.meshtastic.feature.firmware

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.chirpy
import org.meshtastic.core.strings.dont_show_again_for_device
import org.meshtastic.core.strings.firmware_update_almost_there
import org.meshtastic.core.strings.firmware_update_alpha
import org.meshtastic.core.strings.firmware_update_checking
import org.meshtastic.core.strings.firmware_update_currently_installed
import org.meshtastic.core.strings.firmware_update_device
import org.meshtastic.core.strings.firmware_update_disclaimer_chirpy_says
import org.meshtastic.core.strings.firmware_update_disclaimer_text
import org.meshtastic.core.strings.firmware_update_disclaimer_title
import org.meshtastic.core.strings.firmware_update_disconnect_warning
import org.meshtastic.core.strings.firmware_update_do_not_close
import org.meshtastic.core.strings.firmware_update_done
import org.meshtastic.core.strings.firmware_update_downloading
import org.meshtastic.core.strings.firmware_update_error
import org.meshtastic.core.strings.firmware_update_hang_tight
import org.meshtastic.core.strings.firmware_update_keep_device_close
import org.meshtastic.core.strings.firmware_update_latest
import org.meshtastic.core.strings.firmware_update_method_detail
import org.meshtastic.core.strings.firmware_update_rak4631_bootloader_hint
import org.meshtastic.core.strings.firmware_update_retry
import org.meshtastic.core.strings.firmware_update_save_dfu_file
import org.meshtastic.core.strings.firmware_update_select_file
import org.meshtastic.core.strings.firmware_update_stable
import org.meshtastic.core.strings.firmware_update_success
import org.meshtastic.core.strings.firmware_update_taking_a_while
import org.meshtastic.core.strings.firmware_update_title
import org.meshtastic.core.strings.firmware_update_unknown_release
import org.meshtastic.core.strings.firmware_update_usb_bootloader_warning
import org.meshtastic.core.strings.firmware_update_usb_instruction_text
import org.meshtastic.core.strings.firmware_update_usb_instruction_title
import org.meshtastic.core.strings.i_know_what_i_m_doing
import org.meshtastic.core.strings.learn_more
import org.meshtastic.core.strings.okay
import org.meshtastic.core.strings.save

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareUpdateScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: FirmwareUpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val selectedReleaseType by viewModel.selectedReleaseType.collectAsState()

    val getZipFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.startUpdateFromFile(it) }
        }
    val getUf2FileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.startUpdateFromFile(it) }
        }

    val saveFileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri: Uri? ->
            uri?.let { viewModel.saveDfuFile(it) }
        }

    val shouldKeepScreenOn = shouldKeepFirmwareScreenOn(state)

    KeepScreenOn(shouldKeepScreenOn)

    FirmwareUpdateScaffold(
        modifier = modifier,
        navController = navController,
        state = state,
        selectedReleaseType = selectedReleaseType,
        onReleaseTypeSelect = viewModel::setReleaseType,
        onStartUpdate = viewModel::startUpdate,
        onPickFile = {
            if (state is FirmwareUpdateState.Ready) {
                if ((state as FirmwareUpdateState.Ready).updateMethod is FirmwareUpdateMethod.Ble) {
                    getZipFileLauncher.launch("application/zip")
                } else if ((state as FirmwareUpdateState.Ready).updateMethod is FirmwareUpdateMethod.Usb) {
                    getUf2FileLauncher.launch("*/*")
                }
            }
        },
        onSaveFile = { fileName -> saveFileLauncher.launch(fileName) },
        onRetry = viewModel::checkForUpdates,
        onDone = { navController.navigateUp() },
        onDismissBootloaderWarning = viewModel::dismissBootloaderWarningForCurrentDevice,
    )
}

@Composable
private fun FirmwareUpdateScaffold(
    navController: NavController,
    state: FirmwareUpdateState,
    selectedReleaseType: FirmwareReleaseType,
    onReleaseTypeSelect: (FirmwareReleaseType) -> Unit,
    onStartUpdate: () -> Unit,
    onPickFile: () -> Unit,
    onSaveFile: (String) -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onDismissBootloaderWarning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.firmware_update_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            FirmwareUpdateContent(
                state = state,
                selectedReleaseType = selectedReleaseType,
                onReleaseTypeSelect = onReleaseTypeSelect,
                onStartUpdate = onStartUpdate,
                onPickFile = onPickFile,
                onSaveFile = onSaveFile,
                onRetry = onRetry,
                onDone = onDone,
                onDismissBootloaderWarning = onDismissBootloaderWarning,
            )
        }
    }
}

private fun shouldKeepFirmwareScreenOn(state: FirmwareUpdateState): Boolean = when (state) {
    is FirmwareUpdateState.Downloading,
    is FirmwareUpdateState.Processing,
    is FirmwareUpdateState.Updating,
    -> true

    else -> false
}

@Composable
private fun FirmwareUpdateContent(
    state: FirmwareUpdateState,
    selectedReleaseType: FirmwareReleaseType,
    onReleaseTypeSelect: (FirmwareReleaseType) -> Unit,
    onStartUpdate: () -> Unit,
    onPickFile: () -> Unit,
    onSaveFile: (String) -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onDismissBootloaderWarning: () -> Unit,
) {
    val modifier =
        if (state is FirmwareUpdateState.Ready) {
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        } else {
            Modifier.padding(24.dp)
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = {
            when (state) {
                is FirmwareUpdateState.Idle,
                FirmwareUpdateState.Checking,
                -> CheckingState()

                is FirmwareUpdateState.Ready ->
                    ReadyState(
                        state = state,
                        selectedReleaseType = selectedReleaseType,
                        onReleaseTypeSelect = onReleaseTypeSelect,
                        onStartUpdate = onStartUpdate,
                        onPickFile = onPickFile,
                        onDismissBootloaderWarning = onDismissBootloaderWarning,
                    )

                is FirmwareUpdateState.Downloading -> DownloadingState(state)
                is FirmwareUpdateState.Processing -> ProcessingState(state.message)
                is FirmwareUpdateState.Updating -> UpdatingState(state)
                is FirmwareUpdateState.Error -> ErrorState(error = state.error, onRetry = onRetry)
                is FirmwareUpdateState.Success -> SuccessState(onDone = onDone)
                is FirmwareUpdateState.AwaitingFileSave -> AwaitingFileSaveState(state, onSaveFile)
            }
        },
    )
}

@Composable
private fun ColumnScope.CheckingState() {
    CircularWavyProgressIndicator(modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(24.dp))
    Text(stringResource(Res.string.firmware_update_checking), style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
private fun ColumnScope.ReadyState(
    state: FirmwareUpdateState.Ready,
    selectedReleaseType: FirmwareReleaseType,
    onReleaseTypeSelect: (FirmwareReleaseType) -> Unit,
    onStartUpdate: () -> Unit,
    onPickFile: () -> Unit,
    onDismissBootloaderWarning: () -> Unit,
) {
    var showDisclaimer by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val device = state.deviceHardware

    if (showDisclaimer) {
        DisclaimerDialog(
            updateMethod = state.updateMethod,
            onDismissRequest = { showDisclaimer = false },
            onConfirm = {
                showDisclaimer = false
                pendingAction?.invoke()
            },
        )
    }

    DeviceInfoCard(device, state.release, state.currentFirmwareVersion)

    if (state.showBootloaderWarning) {
        Spacer(Modifier.height(16.dp))
        BootloaderWarningCard(deviceHardware = device, onDismissForDevice = onDismissBootloaderWarning)
    }

    if (state.release != null) {
        ReleaseTypeSelector(selectedReleaseType, onReleaseTypeSelect)
        Spacer(Modifier.height(16.dp))
        ReleaseNotesCard(state.release.releaseNotes)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                pendingAction = onStartUpdate
                showDisclaimer = true
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(
                imageVector =
                when (state.updateMethod) {
                    FirmwareUpdateMethod.Ble -> Icons.Rounded.Bluetooth
                    FirmwareUpdateMethod.Usb -> Icons.Rounded.Usb
                    else -> Icons.Default.SystemUpdate
                },
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    resource = Res.string.firmware_update_method_detail,
                    stringResource(state.updateMethod.description),
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    OutlinedButton(
        onClick = {
            pendingAction = onPickFile
            showDisclaimer = true
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Icon(Icons.Default.Folder, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.firmware_update_select_file))
    }
}

@Composable
private fun DisclaimerDialog(updateMethod: FirmwareUpdateMethod, onDismissRequest: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.firmware_update_disclaimer_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.firmware_update_disclaimer_text))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(Res.string.firmware_update_disconnect_warning),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (updateMethod is FirmwareUpdateMethod.Ble) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ChirpyCard()
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.i_know_what_i_m_doing)) } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun ChirpyCard() {
    Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = spacedBy(4.dp),
            ) {
                BasicText(text = "🪜", modifier = Modifier.size(48.dp), autoSize = TextAutoSize.StepBased())
                AsyncImage(
                    model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(org.meshtastic.core.ui.R.drawable.chirpy)
                        .build(),
                    contentScale = ContentScale.Fit,
                    contentDescription = stringResource(Res.string.chirpy),
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(
                text = stringResource(Res.string.firmware_update_disclaimer_chirpy_says),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun DeviceHardwareImage(deviceHardware: DeviceHardware, modifier: Modifier = Modifier) {
    val hwImg = deviceHardware.images?.getOrNull(1) ?: deviceHardware.images?.getOrNull(0) ?: "unknown.svg"
    val imageUrl = "https://flasher.meshtastic.org/img/devices/$hwImg"
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(imageUrl).build(),
        contentScale = ContentScale.Fit,
        contentDescription = deviceHardware.displayName,
        modifier = modifier,
    )
}

@Composable
private fun ReleaseNotesCard(releaseNotes: String) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Release Notes", style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Markdown(content = releaseNotes, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(
    deviceHardware: DeviceHardware,
    release: FirmwareRelease?,
    currentFirmwareVersion: String? = null,
) {
    val target = deviceHardware.hwModelSlug.ifEmpty { deviceHardware.platformioTarget }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceHardwareImage(deviceHardware, Modifier.size(80.dp))

                Text(
                    stringResource(Res.string.firmware_update_device, deviceHardware.displayName),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Target: $target",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val currentVersion =
                stringResource(
                    Res.string.firmware_update_currently_installed,
                    currentFirmwareVersion ?: stringResource(Res.string.firmware_update_unknown_release),
                )
            Text(modifier = Modifier.fillMaxWidth(), text = currentVersion)
            Spacer(Modifier.height(4.dp))
            val releaseVersion = release?.title ?: stringResource(Res.string.firmware_update_unknown_release)
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(Res.string.firmware_update_latest, releaseVersion),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BootloaderWarningCard(deviceHardware: DeviceHardware, onDismissForDevice: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors =
        CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                    stringResource(Res.string.firmware_update_usb_bootloader_warning, deviceHardware.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val slug = deviceHardware.hwModelSlug
            if (slug.equals("RAK4631", ignoreCase = true)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.firmware_update_rak4631_bootloader_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val infoUrl = deviceHardware.bootloaderInfoUrl
            if (!infoUrl.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                val context = LocalContext.current
                TextButton(
                    onClick = {
                        runCatching {
                            val intent =
                                android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = infoUrl.toUri()
                                }
                            context.startActivity(intent)
                        }
                    },
                ) {
                    Text(text = stringResource(Res.string.learn_more))
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismissForDevice) {
                Text(text = stringResource(Res.string.dont_show_again_for_device))
            }
        }
    }
}

@Composable
private fun ReleaseTypeSelector(
    selectedReleaseType: FirmwareReleaseType,
    onReleaseTypeSelect: (FirmwareReleaseType) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selectedReleaseType == FirmwareReleaseType.STABLE,
            onClick = { onReleaseTypeSelect(FirmwareReleaseType.STABLE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text(stringResource(Res.string.firmware_update_stable))
        }
        SegmentedButton(
            selected = selectedReleaseType == FirmwareReleaseType.ALPHA,
            onClick = { onReleaseTypeSelect(FirmwareReleaseType.ALPHA) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text(stringResource(Res.string.firmware_update_alpha))
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun ColumnScope.DownloadingState(state: FirmwareUpdateState.Downloading) {
    Icon(
        Icons.Default.CloudDownload,
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(Res.string.firmware_update_downloading, (state.progress * 100).toInt()),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(16.dp))
    LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    CyclingMessages()
}

@Composable
private fun ColumnScope.ProcessingState(message: String) {
    CircularWavyProgressIndicator(modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(24.dp))
    Text(message, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))
    CyclingMessages()
}

@Composable
private fun ColumnScope.UpdatingState(state: FirmwareUpdateState.Updating) {
    CircularWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(24.dp))
    Text(state.message, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    CyclingMessages()
}

@Composable
private fun ColumnScope.AwaitingFileSaveState(
    state: FirmwareUpdateState.AwaitingFileSave,
    onSaveFile: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { /* No-op to force user to acknowledge */ },
            title = { Text(stringResource(Res.string.firmware_update_usb_instruction_title)) },
            text = { Text(stringResource(Res.string.firmware_update_usb_instruction_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onSaveFile(state.fileName)
                    },
                ) {
                    Text(stringResource(Res.string.okay))
                }
            },
        )
    }

    CircularWavyProgressIndicator(modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(Res.string.firmware_update_save_dfu_file),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )

    if (!showDialog) {
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSaveFile(state.fileName) }) { Text(stringResource(Res.string.save)) }
    }
}

private const val CYCLE_DELAY = 4000L

@Composable
private fun CyclingMessages() {
    val messages =
        listOf(
            stringResource(Res.string.firmware_update_hang_tight),
            stringResource(Res.string.firmware_update_keep_device_close),
            stringResource(Res.string.firmware_update_do_not_close),
            stringResource(Res.string.firmware_update_almost_there),
            stringResource(Res.string.firmware_update_taking_a_while),
        )
    var currentMessageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(CYCLE_DELAY)
            currentMessageIndex = (currentMessageIndex + 1) % messages.size
        }
    }

    AnimatedContent(targetState = messages[currentMessageIndex], label = "CyclingMessage") { message ->
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ColumnScope.ErrorState(error: String, onRetry: () -> Unit) {
    Icon(
        Icons.Default.Dangerous,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(Res.string.firmware_update_error, error),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    OutlinedButton(onClick = onRetry) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.firmware_update_retry))
    }
}

@Composable
private fun ColumnScope.SuccessState(onDone: () -> Unit) {
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(Res.string.firmware_update_success),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(stringResource(Res.string.firmware_update_done))
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        if (enabled) {
            view.keepScreenOn = true
        }
        onDispose {
            if (enabled) {
                view.keepScreenOn = false
            }
        }
    }
}
