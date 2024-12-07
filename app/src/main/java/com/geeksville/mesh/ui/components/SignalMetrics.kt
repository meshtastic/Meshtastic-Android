/*
 * Copyright (c) 2024 Meshtastic LLC
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

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.R
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.components.CommonCharts.MS_PER_SEC
import com.geeksville.mesh.ui.components.CommonCharts.LINE_LIMIT
import com.geeksville.mesh.ui.components.CommonCharts.TEXT_PAINT_ALPHA
import com.geeksville.mesh.ui.components.CommonCharts.LEFT_LABEL_SPACING
import com.geeksville.mesh.ui.components.CommonCharts.DATE_TIME_FORMAT

private val METRICS_COLORS = listOf(Color.Green, Color.Blue)

@Suppress("MagicNumber")
private enum class Metric(val min: Float, val max: Float) {
    SNR(-20f, 12f), /* Selected 12 as the max to get 4 equal vertical sections. */
    RSSI(-140f, -20f);
    /**
     * Difference between the metrics `max` and `min` values.
     */
    fun difference() = max - min
}
private val LEGEND_DATA = listOf(
    LegendData(nameRes = R.string.rssi, color = METRICS_COLORS[Metric.RSSI.ordinal]),
    LegendData(nameRes = R.string.snr, color = METRICS_COLORS[Metric.SNR.ordinal])
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
            promptInfoDialog = { displayInfoDialog = true }
        )

        MetricsTimeSelector(
            selectedTimeFrame,
            onOptionSelected = { viewModel.setTimeFrame(it) }
        ) {
            TimeLabel(stringResource(it.strRes))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(data) { meshPacket -> SignalMetricsCard(meshPacket) }
        }
    }
}

@Composable
private fun SignalMetricsChart(
    modifier: Modifier = Modifier,
    meshPackets: List<MeshPacket>,
    promptInfoDialog: () -> Unit
) {

    ChartHeader(amount = meshPackets.size)
    if (meshPackets.isEmpty()) {
        return
    }

    TimeLabels(
        oldest = meshPackets.first().rxTime,
        newest = meshPackets.last().rxTime
    )

    Spacer(modifier = Modifier.height(16.dp))

    val graphColor = MaterialTheme.colors.onSurface
    val snrDiff = Metric.SNR.difference()
    val rssiDiff = Metric.RSSI.difference()

    Box(contentAlignment = Alignment.TopStart) {

        ChartOverlay(
            modifier = modifier,
            lineColors = List(size = 5) { graphColor },
            labelColor = METRICS_COLORS[Metric.SNR.ordinal],
            minValue = Metric.SNR.min,
            maxValue = Metric.SNR.max,
            leaveSpace = true
        )
        LeftYLabels(modifier = modifier, labelColor = METRICS_COLORS[Metric.RSSI.ordinal])

        /* Plot SNR and RSSI */
        Canvas(modifier = modifier) {

            val height = size.height
            val width = size.width - 28.dp.toPx()
            val spacing = LEFT_LABEL_SPACING.dp.toPx()
            val spacePerEntry = (width - spacing) / meshPackets.size

            /* Plot */
            val dataPointRadius = 2.dp.toPx()
            for ((i, packet) in meshPackets.withIndex()) {

                val x = spacing + i * spacePerEntry

                /* SNR */
                val snrRatio = (packet.rxSnr - Metric.SNR.min) / snrDiff
                val ySNR = height - (snrRatio * height)
                drawCircle(
                    color = METRICS_COLORS[Metric.SNR.ordinal],
                    radius = dataPointRadius,
                    center = Offset(x, ySNR)
                )

                /* RSSI */
                val rssiRatio = (packet.rxRssi - Metric.RSSI.min) / rssiDiff
                val yRssi = height - (rssiRatio * height)
                drawCircle(
                    color = METRICS_COLORS[Metric.RSSI.ordinal],
                    radius = dataPointRadius,
                    center = Offset(x, yRssi)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Legend(legendData = LEGEND_DATA, promptInfoDialog)

    Spacer(modifier = Modifier.height(16.dp))
}

/**
 * Draws a set of Y labels on the left side of the graph.
 * Currently only used for the RSSI labels.
 */
@Composable
private fun LeftYLabels(
    modifier: Modifier,
    labelColor: Color,
) {
    val range = Metric.RSSI.difference()
    val verticalSpacing = range / LINE_LIMIT
    val density = LocalDensity.current
    Canvas(modifier = modifier) {

        val height = size.height

        /* Y Labels */

        val textPaint = Paint().apply {
            color = labelColor.toArgb()
            textAlign = Paint.Align.LEFT
            textSize = density.run { 12.dp.toPx() }
            typeface = setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
            alpha = TEXT_PAINT_ALPHA
        }
        drawContext.canvas.nativeCanvas.apply {
            var label = Metric.RSSI.min
            for (i in 0..LINE_LIMIT) {
                val ratio = (label - Metric.RSSI.min) / range
                val y = height - (ratio * height)
                drawText(
                    "${label.toInt()}",
                    4.dp.toPx(),
                    y + 4.dp.toPx(),
                    textPaint
                )
                label += verticalSpacing
            }
        }
    }
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
