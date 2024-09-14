package com.geeksville.mesh.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.ui.BatteryInfo
import com.geeksville.mesh.ui.components.CommonCharts.DEVICE_METRICS_COLORS
import com.geeksville.mesh.ui.components.CommonCharts.LEFT_CHART_SPACING
import com.geeksville.mesh.ui.components.CommonCharts.MAX_PERCENT_VALUE
import com.geeksville.mesh.ui.components.CommonCharts.MS_PER_SEC
import com.geeksville.mesh.ui.components.CommonCharts.TIME_FORMAT


@Composable
fun DeviceMetricsScreen(telemetries: List<Telemetry>) {
    Column {
        DeviceMetricsChart(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.33f),
            telemetries.reversed()
        )
        /* Device Metric Cards */
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(telemetries) { telemetry -> DeviceMetricsCard(telemetry) }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun DeviceMetricsChart(modifier: Modifier = Modifier, telemetries: List<Telemetry>) {

    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty())
        return

    Spacer(modifier = Modifier.height(16.dp))

    val graphColor = MaterialTheme.colors.onSurface
    val spacing = LEFT_CHART_SPACING

    Box(contentAlignment = Alignment.TopStart) {

        ChartOverlay(modifier, graphColor, minValue = 0f, maxValue = 100f)

        /* Plot Battery Line, ChUtil, and AirUtilTx */
        Canvas(modifier = modifier) {

            val height = size.height
            val width = size.width - 28.dp.toPx()
            val spacePerEntry = (width - spacing) / telemetries.size
            val dataPointRadius = 2.dp.toPx()
            var lastX: Float
            val strokePath = Path().apply {
                for (i in telemetries.indices) {
                    val telemetry = telemetries[i]
                    val nextTelemetry = telemetries.getOrNull(i + 1) ?: telemetries.last()
                    val leftRatio = telemetry.deviceMetrics.batteryLevel / MAX_PERCENT_VALUE
                    val rightRatio = nextTelemetry.deviceMetrics.batteryLevel / MAX_PERCENT_VALUE

                    val x1 = spacing + i * spacePerEntry
                    val y1 = height - spacing - (leftRatio * height)

                    /* Channel Utilization */
                    val chUtilRatio = telemetry.deviceMetrics.channelUtilization / MAX_PERCENT_VALUE
                    val yChUtil = height - spacing - (chUtilRatio * height)
                    drawCircle(
                        color = DEVICE_METRICS_COLORS[1],
                        radius = dataPointRadius,
                        center = Offset(x1, yChUtil)
                    )

                    /* Air Utilization Transmit  */
                    val airUtilRatio = telemetry.deviceMetrics.airUtilTx / MAX_PERCENT_VALUE
                    val yAirUtil = height - spacing - (airUtilRatio * height)
                    drawCircle(
                        color = DEVICE_METRICS_COLORS[2],
                        radius = dataPointRadius,
                        center = Offset(x1, yAirUtil)
                    )

                    val x2 = spacing + (i + 1) * spacePerEntry
                    val y2 = height - spacing - (rightRatio * height)
                    if (i == 0)
                        moveTo(x1, y1)

                    lastX = (x1 + x2) / 2f

                    quadraticBezierTo(x1, y1, lastX, (y1 + y2) / 2f)
                }
            }

            /* Battery Line */
            drawPath(
                path = strokePath,
                color = DEVICE_METRICS_COLORS[0],
                style = Stroke(
                    width = dataPointRadius,
                    cap = StrokeCap.Round
                )
            )
        }

        TimeLabels(
            modifier = modifier,
            graphColor = graphColor,
            oldest = telemetries.first().time * MS_PER_SEC,
            newest = telemetries.last().time * MS_PER_SEC
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    DeviceLegend()

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun DeviceMetricsCard(telemetry: Telemetry) {
    val deviceMetrics = telemetry.deviceMetrics
    val time = telemetry.time * MS_PER_SEC
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Surface {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    /* Time, Battery, and Voltage */
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = TIME_FORMAT.format(time),
                            style = TextStyle(fontWeight = FontWeight.Bold),
                            fontSize = MaterialTheme.typography.button.fontSize
                        )

                        BatteryInfo(
                            batteryLevel = deviceMetrics.batteryLevel,
                            voltage = deviceMetrics.voltage
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    /* Channel Utilization and Air Utilization Tx */
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val text = stringResource(R.string.channel_air_util).format(
                            deviceMetrics.channelUtilization,
                            deviceMetrics.airUtilTx
                        )
                        Text(
                            text = text,
                            color = MaterialTheme.colors.onSurface,
                            fontSize = MaterialTheme.typography.button.fontSize
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        LegendLabel(text = stringResource(R.string.battery), color = DEVICE_METRICS_COLORS[0], isLine = true)

        Spacer(modifier = Modifier.width(4.dp))

        LegendLabel(text = stringResource(R.string.channel_utilization), color = DEVICE_METRICS_COLORS[1])

        Spacer(modifier = Modifier.width(4.dp))

        LegendLabel(text = stringResource(R.string.air_utilization), color = DEVICE_METRICS_COLORS[2])

        Spacer(modifier = Modifier.weight(1f))
    }
}
