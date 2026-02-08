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
@file:Suppress("TooManyFunctions")
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package org.meshtastic.feature.firmware

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.back
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
import org.meshtastic.core.strings.firmware_update_error
import org.meshtastic.core.strings.firmware_update_hang_tight
import org.meshtastic.core.strings.firmware_update_keep_device_close
import org.meshtastic.core.strings.firmware_update_latest
import org.meshtastic.core.strings.firmware_update_local_file
import org.meshtastic.core.strings.firmware_update_method_detail
import org.meshtastic.core.strings.firmware_update_rak4631_bootloader_hint
import org.meshtastic.core.strings.firmware_update_release_notes
import org.meshtastic.core.strings.firmware_update_retry
import org.meshtastic.core.strings.firmware_update_save_dfu_file
import org.meshtastic.core.strings.firmware_update_select_file
import org.meshtastic.core.strings.firmware_update_source_local
import org.meshtastic.core.strings.firmware_update_stable
import org.meshtastic.core.strings.firmware_update_success
import org.meshtastic.core.strings.firmware_update_taking_a_while
import org.meshtastic.core.strings.firmware_update_target
import org.meshtastic.core.strings.firmware_update_title
import org.meshtastic.core.strings.firmware_update_unknown_release
import org.meshtastic.core.strings.firmware_update_usb_bootloader_warning
import org.meshtastic.core.strings.firmware_update_usb_instruction_text
import org.meshtastic.core.strings.firmware_update_usb_instruction_title
import org.meshtastic.core.strings.firmware_update_verification_failed
import org.meshtastic.core.strings.firmware_update_verifying
import org.meshtastic.core.strings.firmware_update_waiting_reconnect
import org.meshtastic.core.strings.i_know_what_i_m_doing
import org.meshtastic.core.strings.learn_more
import org.meshtastic.core.strings.okay
import org.meshtastic.core.strings.save
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.CheckCircle
import org.meshtastic.core.ui.icon.CloudDownload
import org.meshtastic.core.ui.icon.Dangerous
import org.meshtastic.core.ui.icon.Folder
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.SystemUpdate
import org.meshtastic.core.ui.icon.Usb
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.icon.Wifi

private const val CYCLE_DELAY_MS = 4500L

@Composable
@Suppress("LongMethod")
fun FirmwareUpdateScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: FirmwareUpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedReleaseType by viewModel.selectedReleaseType.collectAsStateWithLifecycle()
    val deviceHardware by viewModel.deviceHardware.collectAsStateWithLifecycle()
    val currentVersion by viewModel.currentFirmwareVersion.collectAsStateWithLifecycle()
    val selectedRelease by viewModel.selectedRelease.collectAsStateWithLifecycle()

    var showExitConfirmation by remember { mutableStateOf(false) }

    val getFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.startUpdateFromFile(it) }
        }

    val saveFileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri: Uri? ->
            uri?.let { viewModel.saveDfuFile(it) }
        }

    val actions =
        remember(viewModel, navController, state) {
            FirmwareUpdateActions(
                onReleaseTypeSelect = viewModel::setReleaseType,
                onStartUpdate = viewModel::startUpdate,
                onPickFile = {
                    if (state is FirmwareUpdateState.Ready) {
                        val readyState = state as FirmwareUpdateState.Ready
                        if (
                            readyState.updateMethod is FirmwareUpdateMethod.Ble ||
                            readyState.updateMethod is FirmwareUpdateMethod.Wifi
                        ) {
                            getFileLauncher.launch("*/*")
                        } else if (readyState.updateMethod is FirmwareUpdateMethod.Usb) {
                            getFileLauncher.launch("*/*")
                        }
                    }
                },
                onSaveFile = { fileName -> saveFileLauncher.launch(fileName) },
                onRetry = viewModel::checkForUpdates,
                onCancel = { showExitConfirmation = true },
                onDone = { navController.navigateUp() },
                onDismissBootloaderWarning = viewModel::dismissBootloaderWarningForCurrentDevice,
            )
        }

    KeepScreenOn(shouldKeepFirmwareScreenOn(state))

    androidx.activity.compose.BackHandler(enabled = shouldKeepFirmwareScreenOn(state)) { showExitConfirmation = true }

    if (showExitConfirmation) {
        MeshtasticDialog(
            onDismiss = { showExitConfirmation = false },
            title = stringResource(Res.string.firmware_update_disclaimer_title),
            message = stringResource(Res.string.firmware_update_disconnect_warning),
            confirmText = stringResource(Res.string.firmware_update_retry),
            onConfirm = {
                showExitConfirmation = false
                viewModel.cancelUpdate()
                navController.navigateUp()
            },
            dismissText = stringResource(Res.string.back),
        )
    }

    FirmwareUpdateScaffold(
        modifier = modifier,
        navController = navController,
        state = state,
        selectedReleaseType = selectedReleaseType,
        actions = actions,
        deviceHardware = deviceHardware,
        currentVersion = currentVersion,
        selectedRelease = selectedRelease,
    )
}

@Composable
private fun FirmwareUpdateScaffold(
    navController: NavController,
    state: FirmwareUpdateState,
    selectedReleaseType: FirmwareReleaseType,
    actions: FirmwareUpdateActions,
    deviceHardware: DeviceHardware?,
    currentVersion: String?,
    selectedRelease: FirmwareRelease?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.firmware_update_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
            Modifier.padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (deviceHardware != null) {
                Spacer(Modifier.height(16.dp))
                AnimatedVisibility(
                    visible =
                    state is FirmwareUpdateState.Ready ||
                        state is FirmwareUpdateState.Idle ||
                        state is FirmwareUpdateState.Checking,
                ) {
                    Column {
                        ReleaseTypeSelector(selectedReleaseType, actions.onReleaseTypeSelect)
                        Spacer(Modifier.height(16.dp))
                    }
                }
                DeviceInfoCard(
                    deviceHardware = deviceHardware,
                    release = selectedRelease,
                    currentFirmwareVersion = currentVersion,
                    selectedReleaseType = selectedReleaseType,
                )
                Spacer(Modifier.height(16.dp))
            }

            Box(contentAlignment = Alignment.TopCenter) {
                FirmwareUpdateContent(state = state, selectedReleaseType = selectedReleaseType, actions = actions)
            }
        }
    }
}

private fun shouldKeepFirmwareScreenOn(state: FirmwareUpdateState): Boolean = when (state) {
    is FirmwareUpdateState.Downloading,
    is FirmwareUpdateState.Processing,
    is FirmwareUpdateState.Updating,
    is FirmwareUpdateState.Verifying,
    -> true

    else -> false
}

@Composable
private fun FirmwareUpdateContent(
    state: FirmwareUpdateState,
    selectedReleaseType: FirmwareReleaseType,
    actions: FirmwareUpdateActions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        content = {
            when (state) {
                is FirmwareUpdateState.Idle,
                FirmwareUpdateState.Checking,
                -> CheckingState()

                is FirmwareUpdateState.Ready ->
                    ReadyState(state = state, selectedReleaseType = selectedReleaseType, actions = actions)

                is FirmwareUpdateState.Downloading ->
                    ProgressContent(state.progressState, onCancel = actions.onCancel, isDownloading = true)

                is FirmwareUpdateState.Processing -> ProgressContent(state.progressState, onCancel = actions.onCancel)

                is FirmwareUpdateState.Updating ->
                    ProgressContent(state.progressState, onCancel = actions.onCancel, isUpdating = true)

                is FirmwareUpdateState.Verifying -> VerifyingState()
                is FirmwareUpdateState.VerificationFailed ->
                    VerificationFailedState(onRetry = actions.onStartUpdate, onIgnore = actions.onDone)
                is FirmwareUpdateState.Error -> ErrorState(error = state.error, onRetry = actions.onRetry)
                is FirmwareUpdateState.Success -> SuccessState(onDone = actions.onDone)
                is FirmwareUpdateState.AwaitingFileSave -> AwaitingFileSaveState(state, actions.onSaveFile)
            }
        },
    )
}

@Composable
private fun VerifyingState() {
    CircularWavyProgressIndicator(modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(24.dp))
    Text(stringResource(Res.string.firmware_update_verifying), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(Res.string.firmware_update_waiting_reconnect),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    CyclingMessages()
}

@Composable
private fun CheckingState() {
    CircularWavyProgressIndicator(modifier = Modifier.size(64.dp))
    Spacer(Modifier.height(24.dp))
    Text(stringResource(Res.string.firmware_update_checking), style = MaterialTheme.typography.bodyLarge)
}

@Composable
@Suppress("LongMethod")
private fun ReadyState(
    state: FirmwareUpdateState.Ready,
    selectedReleaseType: FirmwareReleaseType,
    actions: FirmwareUpdateActions,
) {
    var showDisclaimer by remember { mutableStateOf(false) }
    val device = state.deviceHardware
    val haptic = LocalHapticFeedback.current

    if (showDisclaimer) {
        DisclaimerDialog(
            updateMethod = state.updateMethod,
            onDismiss = { showDisclaimer = false },
            onConfirm = {
                showDisclaimer = false
                if (selectedReleaseType == FirmwareReleaseType.LOCAL) {
                    actions.onPickFile()
                } else {
                    actions.onStartUpdate()
                }
            },
        )
    }

    if (state.showBootloaderWarning) {
        BootloaderWarningCard(deviceHardware = device, onDismissForDevice = actions.onDismissBootloaderWarning)
        Spacer(Modifier.height(16.dp))
    }

    Spacer(Modifier.height(16.dp))

    if (selectedReleaseType == FirmwareReleaseType.LOCAL) {
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showDisclaimer = true
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(MeshtasticIcons.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.firmware_update_select_file))
        }
    } else if (state.release != null) {
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showDisclaimer = true
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(
                imageVector =
                when (state.updateMethod) {
                    FirmwareUpdateMethod.Ble -> MeshtasticIcons.Bluetooth
                    FirmwareUpdateMethod.Usb -> MeshtasticIcons.Usb
                    FirmwareUpdateMethod.Wifi -> MeshtasticIcons.Wifi
                    else -> MeshtasticIcons.SystemUpdate
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
        Spacer(Modifier.height(24.dp))
        ReleaseNotesCard(state.release.releaseNotes)
    }
}

@Composable
private fun DisclaimerDialog(updateMethod: FirmwareUpdateMethod, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MeshtasticDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.firmware_update_disclaimer_title),
        confirmText = stringResource(Res.string.i_know_what_i_m_doing),
        onConfirm = onConfirm,
        dismissText = stringResource(Res.string.cancel),
        text = {
            Column(modifier = Modifier.animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.firmware_update_disclaimer_text))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        MeshtasticIcons.Warning,
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
                    Spacer(modifier = Modifier.height(12.dp))
                    ChirpyCard()
                }
            }
        },
    )
}

@Composable
private fun ChirpyCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                        .crossfade(true)
                        .build(),
                    contentScale = ContentScale.Fit,
                    contentDescription = stringResource(Res.string.chirpy),
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(
                text = stringResource(Res.string.firmware_update_disclaimer_chirpy_says),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeviceHardwareImage(deviceHardware: DeviceHardware, modifier: Modifier = Modifier) {
    val hwImg = deviceHardware.images?.getOrNull(1) ?: deviceHardware.images?.getOrNull(0) ?: "unknown.svg"
    val imageUrl = "https://flasher.meshtastic.org/img/devices/$hwImg"

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(imageUrl).crossfade(true).build(),
        contentScale = ContentScale.Fit,
        contentDescription = deviceHardware.displayName,
        modifier = modifier,
    )
}

@Composable
private fun ReleaseNotesCard(releaseNotes: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.firmware_update_release_notes),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Markdown(content = releaseNotes, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DeviceInfoCard(
    deviceHardware: DeviceHardware,
    release: FirmwareRelease?,
    currentFirmwareVersion: String? = null,
    selectedReleaseType: FirmwareReleaseType = FirmwareReleaseType.STABLE,
) {
    val target = deviceHardware.hwModelSlug.ifEmpty { deviceHardware.platformioTarget }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
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
                stringResource(Res.string.firmware_update_target, target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val currentVersionString =
                stringResource(
                    Res.string.firmware_update_currently_installed,
                    currentFirmwareVersion ?: stringResource(Res.string.firmware_update_unknown_release),
                )
            Text(modifier = Modifier.fillMaxWidth(), text = currentVersionString)
            Spacer(Modifier.height(4.dp))
            val (label, version) =
                if (selectedReleaseType == FirmwareReleaseType.LOCAL) {
                    stringResource(Res.string.firmware_update_source_local) to ""
                } else {
                    val releaseVersion = release?.title ?: stringResource(Res.string.firmware_update_unknown_release)
                    stringResource(Res.string.firmware_update_latest, "") to releaseVersion
                }
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "$label$version",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BootloaderWarningCard(deviceHardware: DeviceHardware, onDismissForDevice: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors =
        CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MeshtasticIcons.Warning,
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
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
        ) {
            Text(stringResource(Res.string.firmware_update_stable))
        }
        SegmentedButton(
            selected = selectedReleaseType == FirmwareReleaseType.ALPHA,
            onClick = { onReleaseTypeSelect(FirmwareReleaseType.ALPHA) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
        ) {
            Text(stringResource(Res.string.firmware_update_alpha))
        }
        SegmentedButton(
            selected = selectedReleaseType == FirmwareReleaseType.LOCAL,
            onClick = { onReleaseTypeSelect(FirmwareReleaseType.LOCAL) },
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
        ) {
            Text(stringResource(Res.string.firmware_update_local_file))
        }
    }
}

@Composable
private fun ProgressContent(
    progressState: ProgressState,
    onCancel: () -> Unit,
    isDownloading: Boolean = false,
    isUpdating: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        if (isDownloading) {
            Icon(
                MeshtasticIcons.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            CircularWavyProgressIndicator(
                progress = { if (isUpdating) progressState.progress else 1f },
                modifier = Modifier.size(64.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(progressState.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

        val details = progressState.details
        if (details != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(12.dp))

        if (isDownloading || isUpdating) {
            LinearWavyProgressIndicator(
                progress = { progressState.progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        CyclingMessages()
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
    }
}

@Composable
private fun AwaitingFileSaveState(state: FirmwareUpdateState.AwaitingFileSave, onSaveFile: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        MeshtasticDialog(
            onDismiss = { /* No-op to force user to acknowledge */ },
            title = stringResource(Res.string.firmware_update_usb_instruction_title),
            confirmText = stringResource(Res.string.okay),
            onConfirm = {
                showDialog = false
                onSaveFile(state.fileName)
            },
            text = { Text(stringResource(Res.string.firmware_update_usb_instruction_text)) },
            dismissable = false,
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
            delay(CYCLE_DELAY_MS)
            currentMessageIndex = (currentMessageIndex + 1) % messages.size
        }
    }

    Text(
        messages[currentMessageIndex],
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    )
}

@Composable
private fun VerificationFailedState(onRetry: () -> Unit, onIgnore: () -> Unit) {
    Icon(
        MeshtasticIcons.Warning,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(Res.string.firmware_update_verification_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = onRetry) {
            Icon(MeshtasticIcons.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.firmware_update_retry))
        }
        Button(onClick = onIgnore) { Text(stringResource(Res.string.firmware_update_done)) }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Icon(
        MeshtasticIcons.Dangerous,
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
        Icon(MeshtasticIcons.Refresh, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.firmware_update_retry))
    }
}

@Composable
private fun SuccessState(onDone: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            MeshtasticIcons.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(Res.string.firmware_update_success),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(Res.string.firmware_update_done))
        }
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
