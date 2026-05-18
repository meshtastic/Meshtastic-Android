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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.view_details
import org.meshtastic.core.ui.component.LastHeardInfo
import org.meshtastic.core.ui.component.MaterialBatteryInfo
import org.meshtastic.core.ui.component.SignalInfo

/**
 * A modal bottom sheet showing a compact summary of a node when tapped on the map. Provides quick info (name, last
 * heard, battery, signal) and a button to navigate to full details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NodeInfoSheet(node: Node, onDismiss: () -> Unit, onViewDetails: (Int) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            // Node name
            Text(
                text = node.user.long_name.ifBlank { node.user.short_name },
                style = MaterialTheme.typography.titleLarge,
            )
            if (node.user.long_name.isNotBlank() && node.user.short_name.isNotBlank()) {
                Text(
                    text = node.user.short_name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Info row: last heard, battery, signal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LastHeardInfo(lastHeard = node.lastHeard, showLabel = false)
                MaterialBatteryInfo(level = node.batteryLevel)
                SignalInfo(node = node)
            }

            Spacer(Modifier.height(24.dp))

            // View details button
            Button(onClick = { onViewDetails(node.num) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.view_details))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
