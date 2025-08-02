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

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.EnvironmentMetrics
import com.geeksville.mesh.R
import com.geeksville.mesh.TelemetryProtos
import com.geeksville.mesh.TelemetryProtos.Telemetry
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.TimeFrame
import com.geeksville.mesh.ui.common.components.IaqDisplayMode
import com.geeksville.mesh.ui.common.components.IndoorAirQuality
import com.geeksville.mesh.ui.common.components.OptionLabel
import com.geeksville.mesh.ui.common.components.SlidingSelector
import com.geeksville.mesh.ui.metrics.CommonCharts.DATE_TIME_FORMAT
import com.geeksville.mesh.ui.metrics.CommonCharts.MS_PER_SEC
import com.geeksville.mesh.util.UnitConversions.celsiusToFahrenheit

@Composable
fun EnvironmentMetricsScreen(viewModel: MetricsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val environmentState by viewModel.environmentState.collectAsStateWithLifecycle()
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    val graphData = environmentState.environmentMetricsFiltered(selectedTimeFrame, state.isFahrenheit)
    val data = graphData.metrics

    val processedTelemetries: List<Telemetry> =
        if (state.isFahrenheit) {
            data.map { telemetry ->
                val temperatureFahrenheit = celsiusToFahrenheit(telemetry.environmentMetrics.temperature)
                val soilTemperatureFahrenheit = celsiusToFahrenheit(telemetry.environmentMetrics.soilTemperature)
                telemetry.copy {
                    environmentMetrics =
                        telemetry.environmentMetrics.copy {
                            temperature = temperatureFahrenheit
                            soilTemperature = soilTemperatureFahrenheit
                        }
                }
            }
        } else {
            data
        }

    var displayInfoDialog by remember { mutableStateOf(false) }
    Column {
        if (displayInfoDialog) {
            LegendInfoDialog(
                pairedRes = listOf(Pair(R.string.iaq, R.string.iaq_definition)),
                onDismiss = { displayInfoDialog = false },
            )
        }

        EnvironmentMetricsChart(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(fraction = 0.33f),
            telemetries = processedTelemetries.reversed(),
            graphData = graphData,
            selectedTime = selectedTimeFrame,
            promptInfoDialog = { displayInfoDialog = true },
        )

        SlidingSelector(
            TimeFrame.entries.toList(),
            selectedTimeFrame,
            onOptionSelected = { viewModel.setTimeFrame(it) },
        ) {
            OptionLabel(stringResource(it.strRes))
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(processedTelemetries) { telemetry -> EnvironmentMetricsCard(telemetry, state.isFahrenheit) }
        }
    }
}

@Composable
private fun TemperatureDisplay(temperature: Float, environmentDisplayFahrenheit: Boolean) {
    if (!temperature.isNaN()) {
        val textFormat = if (environmentDisplayFahrenheit) "%s %.1f°F" else "%s %.1f°C"
        Text(
            text = textFormat.format(stringResource(id = R.string.temperature), temperature),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
        )
    }
}

@Composable
private fun HumidityAndBarometricPressureDisplay(envMetrics: TelemetryProtos.EnvironmentMetrics) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        envMetrics.relativeHumidity?.let { humidity ->
            if (!humidity.isNaN()) {
                Text(
                    text = "%s %.2f%%".format(stringResource(id = R.string.humidity), humidity),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
        envMetrics.barometricPressure?.let { pressure ->
            if (!pressure.isNaN() && pressure > 0) { // Keep pressure > 0 check
                Text(
                    text = "%.2f hPa".format(pressure),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@Composable
private fun SoilMetricsDisplay(envMetrics: TelemetryProtos.EnvironmentMetrics, environmentDisplayFahrenheit: Boolean) {
    if (
        envMetrics.soilTemperature != null ||
        (envMetrics.soilMoisture != null && envMetrics.soilMoisture != Int.MIN_VALUE)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val soilTemperatureTextFormat = if (environmentDisplayFahrenheit) "%s %.1f°F" else "%s %.1f°C"
            val soilMoistureTextFormat = "%s %d%%"
            envMetrics.soilMoisture?.let { soilMoistureValue ->
                if (soilMoistureValue != Int.MIN_VALUE) {
                    Text(
                        text = soilMoistureTextFormat.format(stringResource(R.string.soil_moisture), soilMoistureValue),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                }
            }
            envMetrics.soilTemperature?.let { soilTemperature ->
                if (!soilTemperature.isNaN()) {
                    Text(
                        text =
                        soilTemperatureTextFormat.format(
                            stringResource(R.string.soil_temperature),
                            soilTemperature,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun IaqDisplay(iaqValue: Int) {
    if (iaqValue != Int.MIN_VALUE) {
        Spacer(modifier = Modifier.height(4.dp))
        /* Air Quality */
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.iaq),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
            Spacer(modifier = Modifier.width(4.dp))
            IndoorAirQuality(iaq = iaqValue, displayMode = IaqDisplayMode.Dot)
        }
    }
}

@Composable
private fun LuxUVLuxDisplay(envMetrics: TelemetryProtos.EnvironmentMetrics) {
    envMetrics.lux?.let { luxValue ->
        if (!luxValue.isNaN()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "%s %.0f lx".format(stringResource(R.string.lux), luxValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
    envMetrics.uvLux?.let { uvLuxValue ->
        if (!uvLuxValue.isNaN()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "%s %.0f UVlx".format(stringResource(R.string.uv_lux), uvLuxValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@Composable
private fun VoltageCurrentDisplay(envMetrics: TelemetryProtos.EnvironmentMetrics) {
    envMetrics.voltage?.let { voltage ->
        if (!voltage.isNaN()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "%s %.2f V".format(stringResource(R.string.voltage), voltage),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }

    envMetrics.current?.let { current ->
        if (!current.isNaN()) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "%s %.2f A".format(stringResource(R.string.current), current),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@Composable
private fun GasResistanceDisplay(gasResistance: Float) {
    if (!gasResistance.isNaN()) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "%s %.2f Ohm".format(stringResource(R.string.gas_resistance), gasResistance),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
        }
    }
}

@Composable
private fun EnvironmentMetricsCard(telemetry: Telemetry, environmentDisplayFahrenheit: Boolean) {
    val envMetrics = telemetry.environmentMetrics
    val time = telemetry.time * MS_PER_SEC
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Surface { SelectionContainer { EnvironmentMetricsContent(telemetry, environmentDisplayFahrenheit) } }
    }
}

@Composable
private fun EnvironmentMetricsContent(telemetry: Telemetry, environmentDisplayFahrenheit: Boolean) {
    val envMetrics = telemetry.environmentMetrics
    val time = telemetry.time * MS_PER_SEC
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        /* Time and Temperature */
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = DATE_TIME_FORMAT.format(time),
                style = TextStyle(fontWeight = FontWeight.Bold),
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
            envMetrics.temperature?.let { temperature -> TemperatureDisplay(temperature, environmentDisplayFahrenheit) }
        }

        Spacer(modifier = Modifier.height(4.dp))

        /* Humidity and Barometric Pressure */
        HumidityAndBarometricPressureDisplay(envMetrics)

        /* Soil Moisture and Soil Temperature */
        SoilMetricsDisplay(envMetrics, environmentDisplayFahrenheit)

        envMetrics.iaq?.let { iaqValue -> IaqDisplay(iaqValue) }

        LuxUVLuxDisplay(envMetrics)

        VoltageCurrentDisplay(envMetrics)

        envMetrics.gasResistance?.let { gasResistance -> GasResistanceDisplay(gasResistance) }
    }
}
