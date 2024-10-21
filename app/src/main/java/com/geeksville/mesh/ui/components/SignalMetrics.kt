package com.geeksville.mesh.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.ui.components.CommonCharts.MS_PER_SEC
import com.geeksville.mesh.ui.components.CommonCharts.LINE_LIMIT
import com.geeksville.mesh.ui.components.CommonCharts.TEXT_PAINT_ALPHA
import com.geeksville.mesh.ui.components.CommonCharts.LEFT_LABEL_SPACING


private val METRICS_COLORS = listOf(Color.Green, Color.Blue)
private enum class Metric(val min: Float, val max: Float) {
    SNR(-20f, 10f),
    RSSI(-140f, -20f);
    /**
     * Difference between the metrics `max` and `min` values.
     */
    fun difference() = max - min
}

@Composable
fun SignalMetricsScreen(meshPackets: List<MeshPacket>) {
    Column {
        SignalMetricsChart(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.33f),
            meshPackets = meshPackets
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(meshPackets.reversed()) { meshPacket -> SignalMetricsCard(meshPacket)}
        }
    }
}

@Composable
private fun SignalMetricsChart(modifier: Modifier = Modifier, meshPackets: List<MeshPacket>) {

    ChartHeader(amount = meshPackets.size)
    if (meshPackets.isEmpty())
        return

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
                val ySNR = height - spacing - (snrRatio * height)
                drawCircle(
                    color = METRICS_COLORS[Metric.SNR.ordinal],
                    radius = dataPointRadius,
                    center = Offset(x, ySNR)
                )

                /* RSSI */
                val rssiRatio = (packet.rxRssi - Metric.RSSI.min) / rssiDiff
                val yRssi= height - spacing - (rssiRatio * height)
                drawCircle(
                    color = METRICS_COLORS[Metric.RSSI.ordinal],
                    radius = dataPointRadius,
                    center = Offset(x, yRssi)
                )
            }
        }
        TimeLabels(
            modifier = modifier,
            graphColor = graphColor,
            oldest = meshPackets.last().rxTime * MS_PER_SEC,
            newest = meshPackets.first().rxTime * MS_PER_SEC
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // TODO legend

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
private fun SignalMetricsCard(meshPacket: MeshPacket) {}
