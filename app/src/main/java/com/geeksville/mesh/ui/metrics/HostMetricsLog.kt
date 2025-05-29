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

package com.geeksville.mesh.ui.metrics

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataArray
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.metrics.CommonCharts.DATE_TIME_FORMAT
import com.geeksville.mesh.util.formatUptime
import java.text.DecimalFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HostMetricsLogScreen(
    metricsViewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by metricsViewModel.state.collectAsStateWithLifecycle()

    val hostMetrics = state.hostMetrics

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(hostMetrics) { telemetry ->
            HostMetricsItem(
                telemetry = telemetry,
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
fun HostMetricsItem(
    modifier: Modifier = Modifier,
    telemetry: TelemetryProtos.Telemetry
) {
    val hostMetrics = telemetry.hostMetrics
    val time = telemetry.time * CommonCharts.MS_PER_SEC
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(onClick = { /* Handle click */ }),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DataArray,
                contentDescription = null,
                modifier = Modifier.width(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            SelectionContainer {

                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        text = DATE_TIME_FORMAT.format(time),
                        style = TextStyle(fontWeight = FontWeight.Bold),
                        fontSize = MaterialTheme.typography.labelLarge.fontSize
                    )
                    LogLine(
                        label = stringResource(R.string.uptime),
                        value = formatUptime(hostMetrics.uptimeSeconds),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LogLine(
                        label = stringResource(R.string.free_memory),
                        value = formatBytes(hostMetrics.freememBytes),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LogLine(
                        label = stringResource(R.string.disk_free) + " 1",
                        value = formatBytes(hostMetrics.diskfree1Bytes),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (hostMetrics.hasDiskfree2Bytes()) {
                        LogLine(
                            label = stringResource(R.string.disk_free) + " 2",
                            value = formatBytes(hostMetrics.diskfree2Bytes),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (hostMetrics.hasDiskfree3Bytes()) {
                        LogLine(
                            label = stringResource(R.string.disk_free) + " 3",
                            value = formatBytes(hostMetrics.diskfree3Bytes),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    LogLine(
                        label = stringResource(R.string.load) + " 1",
                        value = (hostMetrics.load1 / 100.0).toString(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LinearProgressIndicator(
                        progress = { hostMetrics.load1 / 100.0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    LogLine(
                        label = stringResource(R.string.load) + " 5",
                        value = (hostMetrics.load5 / 100.0).toString(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LinearProgressIndicator(
                        progress = { hostMetrics.load5 / 100.0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    LogLine(
                        label = stringResource(R.string.load) + " 15",
                        value = (hostMetrics.load15 / 100.0).toString(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LinearProgressIndicator(
                        progress = { hostMetrics.load15 / 100.0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    if (hostMetrics.hasUserString()) {
                        LogLine(
                            label = stringResource(R.string.user_string),
                            value = hostMetrics.userString,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogLine(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
        )
        Text(
            text = value,
        )
    }
}

const val BYTES_IN_KB = 1024.0
const val BYTES_IN_MB = BYTES_IN_KB * 1024.0
const val BYTES_IN_GB = BYTES_IN_MB * 1024.0

fun formatBytes(bytes: Long, decimalPlaces: Int = 2): String {
    val formatter = DecimalFormat().apply {
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
    val hostMetrics = TelemetryProtos.HostMetrics.newBuilder()
        .setUptimeSeconds(3600)
        .setFreememBytes(2048000)
        .setDiskfree1Bytes(104857600)
        .setDiskfree2Bytes(2097915200)
        .setDiskfree3Bytes(44444)
        .setLoad1(30)
        .setLoad5(75)
        .setLoad15(19)
        .setUserString("test")
        .build()
    val logs = TelemetryProtos.Telemetry.newBuilder()
        .setTime((System.currentTimeMillis() / 1000L).toInt())
        .setHostMetrics(hostMetrics)
        .build()
    AppTheme {
        HostMetricsItem(telemetry = logs)
    }
}
