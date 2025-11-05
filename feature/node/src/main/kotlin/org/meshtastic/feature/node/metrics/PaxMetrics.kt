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

package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.model.util.formatUptime
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.OptionLabel
import org.meshtastic.core.ui.component.SlidingSelector
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.PaxcountProtos
import org.meshtastic.proto.Portnums.PortNum
import java.text.DateFormat
import java.util.Date
import org.meshtastic.core.strings.R as Res

private const val CHART_WEIGHT = 1f
private const val Y_AXIS_WEIGHT = 0.1f
private const val CHART_WIDTH_RATIO = CHART_WEIGHT / (CHART_WEIGHT + Y_AXIS_WEIGHT + Y_AXIS_WEIGHT)

private enum class PaxSeries(val color: Color, val legendRes: Int) {
    PAX(Color.Black, Res.string.pax),
    BLE(Color.Cyan, Res.string.ble_devices),
    WIFI(Color.Green, Res.string.wifi_devices),
}

@Suppress("LongMethod")
@Composable
private fun PaxMetricsChart(
    modifier: Modifier = Modifier,
    totalSeries: List<Pair<Int, Int>>,
    bleSeries: List<Pair<Int, Int>>,
    wifiSeries: List<Pair<Int, Int>>,
    minValue: Float,
    maxValue: Float,
    timeFrame: TimeFrame,
) {
    if (totalSeries.isEmpty()) return
    val scrollState = rememberScrollState()
    val screenWidth = LocalWindowInfo.current.containerSize.width
    val times = totalSeries.map { it.first }
    val minTime = times.minOrNull() ?: 0
    val maxTime = times.maxOrNull() ?: 1
    val timeDiff = maxTime - minTime
    val dp = remember(timeFrame, screenWidth, timeDiff) { timeFrame.dp(screenWidth, time = timeDiff.toLong()) }
    // Calculate visible time range based on scroll position and chart width
    val visibleTimeRange = run {
        val totalWidthPx = with(LocalDensity.current) { dp.toPx() }
        val scrollPx = scrollState.value.toFloat()
        val visibleWidthPx = screenWidth * CHART_WIDTH_RATIO
        val leftRatio = (scrollPx / totalWidthPx).coerceIn(0f, 1f)
        val rightRatio = ((scrollPx + visibleWidthPx) / totalWidthPx).coerceIn(0f, 1f)
        val visibleOldest = minTime + (timeDiff * leftRatio).toInt()
        val visibleNewest = minTime + (timeDiff * rightRatio).toInt()
        visibleOldest to visibleNewest
    }
    TimeLabels(oldest = visibleTimeRange.first, newest = visibleTimeRange.second)
    Spacer(modifier = Modifier.height(16.dp))
    Row(modifier = modifier.fillMaxWidth().fillMaxHeight(fraction = 0.33f)) {
        YAxisLabels(
            modifier = Modifier.weight(Y_AXIS_WEIGHT).fillMaxHeight().padding(start = 8.dp),
            labelColor = MaterialTheme.colorScheme.onSurface,
            minValue = minValue,
            maxValue = maxValue,
        )
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier.horizontalScroll(state = scrollState, reverseScrolling = true).weight(CHART_WEIGHT),
        ) {
            HorizontalLinesOverlay(modifier.width(dp), lineColors = List(size = 5) { Color.LightGray })
            TimeAxisOverlay(modifier.width(dp), oldest = minTime, newest = maxTime, timeFrame.lineInterval())
            Canvas(modifier = Modifier.width(dp).fillMaxHeight()) {
                val width = size.width
                val height = size.height
                fun xForTime(t: Int): Float =
                    if (maxTime == minTime) width / 2 else (t - minTime).toFloat() / (maxTime - minTime) * width
                fun yForValue(v: Int): Float = height - (v - minValue) / (maxValue - minValue) * height
                fun drawLine(series: List<Pair<Int, Int>>, color: Color) {
                    for (i in 1 until series.size) {
                        drawLine(
                            color = color,
                            start = Offset(xForTime(series[i - 1].first), yForValue(series[i - 1].second)),
                            end = Offset(xForTime(series[i].first), yForValue(series[i].second)),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }
                drawLine(bleSeries, PaxSeries.BLE.color)
                drawLine(wifiSeries, PaxSeries.WIFI.color)
                drawLine(totalSeries, PaxSeries.PAX.color)
            }
        }
        YAxisLabels(
            modifier = Modifier.weight(Y_AXIS_WEIGHT).fillMaxHeight().padding(end = 8.dp),
            labelColor = MaterialTheme.colorScheme.onSurface,
            minValue = minValue,
            maxValue = maxValue,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
@Suppress("MagicNumber", "LongMethod")
fun PaxMetricsScreen(metricsViewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by metricsViewModel.state.collectAsStateWithLifecycle()
    val dateFormat = DateFormat.getDateTimeInstance()
    var timeFrame by remember { mutableStateOf(TimeFrame.TWENTY_FOUR_HOURS) }
    // Only show logs that can be decoded as PaxcountProtos.Paxcount
    val paxMetrics =
        state.paxMetrics.mapNotNull { log ->
            val pax = decodePaxFromLog(log)
            if (pax != null) {
                Pair(log, pax)
            } else {
                null
            }
        }
    // Prepare data for graph
    val oldestTime = timeFrame.calculateOldestTime()
    val graphData =
        paxMetrics
            .filter { it.first.received_date / 1000 >= oldestTime }
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
    val legendData =
        listOf(
            LegendData(PaxSeries.PAX.legendRes, PaxSeries.PAX.color, environmentMetric = null),
            LegendData(PaxSeries.BLE.legendRes, PaxSeries.BLE.color, environmentMetric = null),
            LegendData(PaxSeries.WIFI.legendRes, PaxSeries.WIFI.color, environmentMetric = null),
        )

    Scaffold(
        topBar = {
            MainAppBar(
                title = state.node?.user?.longName ?: "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Time frame selector
            SlidingSelector(
                options = TimeFrame.entries.toList(),
                selectedOption = timeFrame,
                onOptionSelected = { timeFrame = it },
            ) { tf: TimeFrame ->
                OptionLabel(stringResource(tf.strRes))
            }
            // Graph
            if (graphData.isNotEmpty()) {
                ChartHeader(graphData.size)
                Legend(legendData = legendData)
                PaxMetricsChart(
                    totalSeries = totalSeries,
                    bleSeries = bleSeries,
                    wifiSeries = wifiSeries,
                    minValue = minValue,
                    maxValue = maxValue,
                    timeFrame = timeFrame,
                )
            }
            // List
            if (paxMetrics.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_pax_metrics_logs),
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(paxMetrics) { (log, pax) -> PaxMetricsItem(log, pax, dateFormat) }
                }
            }
        }
    }
}

@Suppress("MagicNumber", "CyclomaticComplexMethod")
fun decodePaxFromLog(log: MeshLog): PaxcountProtos.Paxcount? {
    var result: PaxcountProtos.Paxcount? = null
    // First, try to parse from the binary fromRadio field (robust, like telemetry)
    try {
        val packet = log.fromRadio.packet
        if (packet != null && packet.hasDecoded() && packet.decoded.portnumValue == PortNum.PAXCOUNTER_APP_VALUE) {
            val pax = PaxcountProtos.Paxcount.parseFrom(packet.decoded.payload)
            if (pax.ble != 0 || pax.wifi != 0 || pax.uptime != 0) result = pax
        }
    } catch (e: com.google.protobuf.InvalidProtocolBufferException) {
        android.util.Log.e("PaxMetrics", "Failed to parse Paxcount from binary data", e)
    } catch (e: IllegalArgumentException) {
        android.util.Log.e("PaxMetrics", "Invalid argument while parsing Paxcount from binary data", e)
    }
    // Fallback: Try direct base64 or bytes from raw_message
    if (result == null) {
        try {
            val base64 = log.raw_message.trim()
            if (base64.matches(Regex("^[A-Za-z0-9+/=\r\n]+$"))) {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val pax = PaxcountProtos.Paxcount.parseFrom(bytes)
                result = pax
            } else if (base64.matches(Regex("^[0-9a-fA-F]+$")) && base64.length % 2 == 0) {
                val bytes = base64.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val pax = PaxcountProtos.Paxcount.parseFrom(bytes)
                result = pax
            }
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("PaxMetrics", "Invalid Base64 or hex input", e)
        } catch (e: com.google.protobuf.InvalidProtocolBufferException) {
            android.util.Log.e("PaxMetrics", "Failed to parse Paxcount from decoded data", e)
        }
    }
    return result
}

@Suppress("MagicNumber")
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = dateFormat.format(Date(log.received_date)),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        val total = pax.ble + pax.wifi
        val summary = "PAX: $total (B:${pax.ble}  W:${pax.wifi})"
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f, fill = true),
            )
            Text(
                text = stringResource(Res.string.uptime) + ": " + formatUptime(pax.uptime),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}
