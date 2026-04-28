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
package org.meshtastic.wear.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.Text
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.wear.presentation.components.PulsingDot
import org.meshtastic.wear.presentation.components.SectionHeader

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val highContrastMode by viewModel.highContrastModeEnabled.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { SectionHeader("SETTINGS") }

            item {
                PhoneConnectionSection(
                    state = connectionState,
                    onPairClick = { viewModel.startScan() },
                    onStopScanClick = { viewModel.stopScan() },
                    onDisconnectClick = { viewModel.disconnect() },
                )
            }

            if (connectionState is PhoneConnectionState.Scanning || discoveredDevices.isNotEmpty()) {
                item {
                    Text(
                        text = "Discovered Devices",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = COLOR_TEXT_SECONDARY,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                    )
                }

                if (discoveredDevices.isEmpty() && connectionState is PhoneConnectionState.Scanning) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Searching...", fontSize = 11.sp, color = COLOR_TEXT_SECONDARY)
                        }
                    }
                } else {
                    items(discoveredDevices) { device ->
                        DeviceItem(device = device, onClick = { viewModel.connectToDevice(device) })
                    }
                }
            }

            item { PhoneMenuSection() }

            item { DeviceSection() }

            item { MaintenanceSection(onSyncClick = { viewModel.requestSync() }) }

            item {
                DisplaySection(
                    highContrastMode = highContrastMode,
                    onHighContrastModeChange = { viewModel.setHighContrastModeEnabled(it) }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DeviceItem(device: BleDevice, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(COLOR_SURFACE2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (device.name ?: "Unknown").take(1).uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = COLOR_TEAL,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = device.name ?: "Unknown Device",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = COLOR_TEXT_PRIMARY,
                )
                Text(text = device.address, fontSize = 8.sp, color = COLOR_TEXT_SECONDARY)
            }
        }
    }
}

@Composable
private fun PhoneConnectionSection(
    state: PhoneConnectionState,
    onPairClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDisconnectClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text(
            text = "Phone Connection",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        Button(
            onClick = {
                when (state) {
                    PhoneConnectionState.Disconnected -> onPairClick()
                    PhoneConnectionState.Scanning -> onStopScanClick()
                    is PhoneConnectionState.Connected -> onDisconnectClick()
                    else -> {}
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = getButtonColor(state)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhoneConnectionIcon(state)
                Spacer(Modifier.width(10.dp))
                PhoneConnectionTitles(state)
            }
        }
    }
}

@Composable
private fun getButtonColor(state: PhoneConnectionState): Color = when (state) {
    is PhoneConnectionState.Connected -> COLOR_TEAL_DIM
    PhoneConnectionState.PhoneLinked -> COLOR_TEAL_DIM
    is PhoneConnectionState.Connecting -> COLOR_SURFACE1
    PhoneConnectionState.NoPermissions -> COLOR_ERROR_RED.copy(alpha = 0.2f)
    else -> COLOR_SURFACE2
}

@Composable
private fun PhoneConnectionIcon(state: PhoneConnectionState) {
    Box(
        modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(COLOR_TEAL_DIM),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            PhoneConnectionState.Scanning,
            is PhoneConnectionState.Connecting,
            -> {
                PulsingDot(color = COLOR_TEAL, sizeDp = 12)
            }
            is PhoneConnectionState.Connected -> {
                Text("✓", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
            }
            PhoneConnectionState.PhoneLinked -> {
                Text("🔗", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
            }
            PhoneConnectionState.NoPermissions -> {
                Text("!", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = COLOR_ERROR_RED)
            }
            else -> {
                Text("BT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
            }
        }
    }
}

@Composable
private fun PhoneConnectionTitles(state: PhoneConnectionState) {
    Column {
        val title =
            when (state) {
                PhoneConnectionState.Disconnected -> "Pair Phone"
                PhoneConnectionState.Scanning -> "Stop Scanning"
                is PhoneConnectionState.Connecting -> "Connecting..."
                is PhoneConnectionState.Connected -> state.device.name ?: "Connected"
                PhoneConnectionState.NoPermissions -> "Missing Permissions"
                PhoneConnectionState.PhoneLinked -> "Phone Linked"
            }
        val subtitle =
            when (state) {
                PhoneConnectionState.Disconnected -> "Connect via BLE"
                PhoneConnectionState.Scanning -> "Searching for devices"
                is PhoneConnectionState.Connecting -> state.device.address
                is PhoneConnectionState.Connected -> "Active connection"
                PhoneConnectionState.NoPermissions -> "Grant BT permissions"
                PhoneConnectionState.PhoneLinked -> "Linked via Data Layer"
            }
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (state == PhoneConnectionState.NoPermissions) COLOR_ERROR_RED else COLOR_TEXT_PRIMARY,
        )
        Text(text = subtitle, fontSize = 9.sp, color = COLOR_TEXT_SECONDARY)
    }
}

@Composable
private fun PhoneMenuSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = "Phone Menu",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        Button(
            onClick = { /* TODO: Notification settings */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(COLOR_TEAL_DIM),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("NT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Notifications",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = COLOR_TEXT_PRIMARY,
                    )
                    Text("Alerts from phone", fontSize = 9.sp, color = COLOR_TEXT_SECONDARY)
                }
            }
        }
    }
}

@Composable
private fun DeviceSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = "Device",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        Button(
            onClick = { /* TODO: Radio settings */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(COLOR_TEAL_DIM),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("RD", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Radio Settings",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = COLOR_TEXT_PRIMARY,
                    )
                    Text("Configure radio", fontSize = 9.sp, color = COLOR_TEXT_SECONDARY)
                }
            }
        }
    }
}

@Composable
private fun DisplaySection(highContrastMode: Boolean, onHighContrastModeChange: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = "Display",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        SwitchButton(
            checked = highContrastMode,
            onCheckedChange = onHighContrastModeChange,
            modifier = Modifier.fillMaxWidth(),
            colors = SwitchButtonDefaults.switchButtonColors(uncheckedContainerColor = COLOR_SURFACE2),
            label = {
                Text(
                    text = "Alternate colors",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = COLOR_TEXT_PRIMARY,
                )
            },
            secondaryLabel = {
                Text(
                    text = if (highContrastMode) "Grey theme enabled" else "OLED mode enabled",
                    fontSize = 9.sp,
                    color = COLOR_TEXT_SECONDARY
                )
            }
        )
    }
}

@Composable
private fun MaintenanceSection(onSyncClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = "Maintenance",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        Button(
            onClick = onSyncClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(COLOR_AMBER.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("SY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Force Sync",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = COLOR_TEXT_PRIMARY,
                    )
                    Text("Refresh from phone", fontSize = 9.sp, color = COLOR_TEXT_SECONDARY)
                }
            }
        }
    }
}
