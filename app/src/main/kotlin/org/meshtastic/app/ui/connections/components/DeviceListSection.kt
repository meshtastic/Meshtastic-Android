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
package org.meshtastic.app.ui.connections.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.app.model.DeviceListEntry
import org.meshtastic.core.model.ConnectionState

@Composable
fun List<DeviceListEntry>.DeviceListSection(
    title: String,
    connectionState: ConnectionState,
    selectedDevice: String,
    onSelect: (DeviceListEntry) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((DeviceListEntry) -> Unit)? = null,
) {
    if (isNotEmpty()) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            this@DeviceListSection.forEach { device ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    DeviceListItem(
                        connectionState =
                        connectionState.takeIf { device.fullAddress == selectedDevice }
                            ?: ConnectionState.Disconnected,
                        device = device,
                        onSelect = { onSelect(device) },
                        onDelete = onDelete?.let { delete -> { delete(device) } },
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}
