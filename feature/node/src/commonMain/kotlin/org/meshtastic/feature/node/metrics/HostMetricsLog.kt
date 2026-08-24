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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.disk_free_indexed
import org.meshtastic.core.resources.free_memory
import org.meshtastic.core.resources.host_metrics_log
import org.meshtastic.core.resources.load_indexed
import org.meshtastic.core.resources.uptime
import org.meshtastic.core.resources.user_string
import org.meshtastic.core.ui.theme.GraphColors
import org.meshtastic.proto.Telemetry

/**
 * Full-screen host metrics log with chart and card list, built on [BaseMetricScreen]. Shows load averages and free
 * memory over time with time-frame filtering, chart expand/collapse, and card-to-chart synchronisation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod")
@Composable
fun HostMetricsLogScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by viewModel.availableTimeFrames.collectAsStateWithLifecycle()

    val threshold = timeFrame.timeThreshold()
    val filteredData =
        remember(state.hostMetrics, threshold) { state.hostMetrics.filter { it.time.toLong() >= threshold } }

    BaseMetricScreen(
        onNavigateUp = onNavigateUp,
        telemetryType = TelemetryType.HOST,
        titleRes = Res.string.host_metrics_log,
        nodeName = state.node?.user?.long_name ?: "",
        data = filteredData,
        timeProvider = { it.time.toDouble() },
        infoData = HOST_METRICS_INFO_DATA,
        onRequestTelemetry = { viewModel.requestTelemetry(TelemetryType.HOST) },
        controlPart = {
            TimeFrameSelector(
                selectedTimeFrame = timeFrame,
                availableTimeFrames = availableTimeFrames,
                onTimeFrameSelected = viewModel::setTimeFrame,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
        chartPart = { chartModifier, selectedX, vicoScrollState, onPointSelected ->
            HostMetricsChart(
                modifier = chartModifier,
                data = filteredData.reversed(),
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
            )
        },
        listPart = { listModifier, selectedX, lazyListState, onCardClick ->
            LazyColumn(modifier = listModifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(filteredData, key = { index, t -> "${t.time}_$index" }) { _, telemetry ->
                    HostMetricsCard(
                        telemetry = telemetry,
                        isSelected = telemetry.time.toDouble() == selectedX,
                        onClick = { onCardClick(telemetry.time.toDouble()) },
                    )
                }
            }
        },
    )
}

/** A selectable card summarising a single host metrics telemetry snapshot. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostMetricsCard(telemetry: Telemetry, isSelected: Boolean, onClick: () -> Unit) {
    val hostMetrics = telemetry.host_metrics
    val time = DateFormatter.formatDateTime(telemetry.time.toLong() * MS_PER_SEC)
    var expanded by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .combinedClickable(onClick = onClick, onLongClick = { expanded = true }),
            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            colors =
            CardDefaults.cardColors(
                containerColor =
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            HostMetricsCardContent(time = time, hostMetrics = hostMetrics)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { DeleteItem { expanded = false } }
    }
}

/** Card body showing timestamp, load averages with progress bars, memory, disk, and uptime. */
@Composable
private fun HostMetricsCardContent(time: String, hostMetrics: org.meshtastic.proto.HostMetrics?) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(text = time, style = MaterialTheme.typography.titleMediumEmphasized, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        hostMetrics?.uptime_seconds?.let {
            LogLine(label = stringResource(Res.string.uptime), value = formatUptime(it))
        }
        hostMetrics?.freemem_bytes?.let {
            LogLine(label = stringResource(Res.string.free_memory), value = formatBytes(it))
        }

        // Disk free rows
        hostMetrics?.diskfree1_bytes?.let {
            LogLine(label = stringResource(Res.string.disk_free_indexed, 1), value = formatBytes(it))
        }
        hostMetrics?.diskfree2_bytes?.let {
            LogLine(label = stringResource(Res.string.disk_free_indexed, 2), value = formatBytes(it))
        }
        hostMetrics?.diskfree3_bytes?.let {
            LogLine(label = stringResource(Res.string.disk_free_indexed, 3), value = formatBytes(it))
        }

        // Load averages with coloured indicators and progress bars
        hostMetrics?.load1?.let {
            LoadRow(label = stringResource(Res.string.load_indexed, 1), value = it, color = GraphColors.Blue)
        }
        hostMetrics?.load5?.let {
            LoadRow(label = stringResource(Res.string.load_indexed, 5), value = it, color = GraphColors.Green)
        }
        hostMetrics?.load15?.let {
            LoadRow(label = stringResource(Res.string.load_indexed, 15), value = it, color = GraphColors.Orange)
        }

        hostMetrics?.user_string?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = stringResource(Res.string.user_string), style = MaterialTheme.typography.bodyMedium)
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A load average row with coloured metric indicator, value text, and progress bar. */
@Composable
private fun LoadRow(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        MetricIndicator(color)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = formatString("%s: %.2f", label, value / 100.0),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
    }
    LinearProgressIndicator(
        progress = { (value / 10000.0f).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        color = color,
        trackColor = ProgressIndicatorDefaults.linearTrackColor,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
    )
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

private const val DECIMAL_FACTOR_1 = 10.0
private const val DECIMAL_FACTOR_2 = 100.0

fun formatBytes(bytes: Long, decimalPlaces: Int = 2): String {
    fun formatValue(value: Double): String {
        // Simple decimal formatting without java.text.DecimalFormat
        val factor =
            when (decimalPlaces) {
                0 -> 1.0
                1 -> DECIMAL_FACTOR_1
                else -> DECIMAL_FACTOR_2
            }
        val rounded = kotlin.math.round(value * factor) / factor
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
    return when {
        bytes < 0 -> "N/A"
        bytes == 0L -> "0 B"
        bytes >= BYTES_IN_GB -> "${formatValue(bytes / BYTES_IN_GB)} GB"
        bytes >= BYTES_IN_MB -> "${formatValue(bytes / BYTES_IN_MB)} MB"
        bytes >= BYTES_IN_KB -> "${formatValue(bytes / BYTES_IN_KB)} KB"
        else -> "$bytes B"
    }
}
