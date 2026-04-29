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
package org.meshtastic.feature.discovery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.discovery.DiscoveryHistoryDetailViewModel
import org.meshtastic.feature.discovery.ui.component.PresetResultCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryHistoryDetailScreen(
    viewModel: DiscoveryHistoryDetailViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToMap: (Long) -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val presetResults by viewModel.presetResults.collectAsStateWithLifecycle()
    val nodesByPreset by viewModel.nodesByPreset.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) { Icon(MeshtasticIcons.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    val s = session
                    val hasAnyMappableNodes =
                        nodesByPreset.values.flatten().any {
                            it.latitude != null && it.longitude != null && it.latitude != 0.0
                        }
                    if (s != null && (s.userLatitude != 0.0 || hasAnyMappableNodes)) {
                        IconButton(onClick = { onNavigateToMap(s.id) }) {
                            Icon(MeshtasticIcons.Map, contentDescription = "View map")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            session?.let { s -> SessionMetadataCard(s) }

            if (presetResults.isNotEmpty()) {
                Text(text = "Preset Results", style = MaterialTheme.typography.titleMedium)
                presetResults.forEach { result ->
                    PresetResultCard(result = result, nodes = nodesByPreset[result.id].orEmpty())
                }
            }
        }
    }
}

@Composable
private fun SessionMetadataCard(session: DiscoverySessionEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = formatTimestamp(session.timestamp), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            MetadataRow("Status", session.completionStatus.replaceFirstChar { it.uppercase() })
            MetadataRow("Presets scanned", session.presetsScanned)
            MetadataRow("Home preset", session.homePreset)
            MetadataRow("Unique nodes", session.totalUniqueNodes.toString())
            MetadataRow("Total messages", session.totalMessages.toString())
            MetadataRow("Total dwell time", formatDuration(session.totalDwellSeconds))
            session.aiSummary?.let { summary ->
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
