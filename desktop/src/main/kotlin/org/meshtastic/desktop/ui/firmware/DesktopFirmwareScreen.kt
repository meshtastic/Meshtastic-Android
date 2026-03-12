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
package org.meshtastic.desktop.ui.firmware

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.actions
import org.meshtastic.core.resources.check_for_updates
import org.meshtastic.core.resources.connected_device
import org.meshtastic.core.resources.download_firmware
import org.meshtastic.core.resources.firmware_charge_warning
import org.meshtastic.core.resources.firmware_update_title
import org.meshtastic.core.resources.no_device_connected
import org.meshtastic.core.resources.note
import org.meshtastic.core.resources.ready_for_firmware_update
import org.meshtastic.core.resources.update_device
import org.meshtastic.core.resources.update_status

/**
 * Desktop Firmware Update Screen — Shows firmware update status and controls.
 *
 * Simplified desktop UI for firmware updates. Demonstrates the firmware feature in a desktop context without full
 * native DFU integration.
 */
@Suppress("LongMethod") // Placeholder screen — will be replaced with shared KMP implementation
@Composable
fun DesktopFirmwareScreen() {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        // Header
        Text(
            stringResource(Res.string.firmware_update_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Device info
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(Res.string.connected_device),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    stringResource(Res.string.no_device_connected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // Update status
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(Res.string.update_status), style = MaterialTheme.typography.labelMedium)

                Text(
                    stringResource(Res.string.ready_for_firmware_update),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )

                // Progress indicator (placeholder)
                LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))

                Text("0%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Controls
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(Res.string.actions),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Button(onClick = { /* Check for updates */ }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.check_for_updates))
                }

                Button(
                    onClick = { /* Download firmware */ },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = false,
                ) {
                    Text(stringResource(Res.string.download_firmware))
                }

                Button(
                    onClick = { /* Start update */ },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = false,
                ) {
                    Text(stringResource(Res.string.update_device))
                }
            }
        }

        // Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(Res.string.note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    stringResource(Res.string.firmware_charge_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
