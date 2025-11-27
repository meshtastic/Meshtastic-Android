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

package com.geeksville.mesh.ui.connections.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.geeksville.mesh.model.DeviceListEntry
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.ui.component.TitledCard

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
        TitledCard(title = title, modifier = modifier) {
            forEach { device ->
                DeviceListItem(
                    connectionState =
                    connectionState.takeIf { device.fullAddress == selectedDevice } ?: ConnectionState.Disconnected,
                    device = device,
                    onSelect = { onSelect(device) },
                    onDelete = onDelete?.let { delete -> { delete(device) } },
                    modifier = Modifier.Companion,
                )
            }
        }
    }
}
