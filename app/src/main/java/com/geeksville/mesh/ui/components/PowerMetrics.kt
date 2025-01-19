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
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.TimeFrame
import com.geeksville.mesh.ui.components.CommonCharts.MS_PER_SEC
import com.geeksville.mesh.ui.components.CommonCharts.DATE_TIME_FORMAT

private val METRICS_COLORS = listOf(Color.Green, Color.Cyan, Color.Magenta)

@Suppress("MagicNumber")
private enum class Power(val min: Float, val max: Float) {
    CURRENT(-500f, 500f),
    VOLTAGE(0f, 20f);
    /**
     * Difference between the metrics `max` and `min` values.
     */
    fun difference() = max - min
}
// TODO maybe channel enum
private val LEGEND_DATA = listOf(
    // TODO resources, magic numbers
    LegendData(nameRes = R.string.channel_1, color = METRICS_COLORS[0], isLine = true),
    LegendData(nameRes = R.string.channel_2, color = METRICS_COLORS[1], isLine = true),
    LegendData(nameRes = R.string.channel_3, color = METRICS_COLORS[2], isLine = true)
)

@Composable
fun PowerMetricsScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var displayInfoDialog by remember { mutableStateOf(false) }
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    val data = state.powerMetricsFiltered(selectedTimeFrame)

    Column {

        PowerMetricsChart(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.33f),
            telemetries = data.reversed(),
            selectedTimeFrame,
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
            items(data) { telemetry -> PowerMetricsCard(telemetry) }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun PowerMetricsChart(
    modifier: Modifier = Modifier,
    telemetries: List<Telemetry>,
    selectedTime: TimeFrame,
    promptInfoDialog: () -> Unit
) {
    ChartHeader(amount = telemetries.size)
    if (telemetries.isEmpty()) {
        return
    }

    val (oldest, newest) = remember(key1 = telemetries) {
        Pair(
            telemetries.minBy { it.time },
            telemetries.maxBy { it.time }
        )
    }
    val timeDiff = newest.time - oldest.time

    TimeLabels(
        oldest = oldest.time,
        newest = newest.time
    )

    Spacer(modifier = Modifier.height(16.dp))

    val graphColor = MaterialTheme.colors.onSurface
    val currentDiff = Power.CURRENT.difference()
    val voltageDiff = Power.VOLTAGE.difference()

    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val dp by remember(key1 = selectedTime) {
        mutableStateOf(selectedTime.dp(screenWidth, time = (newest.time - oldest.time).toLong()))
    }

    Row {
        YAxisLabels(
            modifier = modifier.weight(weight = .1f),
            graphColor,
            minValue = Power.CURRENT.min,
            maxValue = Power.CURRENT.max,
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
                oldest = oldest.time,
                newest = newest.time,
                selectedTime.lineInterval()
            )

            /* Plot */
            Canvas(modifier = modifier.width(dp)) {
                val width = size.width
            }
        }
        YAxisLabels(
            modifier = modifier.weight(weight = .1f),
            graphColor,
            minValue = Power.VOLTAGE.min,
            maxValue = Power.VOLTAGE.max,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Legend(legendData = LEGEND_DATA, promptInfoDialog)

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun PowerMetricsCard(telemetry: Telemetry) {
    val time = telemetry.time * MS_PER_SEC
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
                        }
                    }
                }
            }
        }
    }
}
