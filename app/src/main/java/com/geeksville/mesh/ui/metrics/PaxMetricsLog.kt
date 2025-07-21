package com.geeksville.mesh.ui.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.PaxcountProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.util.formatUptime
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import com.geeksville.mesh.model.TimeFrame
import com.geeksville.mesh.ui.metrics.CommonCharts
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ui.common.components.OptionLabel
import com.geeksville.mesh.ui.common.components.SlidingSelector

@Composable
fun PaxMetricsLogScreen(
    metricsViewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by metricsViewModel.state.collectAsStateWithLifecycle()
    val dateFormat = DateFormat.getDateTimeInstance()
    var timeFrame by remember { mutableStateOf(TimeFrame.TWENTY_FOUR_HOURS) }
    // Only show logs that can be decoded as PaxcountProtos.Paxcount
    val paxMetrics = state.paxMetrics.mapNotNull { log ->
        val pax = decodePaxFromLog(log)
        if (pax != null) Pair(log, pax) else null
    }
    // Prepare data for graph
    val now = System.currentTimeMillis() / 1000
    val oldestTime = timeFrame.calculateOldestTime()
    val graphData = paxMetrics.filter { it.first.received_date / 1000 >= oldestTime }
        .map {
            val t = (it.first.received_date / 1000).toInt()
            Triple(t, it.second.ble, it.second.wifi)
        }
        .sortedBy { it.first }
    val totalSeries = graphData.map { it.first to (it.second + it.third) }
    val bleSeries = graphData.map { it.first to it.second }
    val wifiSeries = graphData.map { it.first to it.third }
    val maxValue = (totalSeries.maxOfOrNull { it.second } ?: 1).toFloat().coerceAtLeast(1f)
    val minValue = 0f
    val legendData = listOf(
        LegendData(R.string.pax, Color.Blue),
        LegendData(R.string.ble_devices, Color.Green),
        LegendData(R.string.wifi_devices, Color.Red),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Time frame selector
        SlidingSelector(
            options = TimeFrame.entries.toList(),
            selectedOption = timeFrame,
            onOptionSelected = { timeFrame = it }
        ) { tf: TimeFrame ->
            OptionLabel(stringResource(tf.strRes))
        }
        // Graph
        if (graphData.isNotEmpty()) {
            ChartHeader(graphData.size)
            Legend(legendData = legendData)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(vertical = 8.dp),
                tonalElevation = 2.dp
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val times = totalSeries.map { it.first }
                    val minTime = times.minOrNull() ?: 0
                    val maxTime = times.maxOrNull() ?: 1
                    fun xForTime(t: Int): Float =
                        if (maxTime == minTime) width / 2 else (t - minTime).toFloat() / (maxTime - minTime) * width
                    fun yForValue(v: Int): Float = height - (v - minValue) / (maxValue - minValue) * height
                    // Draw lines
                    fun drawLine(series: List<Pair<Int, Int>>, color: Color) {
                        for (i in 1 until series.size) {
                            drawLine(
                                color = color,
                                start = Offset(xForTime(series[i - 1].first), yForValue(series[i - 1].second)),
                                end = Offset(xForTime(series[i].first), yForValue(series[i].second)),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                    drawLine(totalSeries, Color.Blue)
                    drawLine(bleSeries, Color.Green)
                    drawLine(wifiSeries, Color.Red)
                }
            }
            TimeLabels(
                oldest = totalSeries.first().first,
                newest = totalSeries.last().first
            )
        }
        // List
        if (paxMetrics.isEmpty()) {
            Text(
                text = stringResource(R.string.no_pax_metrics_logs),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(paxMetrics) { (log, pax) ->
                    PaxMetricsItem(log, pax, dateFormat)
                }
            }
        }
    }
}

fun decodePaxFromLog(log: MeshLog): PaxcountProtos.Paxcount? {
    // Try direct base64 or bytes
    try {
        // Try base64
        val base64 = log.raw_message.trim()
        if (base64.matches(Regex("^[A-Za-z0-9+/=\r\n]+$"))) {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            return PaxcountProtos.Paxcount.parseFrom(bytes)
        }
        // Try hex
        if (base64.matches(Regex("^[0-9a-fA-F]+$")) && base64.length % 2 == 0) {
            val bytes = base64.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return PaxcountProtos.Paxcount.parseFrom(bytes)
        }
    } catch (_: Exception) {}
    // Try extracting payload from packet log
    val regex = Regex("payload:\\s*\"([^\"]+)\"")
    val match = regex.find(log.raw_message)
    if (match != null) {
        val payloadEscaped = match.groupValues[1]
        val payloadBytes = unescapeProtoString(payloadEscaped)
        return try {
            PaxcountProtos.Paxcount.parseFrom(payloadBytes)
        } catch (_: Exception) { null }
    }
    return null
}

fun unescapeProtoString(escaped: String): ByteArray {
    val out = mutableListOf<Byte>()
    var i = 0
    while (i < escaped.length) {
        if (escaped[i] == '\\' && i + 3 < escaped.length && escaped[i + 1].isDigit()) {
            // Octal escape: \\ddd
            val octal = escaped.substring(i + 1, i + 4)
            out.add(octal.toInt(8).toByte())
            i += 4
        } else {
            out.add(escaped[i].code.toByte())
            i++
        }
    }
    return out.toByteArray()
}

@Composable
fun PaxMetricsItem(log: MeshLog, pax: PaxcountProtos.Paxcount, dateFormat: DateFormat) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = dateFormat.format(Date(log.received_date)),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        val total = pax.ble + pax.wifi
        val summary = "PAX: $total (B:${pax.ble}  W:${pax.wifi})"
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f, fill = true)
            )
            Text(
                text = stringResource(R.string.uptime) + ": " + formatUptime(pax.uptime),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
} 