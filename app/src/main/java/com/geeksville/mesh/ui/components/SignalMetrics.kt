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

package com.geeksville.mesh.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.Surface
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.R
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.TimeFrame
import com.geeksville.mesh.ui.components.CommonCharts.MS_PER_SEC
import com.geeksville.mesh.ui.components.CommonCharts.DATE_TIME_FORMAT
import com.geeksville.mesh.util.GraphUtil.plotPoint

@Suppress("MagicNumber")
private enum class Metric(val color: Color, val min: Float, val max: Float) {
    SNR(Color.Green, -20f, 12f), /* Selected 12 as the max to get 4 equal vertical sections. */
    RSSI(Color.Blue, -140f, -20f);
    /**
     * Difference between the metrics `max` and `min` values.
     */
    fun difference() = max - min
}
private val LEGEND_DATA = listOf(
    LegendData(nameRes = R.string.rssi, color = Metric.RSSI.color),
    LegendData(nameRes = R.string.snr, color = Metric.SNR.color)
)

@Composable
fun SignalMetricsScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var displayInfoDialog by remember { mutableStateOf(false) }
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    val data = state.signalMetricsFiltered(selectedTimeFrame)

    Column {

        if (displayInfoDialog) {
            LegendInfoDialog(
                pairedRes = listOf(
                    Pair(R.string.snr, R.string.snr_definition),
                    Pair(R.string.rssi, R.string.rssi_definition)
                ),
                onDismiss = { displayInfoDialog = false }
            )
        }

        SignalMetricsChart(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.33f),
            meshPackets = data.reversed(),
            selectedTimeFrame,
            promptInfoDialog = { displayInfoDialog = true }
        )

        SlidingSelector(
            TimeFrame.entries.toList(),
            selectedTimeFrame,
            onOptionSelected = { viewModel.setTimeFrame(it) }
        ) {
            OptionLabel(stringResource(it.strRes))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(data) { meshPacket -> SignalMetricsCard(meshPacket) }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun SignalMetricsChart(
    modifier: Modifier = Modifier,
    meshPackets: List<MeshPacket>,
    selectedTime: TimeFrame,
    promptInfoDialog: () -> Unit
) {
    ChartHeader(amount = meshPackets.size)
    if (meshPackets.isEmpty()) {
        return
    }

    val (oldest, newest) = remember(key1 = meshPackets) {
        Pair(
            meshPackets.minBy { it.rxTime },
            meshPackets.maxBy { it.rxTime }
        )
    }
    val timeDiff = newest.rxTime - oldest.rxTime

    TimeLabels(
        oldest = oldest.rxTime,
        newest = newest.rxTime
    )

    Spacer(modifier = Modifier.height(16.dp))

    val graphColor = MaterialTheme.colors.onSurface
    val snrDiff = Metric.SNR.difference()
    val rssiDiff = Metric.RSSI.difference()

    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val dp by remember(key1 = selectedTime) {
        mutableStateOf(selectedTime.dp(screenWidth, time = (newest.rxTime - oldest.rxTime).toLong()))
    }

    Row {
        YAxisLabels(
            modifier = modifier.weight(weight = .1f),
            Metric.RSSI.color,
            minValue = Metric.RSSI.min,
            maxValue = Metric.RSSI.max,
        )
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier
                .horizontalScroll(state = scrollState, reverseScrolling = true)
                .weight(1f)
        ) {
            HorizontalLinesOverlay(
                modifier.width(dp),
                lineColors = List(size = 5) { graphColor },
            )

            TimeAxisOverlay(
                modifier.width(dp),
                oldest = oldest.rxTime,
                newest = newest.rxTime,
                selectedTime.lineInterval()
            )

            /* Plot SNR and RSSI */
            Canvas(modifier = modifier.width(dp)) {
                val width = size.width
                /* Plot */
                for (packet in meshPackets) {

                    val xRatio = (packet.rxTime - oldest.rxTime).toFloat() / timeDiff
                    val x = xRatio * width

                    /* SNR */
                    plotPoint(
                        drawContext = drawContext,
                        color = Metric.SNR.color,
                        x = x,
                        value = packet.rxSnr - Metric.SNR.min,
                        divisor = snrDiff
                    )

                    /* RSSI */
                    plotPoint(
                        drawContext = drawContext,
                        color = Metric.RSSI.color,
                        x = x,
                        value = packet.rxRssi - Metric.RSSI.min,
                        divisor = rssiDiff
                    )
                }
            }
        }
        YAxisLabels(
            modifier = modifier.weight(weight = .1f),
            Metric.SNR.color,
            minValue = Metric.SNR.min,
            maxValue = Metric.SNR.max,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Legend(legendData = LEGEND_DATA, promptInfoDialog = promptInfoDialog)

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun SignalMetricsCard(meshPacket: MeshPacket) {
    val time = meshPacket.rxTime * MS_PER_SEC
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Surface {
            SelectionContainer {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    /* Data */
                    Box(
                        modifier = Modifier
                            .weight(weight = 5f)
                            .height(IntrinsicSize.Min)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                        ) {
                            /* Time */
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = DATE_TIME_FORMAT.format(time),
                                    style = TextStyle(fontWeight = FontWeight.Bold),
                                    fontSize = MaterialTheme.typography.button.fontSize
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            /* SNR and RSSI */
                            SnrAndRssi(meshPacket.rxSnr, meshPacket.rxRssi)
                        }
                    }

                    /* Signal Indicator */
                    Box(
                        modifier = Modifier
                            .weight(weight = 3f)
                            .height(IntrinsicSize.Max)
                    ) {
                        LoraSignalIndicator(meshPacket.rxSnr, meshPacket.rxRssi)
                    }
                }
            }
        }
    }
}
