/*
 * Copyright (c) 2026 Meshtastic LLC
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
@file:Suppress("TooManyFunctions", "MagicNumber")

package org.meshtastic.feature.discovery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.util.beaconJoinOption
import org.meshtastic.core.model.util.toJoinChannelSet
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.back
import org.meshtastic.core.resources.discovery_analysing_results
import org.meshtastic.core.resources.discovery_cancelling_scan
import org.meshtastic.core.resources.discovery_connection_warning
import org.meshtastic.core.resources.discovery_dwell_time
import org.meshtastic.core.resources.discovery_dwell_time_description
import org.meshtastic.core.resources.discovery_keep_screen_awake
import org.meshtastic.core.resources.discovery_keep_screen_awake_description
import org.meshtastic.core.resources.discovery_local_mesh
import org.meshtastic.core.resources.discovery_not_connected
import org.meshtastic.core.resources.discovery_not_connected_description
import org.meshtastic.core.resources.discovery_paused
import org.meshtastic.core.resources.discovery_preparing
import org.meshtastic.core.resources.discovery_reconnecting
import org.meshtastic.core.resources.discovery_restoring_preset
import org.meshtastic.core.resources.discovery_scan_failed
import org.meshtastic.core.resources.discovery_scan_history
import org.meshtastic.core.resources.discovery_scan_progress
import org.meshtastic.core.resources.discovery_shifting_to
import org.meshtastic.core.resources.discovery_start_scan
import org.meshtastic.core.resources.discovery_start_scan_disabled
import org.meshtastic.core.resources.discovery_start_scan_reason_24ghz_unsupported
import org.meshtastic.core.resources.discovery_start_scan_reason_default_key
import org.meshtastic.core.resources.discovery_start_scan_reason_no_presets
import org.meshtastic.core.resources.discovery_start_scan_reason_not_connected
import org.meshtastic.core.resources.discovery_stop_scan
import org.meshtastic.core.resources.mesh_beacon_invitations_title
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.History
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PlayArrow
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.util.KeepScreenOn
import org.meshtastic.feature.discovery.DiscoveryScanState
import org.meshtastic.feature.discovery.DiscoveryViewModel
import org.meshtastic.feature.discovery.ui.component.BeaconChannelsCard
import org.meshtastic.feature.discovery.ui.component.DwellProgressIndicator
import org.meshtastic.feature.discovery.ui.component.MeshBeaconInvitationCard
import org.meshtastic.feature.discovery.ui.component.PresetPickerCard
import org.meshtastic.proto.ChannelSet

private val CONTENT_PADDING = 16.dp
private val SECTION_SPACING = 16.dp

private val DWELL_OPTIONS = listOf(1, 5, 15, 30, 45, 60, 90, 120, 180)

/** Main scan screen for the Local Mesh Discovery feature. */
@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScanScreen(
    viewModel: DiscoveryViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToSummary: (sessionId: Long) -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onJoinOffer: (ChannelSet) -> Unit = {},
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val selectedPresets by viewModel.selectedPresets.collectAsStateWithLifecycle()
    val beaconOffers by viewModel.beaconOffers.collectAsStateWithLifecycle()
    val beaconPresets by viewModel.beaconPresets.collectAsStateWithLifecycle()
    val beaconChannels by viewModel.beaconChannels.collectAsStateWithLifecycle()
    val selectedBeaconChannels by viewModel.selectedBeaconChannels.collectAsStateWithLifecycle()
    val currentLora by viewModel.currentLora.collectAsStateWithLifecycle()
    val currentChannels by viewModel.currentChannels.collectAsStateWithLifecycle()
    val dwellMinutes by viewModel.dwellDurationMinutes.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val usesDefaultKey by viewModel.usesDefaultKey.collectAsStateWithLifecycle()
    val is24GhzBlocked by viewModel.is24GhzBlocked.collectAsStateWithLifecycle()
    val isLora24Region by viewModel.isLora24Region.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val homePreset by viewModel.homePreset.collectAsStateWithLifecycle()

    var keepScreenAwake by rememberSaveable { mutableStateOf(true) }
    val isScanning = scanState !is DiscoveryScanState.Idle

    // Keep screen awake while a scan is in progress
    KeepScreenOn(isScanning && keepScreenAwake)

    // Navigate to summary when scan completes
    LaunchedEffect(scanState, onNavigateToSummary) {
        if (scanState is DiscoveryScanState.Complete) {
            currentSession?.id?.let { sessionId ->
                viewModel.reset()
                onNavigateToSummary(sessionId)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.discovery_local_mesh)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = MeshtasticIcons.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = MeshtasticIcons.History,
                            contentDescription = stringResource(Res.string.discovery_scan_history),
                        )
                    }
                },
            )
        },
        bottomBar = {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.padding(horizontal = CONTENT_PADDING, vertical = 16.dp),
                ) {
                    ScanButton(
                        scanState = scanState,
                        isConnected = isConnected,
                        hasPresetsSelected = selectedPresets.isNotEmpty() || selectedBeaconChannels.isNotEmpty(),
                        usesDefaultKey = usesDefaultKey,
                        is24GhzUnsupported = isLora24Region && is24GhzBlocked,
                        onStart = viewModel::startScan,
                        onStop = viewModel::stopScan,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
            modifier = Modifier.fillMaxSize().padding(horizontal = CONTENT_PADDING).padding(top = SECTION_SPACING),
        ) {
            // Connection warning
            if (!isConnected) {
                item(key = "connection_warning") { ConnectionWarningCard() }
            }

            if (!isScanning) {
                // Received Mesh Beacon invitations from other meshes
                if (beaconOffers.isNotEmpty()) {
                    item(key = "invitations_header") {
                        Text(
                            text = stringResource(Res.string.mesh_beacon_invitations_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    items(beaconOffers, key = { "invitation_${it.key}" }) { offer ->
                        val joinOption =
                            remember(offer, currentLora, currentChannels) {
                                offer.beacon.beaconJoinOption(currentLora, currentChannels)
                            }
                        MeshBeaconInvitationCard(
                            offer = offer,
                            joinOption = joinOption,
                            onJoin = { offer.beacon.toJoinChannelSet(joinOption, currentLora)?.let(onJoinOffer) },
                            onDiscover = { viewModel.discoverOffer(offer) },
                            onDismiss = { viewModel.dismissOffer(offer) },
                        )
                    }
                }

                // Preset picker
                item(key = "preset_picker") {
                    PresetPickerCard(
                        selectedPresets = selectedPresets,
                        homePreset = homePreset,
                        onTogglePreset = viewModel::togglePreset,
                        enabled = true,
                        beaconPresets = beaconPresets,
                    )
                }

                // Beacon channels — custom channels advertised by beacons (hidden when none recorded)
                if (beaconChannels.isNotEmpty()) {
                    item(key = "beacon_channels") {
                        BeaconChannelsCard(
                            channels = beaconChannels,
                            selectedIds = selectedBeaconChannels,
                            onToggle = viewModel::toggleBeaconChannel,
                            enabled = true,
                        )
                    }
                }

                // Dwell time picker
                item(key = "dwell_picker") {
                    DwellTimePicker(
                        selectedMinutes = dwellMinutes,
                        onMinuteSelect = viewModel::setDwellDuration,
                        enabled = true,
                    )
                }

                // Keep awake toggle
                item(key = "keep_awake_toggle") {
                    KeepAwakeToggleCard(keepAwake = keepScreenAwake, onToggle = { keepScreenAwake = it })
                }
            }

            // Scan progress section
            if (isScanning) {
                item(key = "scan_progress") { ScanProgressSection(scanState = scanState) }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(SECTION_SPACING)) }
        }
    }
}

@Composable
private fun KeepAwakeToggleCard(keepAwake: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        SwitchPreference(
            title = stringResource(Res.string.discovery_keep_screen_awake),
            summary = stringResource(Res.string.discovery_keep_screen_awake_description),
            checked = keepAwake,
            enabled = true,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun ConnectionWarningCard(modifier: Modifier = Modifier) {
    val warningDescription = stringResource(Res.string.discovery_connection_warning)
    ElevatedCard(
        modifier =
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = warningDescription
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(CONTENT_PADDING),
        ) {
            Icon(
                imageVector = MeshtasticIcons.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Column {
                Text(
                    text = stringResource(Res.string.discovery_not_connected),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(Res.string.discovery_not_connected_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DwellTimePicker(
    selectedMinutes: Int,
    onMinuteSelect: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CONTENT_PADDING)) {
            Text(
                text = stringResource(Res.string.discovery_dwell_time),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.discovery_dwell_time_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
                OutlinedTextField(
                    value = "$selectedMinutes min",
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DWELL_OPTIONS.forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text("$minutes min") },
                            onClick = {
                                onMinuteSelect(minutes)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanButton(
    scanState: DiscoveryScanState,
    isConnected: Boolean,
    hasPresetsSelected: Boolean,
    usesDefaultKey: Boolean,
    is24GhzUnsupported: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isScanning = scanState !is DiscoveryScanState.Idle
    if (isScanning) {
        OutlinedButton(
            onClick = onStop,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = MeshtasticIcons.Close, contentDescription = null)
            Text(stringResource(Res.string.discovery_stop_scan), modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        val isEnabled = isConnected && hasPresetsSelected && !usesDefaultKey && !is24GhzUnsupported
        val disabledReason =
            when {
                !isConnected -> stringResource(Res.string.discovery_start_scan_reason_not_connected)
                usesDefaultKey -> stringResource(Res.string.discovery_start_scan_reason_default_key)
                is24GhzUnsupported -> stringResource(Res.string.discovery_start_scan_reason_24ghz_unsupported)
                !hasPresetsSelected -> stringResource(Res.string.discovery_start_scan_reason_no_presets)
                else -> ""
            }
        val disabledDescription = stringResource(Res.string.discovery_start_scan_disabled, disabledReason)
        val buttonModifier =
            if (!isEnabled) {
                Modifier.fillMaxWidth().semantics { contentDescription = disabledDescription }
            } else {
                Modifier.fillMaxWidth()
            }
        Column(modifier = modifier) {
            Button(onClick = onStart, enabled = isEnabled, modifier = buttonModifier) {
                Icon(imageVector = MeshtasticIcons.PlayArrow, contentDescription = null)
                Text(stringResource(Res.string.discovery_start_scan), modifier = Modifier.padding(start = 8.dp))
            }
            if (!isEnabled && disabledReason.isNotEmpty()) {
                Text(
                    text = disabledReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun ScanProgressSection(scanState: DiscoveryScanState, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(CONTENT_PADDING).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = stringResource(Res.string.discovery_scan_progress),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            when (scanState) {
                is DiscoveryScanState.Preparing -> {
                    Text(
                        text = stringResource(Res.string.discovery_preparing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is DiscoveryScanState.Shifting -> {
                    Text(
                        text = stringResource(Res.string.discovery_shifting_to, scanState.presetName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is DiscoveryScanState.Reconnecting -> {
                    Text(
                        text = stringResource(Res.string.discovery_reconnecting, scanState.presetName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is DiscoveryScanState.Dwell -> {
                    DwellProgressIndicator(
                        presetName = scanState.presetName,
                        remainingSeconds = scanState.remainingSeconds,
                        totalSeconds = scanState.totalSeconds,
                    )
                }

                is DiscoveryScanState.Analysis -> {
                    Text(
                        text = stringResource(Res.string.discovery_analysing_results),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is DiscoveryScanState.Restoring -> {
                    Text(
                        text = stringResource(Res.string.discovery_restoring_preset),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is DiscoveryScanState.Cancelling -> {
                    Text(
                        text = stringResource(Res.string.discovery_cancelling_scan),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is DiscoveryScanState.Paused -> {
                    Text(
                        text = stringResource(Res.string.discovery_paused, scanState.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is DiscoveryScanState.Failed -> {
                    Text(
                        text = stringResource(Res.string.discovery_scan_failed, scanState.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is DiscoveryScanState.Complete,
                is DiscoveryScanState.Idle,
                -> Unit
            }
        }
    }
}
