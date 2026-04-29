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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.meshtastic.feature.discovery.ui.component.DwellProgressIndicator
import org.meshtastic.feature.discovery.ui.component.PresetPickerCard

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
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val selectedPresets by viewModel.selectedPresets.collectAsStateWithLifecycle()
    val dwellMinutes by viewModel.dwellDurationMinutes.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val homePreset by viewModel.homePreset.collectAsStateWithLifecycle()

    var keepScreenAwake by rememberSaveable { mutableStateOf(true) }
    val isScanning = scanState !is DiscoveryScanState.Idle

    // Keep screen awake while a scan is in progress
    KeepScreenOn(isScanning && keepScreenAwake)

    // Navigate to summary when scan completes
    LaunchedEffect(scanState) {
        if (scanState is DiscoveryScanState.Complete) {
            currentSession?.id?.let { sessionId ->
                viewModel.reset()
                onNavigateToSummary(sessionId)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Local Mesh Discovery") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(imageVector = MeshtasticIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(imageVector = MeshtasticIcons.History, contentDescription = "Scan History")
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
                        hasPresetsSelected = selectedPresets.isNotEmpty(),
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
                // Preset picker
                item(key = "preset_picker") {
                    PresetPickerCard(
                        selectedPresets = selectedPresets,
                        homePreset = homePreset,
                        onTogglePreset = viewModel::togglePreset,
                        enabled = true,
                    )
                }

                // Dwell time picker
                item(key = "dwell_picker") {
                    DwellTimePicker(
                        selectedMinutes = dwellMinutes,
                        onMinutesSelected = viewModel::setDwellDuration,
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
            title = "Keep screen awake",
            summary = "Prevents Android Doze mode from dropping radio packets during long scans. Recommended.",
            checked = keepAwake,
            enabled = true,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun ConnectionWarningCard(modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
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
                    text = "Not Connected",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Connect to a Meshtastic device to start scanning.",
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
    onMinutesSelected: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CONTENT_PADDING)) {
            Text(text = "Dwell Time", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Time to listen on each preset",
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
                                onMinutesSelected(minutes)
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
            Text("Stop Scan", modifier = Modifier.padding(start = 8.dp))
        }
    } else {
        Button(onClick = onStart, enabled = isConnected && hasPresetsSelected, modifier = modifier.fillMaxWidth()) {
            Icon(imageVector = MeshtasticIcons.PlayArrow, contentDescription = null)
            Text("Start Scan", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ScanProgressSection(scanState: DiscoveryScanState, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(CONTENT_PADDING)) {
            Text(text = "Scan Progress", style = MaterialTheme.typography.titleMedium)
            when (scanState) {
                is DiscoveryScanState.Shifting -> {
                    Text(text = "Shifting to ${scanState.presetName}…", style = MaterialTheme.typography.bodyMedium)
                }
                is DiscoveryScanState.Reconnecting -> {
                    Text(text = "Reconnecting on ${scanState.presetName}…", style = MaterialTheme.typography.bodyMedium)
                }
                is DiscoveryScanState.Dwell -> {
                    DwellProgressIndicator(
                        presetName = scanState.presetName,
                        remainingSeconds = scanState.remainingSeconds,
                        totalSeconds = scanState.totalSeconds,
                    )
                }
                is DiscoveryScanState.Analysis -> {
                    Text(text = "Analyzing results…", style = MaterialTheme.typography.bodyMedium)
                }
                is DiscoveryScanState.Restoring -> {
                    Text(text = "Restoring home preset…", style = MaterialTheme.typography.bodyMedium)
                }
                is DiscoveryScanState.Paused -> {
                    Text(
                        text = "Paused: ${scanState.reason}",
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
