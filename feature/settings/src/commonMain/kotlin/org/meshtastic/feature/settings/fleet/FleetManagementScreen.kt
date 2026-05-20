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
package org.meshtastic.feature.settings.fleet

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.fleet_management
import org.meshtastic.core.resources.fleet_management_apply
import org.meshtastic.core.resources.fleet_management_bulk_naming
import org.meshtastic.core.resources.fleet_management_prefix
import org.meshtastic.core.resources.fleet_management_start_index
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.settings.SettingsViewModel

@Suppress("LongMethod")
@Composable
fun FleetManagementScreen(viewModel: SettingsViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val ownedNodes by viewModel.ownedNodes.collectAsStateWithLifecycle(emptyList())
    var prefix by remember { mutableStateOf("VNX") }
    var startIndex by remember { mutableStateOf("1") }

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.fleet_management),
                onNavigateUp = onNavigateUp,
                canNavigateUp = true,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                text = stringResource(Res.string.fleet_management_bulk_naming),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text(stringResource(Res.string.fleet_management_prefix)) },
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                OutlinedTextField(
                    value = startIndex,
                    onValueChange = { startIndex = it },
                    label = { Text(stringResource(Res.string.fleet_management_start_index)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val start = startIndex.toIntOrNull() ?: 1
                    viewModel.bulkRenameFleet(prefix, start)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.fleet_management_apply))
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(ownedNodes) { node ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column {
                            Text(text = node.user.long_name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = node.user.id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
