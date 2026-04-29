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
@file:Suppress("MagicNumber")

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.database.entity.DiscoveredNodeEntity
import org.meshtastic.core.database.entity.DiscoveryPresetResultEntity
import org.meshtastic.core.database.entity.DiscoverySessionEntity
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Share
import org.meshtastic.feature.discovery.DiscoverySummaryViewModel
import org.meshtastic.feature.discovery.export.ExportResult
import org.meshtastic.feature.discovery.ui.component.PresetResultCard

@Composable
fun DiscoverySummaryScreen(
    viewModel: DiscoverySummaryViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToMap: (Long) -> Unit,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val presetResults by viewModel.presetResults.collectAsStateWithLifecycle()
    val nodesByPreset by viewModel.nodesByPreset.collectAsStateWithLifecycle()
    val algorithmicSummary by viewModel.algorithmicSummary.collectAsStateWithLifecycle()
    val aiSummary by viewModel.aiSummary.collectAsStateWithLifecycle()
    val presetAiSummaries by viewModel.presetAiSummaries.collectAsStateWithLifecycle()
    val isGeneratingAi by viewModel.isGeneratingAi.collectAsStateWithLifecycle()
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()

    LaunchedEffect(exportResult) {
        when (exportResult) {
            is ExportResult.Success -> {
                // TODO: Wire platform share intent (Android) / file-save dialog (Desktop)
                viewModel.clearExportResult()
            }
            is ExportResult.Error -> {
                // TODO: Show snackbar with error message
                viewModel.clearExportResult()
            }
            null -> {
                /* no-op */
            }
        }
    }

    DiscoverySummaryContent(
        session = session,
        presetResults = presetResults,
        nodesByPreset = nodesByPreset,
        algorithmicSummary = algorithmicSummary,
        aiSummary = aiSummary,
        presetAiSummaries = presetAiSummaries,
        isGeneratingAi = isGeneratingAi,
        onNavigateUp = onNavigateUp,
        onNavigateToMap = onNavigateToMap,
        onExport = viewModel::exportReport,
        onRerunAnalysis = viewModel::rerunAnalysis,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun DiscoverySummaryContent(
    session: DiscoverySessionEntity?,
    presetResults: List<DiscoveryPresetResultEntity>,
    nodesByPreset: Map<Long, List<DiscoveredNodeEntity>>,
    algorithmicSummary: String?,
    aiSummary: String?,
    presetAiSummaries: Map<Long, String>,
    isGeneratingAi: Boolean,
    onNavigateUp: () -> Unit,
    onNavigateToMap: (Long) -> Unit,
    onExport: () -> Unit,
    onRerunAnalysis: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Summary") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) { Icon(MeshtasticIcons.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (session != null) {
                        IconButton(onClick = { onNavigateToMap(session.id) }) {
                            Icon(MeshtasticIcons.Map, contentDescription = "View map")
                        }
                    }
                    IconButton(onClick = onExport) { Icon(MeshtasticIcons.Share, contentDescription = "Export report") }
                },
            )
        },
    ) { padding ->
        if (session == null) {
            CircularProgressIndicator(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item { SessionOverviewCard(session = session) }

            items(presetResults, key = { it.id }) { result ->
                PresetResultCard(
                    result = result,
                    nodes = nodesByPreset[result.id].orEmpty(),
                    aiSummary = presetAiSummaries[result.id],
                )
            }

            item {
                AiSummaryCard(
                    aiSummary = aiSummary ?: session.aiSummary,
                    algorithmicSummary = algorithmicSummary,
                    isGenerating = isGeneratingAi,
                    onRerunAnalysis = onRerunAnalysis,
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SessionOverviewCard(session: DiscoverySessionEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Session Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            StatRow(label = "Date", value = DateFormatter.formatDateTime(session.timestamp))
            StatRow(label = "Total unique nodes", value = session.totalUniqueNodes.toString())
            StatRow(label = "Total dwell time", value = formatDuration(session.totalDwellSeconds))
            StatRow(label = "Status", value = session.completionStatus.replaceFirstChar { it.uppercase() })
            StatRow(
                label = "Channel utilization",
                value = "${NumberFormatter.format(session.avgChannelUtilization, 1)}%",
            )
        }
    }
}

@Composable
private fun AiSummaryCard(
    aiSummary: String?,
    algorithmicSummary: String?,
    isGenerating: Boolean,
    onRerunAnalysis: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRerunAnalysis) {
                        Icon(
                            MeshtasticIcons.Refresh,
                            contentDescription = "Re-run analysis",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val summaryText = aiSummary ?: algorithmicSummary ?: "AI analysis not available"

            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
internal fun StatRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

internal fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
}
