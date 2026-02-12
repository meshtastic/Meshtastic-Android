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
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.model.util.nowSeconds
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.disk_free_indexed
import org.meshtastic.core.strings.free_memory
import org.meshtastic.core.strings.load_indexed
import org.meshtastic.core.strings.uptime
import org.meshtastic.core.strings.user_string
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.DataArray
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.proto.HostMetrics
import org.meshtastic.proto.Telemetry
import java.text.DecimalFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HostMetricsLogScreen(metricsViewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by metricsViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        metricsViewModel.effects.collect { effect ->
            when (effect) {
                is NodeRequestEffect.ShowFeedback -> {
                    @Suppress("SpreadOperator")
                    snackbarHostState.showSnackbar(getString(effect.resource, *effect.args.toTypedArray()))
                }
            }
        }
    }

    val hostMetrics = state.hostMetrics

    Scaffold(
        topBar = {
            MainAppBar(
                title = state.node?.user?.long_name ?: "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (!state.isLocal) {
                        IconButton(onClick = { metricsViewModel.requestTelemetry(TelemetryType.HOST) }) {
                            Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                        }
                    }
                },
                onClickChip = {},
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(hostMetrics) { telemetry -> HostMetricsItem(telemetry = telemetry) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod", "MagicNumber")
@Composable
fun HostMetricsItem(modifier: Modifier = Modifier, telemetry: Telemetry) {
    val hostMetrics = telemetry.host_metrics
    val time = telemetry.time.toLong() * CommonCharts.MS_PER_SEC
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(onClick = { /* Handle click */ }),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = MeshtasticIcons.DataArray, contentDescription = null, modifier = Modifier.width(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            SelectionContainer {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        text = DATE_TIME_FORMAT.format(time),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    hostMetrics?.uptime_seconds?.let {
                        LogLine(
                            label = stringResource(Res.string.uptime),
                            value = formatUptime(it),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    hostMetrics?.freemem_bytes?.let {
                        LogLine(
                            label = stringResource(Res.string.free_memory),
                            value = formatBytes(it),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    hostMetrics?.diskfree1_bytes?.let {
                        LogLine(
                            label = stringResource(Res.string.disk_free_indexed, 1),
                            value = formatBytes(it),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    hostMetrics?.diskfree2_bytes?.let {
                        LogLine(
                            label = stringResource(Res.string.disk_free_indexed, 2),
                            value = formatBytes(it),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    hostMetrics?.diskfree3_bytes?.let {
                        LogLine(
                            label = stringResource(Res.string.disk_free_indexed, 3),
                            value = formatBytes(it),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    hostMetrics?.load1?.let {
                        LogLine(
                            label = stringResource(Res.string.load_indexed, 1),
                            value = (hostMetrics.load1 / 100.0).toString(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LinearProgressIndicator(
                            progress = { hostMetrics.load1 / 10000.0f },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            color = ProgressIndicatorDefaults.linearColor,
                            trackColor = ProgressIndicatorDefaults.linearTrackColor,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                    }
                    hostMetrics?.load5?.let {
                        LogLine(
                            label = stringResource(Res.string.load_indexed, 5),
                            value = (hostMetrics.load5 / 100.0).toString(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LinearProgressIndicator(
                            progress = { hostMetrics.load5 / 10000.0f },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            color = ProgressIndicatorDefaults.linearColor,
                            trackColor = ProgressIndicatorDefaults.linearTrackColor,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                    }
                    hostMetrics?.load15?.let {
                        LogLine(
                            label = stringResource(Res.string.load_indexed, 15),
                            value = (hostMetrics.load15 / 100.0).toString(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LinearProgressIndicator(
                            progress = { hostMetrics.load15 / 10000.0f },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            color = ProgressIndicatorDefaults.linearColor,
                            trackColor = ProgressIndicatorDefaults.linearTrackColor,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                    }
                    hostMetrics?.user_string?.let {
                        Text(text = stringResource(Res.string.user_string), style = MaterialTheme.typography.bodyMedium)
                        Text(text = it, style = TextStyle(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        }
    }
}

@Composable
fun LogLine(modifier: Modifier = Modifier, label: String, value: String) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label)
        Text(text = value)
    }
}

const val BYTES_IN_KB = 1024.0
const val BYTES_IN_MB = BYTES_IN_KB * 1024.0
const val BYTES_IN_GB = BYTES_IN_MB * 1024.0

fun formatBytes(bytes: Long, decimalPlaces: Int = 2): String {
    val formatter =
        DecimalFormat().apply {
            maximumFractionDigits = decimalPlaces
            minimumFractionDigits = 0
            isGroupingUsed = false
        }
    return when {
        bytes < 0 -> "N/A" // Handle negative bytes gracefully
        bytes == 0L -> "0 B"
        bytes >= BYTES_IN_GB -> "${formatter.format(bytes / BYTES_IN_GB)} GB"
        bytes >= BYTES_IN_MB -> "${formatter.format(bytes / BYTES_IN_MB)} MB"
        bytes >= BYTES_IN_KB -> "${formatter.format(bytes / BYTES_IN_KB)} KB"
        else -> "$bytes B"
    }
}

@Suppress("MagicNumber")
@PreviewLightDark
@Composable
private fun HostMetricsItemPreview() {
    val hostMetrics =
        HostMetrics(
            uptime_seconds = 3600,
            freemem_bytes = 2048000,
            diskfree1_bytes = 104857600,
            diskfree2_bytes = 2097915200,
            diskfree3_bytes = 44444,
            load1 = 30,
            load5 = 75,
            load15 = 19,
            user_string = "test",
        )
    val logs = Telemetry(time = nowSeconds.toInt(), host_metrics = hostMetrics)
    AppTheme { HostMetricsItem(telemetry = logs) }
}
