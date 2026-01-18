/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.util.UnitConversions.celsiusToFahrenheit
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.current
import org.meshtastic.core.strings.gas_resistance
import org.meshtastic.core.strings.humidity
import org.meshtastic.core.strings.iaq
import org.meshtastic.core.strings.iaq_definition
import org.meshtastic.core.strings.lux
import org.meshtastic.core.strings.radiation
import org.meshtastic.core.strings.soil_moisture
import org.meshtastic.core.strings.soil_temperature
import org.meshtastic.core.strings.temperature
import org.meshtastic.core.strings.uv_lux
import org.meshtastic.core.strings.voltage
import org.meshtastic.core.ui.component.IaqDisplayMode
import org.meshtastic.core.ui.component.IndoorAirQuality
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.OptionLabel
import org.meshtastic.core.ui.component.SlidingSelector
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.feature.node.model.TimeFrame
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.Telemetry

@Composable
fun EnvironmentMetricsScreen(viewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val environmentState by viewModel.environmentState.collectAsStateWithLifecycle()
    val selectedTimeFrame by viewModel.timeFrame.collectAsState()
    val graphData = environmentState.environmentMetricsFiltered(selectedTimeFrame, state.isFahrenheit)
    val data = graphData.metrics

    val processedTelemetries: List<Telemetry> =
        if (state.isFahrenheit) {
            data.map { telemetry ->
                val env = telemetry.environment_metrics ?: EnvironmentMetrics()
                val temperatureFahrenheit = celsiusToFahrenheit(env.temperature ?: 0f)
                val soilTemperatureFahrenheit = celsiusToFahrenheit(env.soil_temperature ?: 0f)
                telemetry.copy(
                    environment_metrics =
                    env.copy(temperature = temperatureFahrenheit, soil_temperature = soilTemperatureFahrenheit),
                )
            }
        } else {
            data
        }

    var displayInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MainAppBar(
                title = state.node?.user?.long_name ?: "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (displayInfoDialog) {
                LegendInfoDialog(
                    pairedRes = listOf(Pair(Res.string.iaq, Res.string.iaq_definition)),
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
}

@Composable
private fun TemperatureDisplay(envMetrics: EnvironmentMetrics, environmentDisplayFahrenheit: Boolean) {
    val temperature = envMetrics.temperature
    if (temperature != null && !temperature.isNaN()) {
        val textFormat = if (environmentDisplayFahrenheit) "%s %.1f°F" else "%s %.1f°C"
        Text(
            text = textFormat.format(stringResource(Res.string.temperature), temperature),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
        )
    }
}

@Composable
private fun HumidityAndBarometricPressureDisplay(envMetrics: EnvironmentMetrics) {
    val humidityValue = envMetrics.relative_humidity ?: Float.NaN
    val pressureValue = envMetrics.barometric_pressure ?: Float.NaN
    val hasHumidity = !humidityValue.isNaN()
    val hasPressure = !pressureValue.isNaN() && pressureValue > 0

    if (hasHumidity || hasPressure) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (hasHumidity) {
                Text(
                    text = "%s %.2f%%".format(stringResource(Res.string.humidity), humidityValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    modifier = Modifier.padding(vertical = 0.dp),
                )
            }
            if (hasPressure) {
                Text(
                    text = "%.2f hPa".format(pressureValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    modifier = Modifier.padding(vertical = 0.dp),
                )
            }
        }
    }
}

@Composable
private fun SoilMetricsDisplay(envMetrics: EnvironmentMetrics, environmentDisplayFahrenheit: Boolean) {
    val soilTemperature = envMetrics.soil_temperature ?: Float.NaN
    val soilMoistureValue = envMetrics.soil_moisture ?: Int.MIN_VALUE
    if (!soilTemperature.isNaN() || (soilMoistureValue != Int.MIN_VALUE)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val soilTemperatureTextFormat = if (environmentDisplayFahrenheit) "%s %.1f°F" else "%s %.1f°C"
            val soilMoistureTextFormat = "%s %d%%"
            if (soilMoistureValue != Int.MIN_VALUE) {
                Text(
                    text = soilMoistureTextFormat.format(stringResource(Res.string.soil_moisture), soilMoistureValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
            if (!soilTemperature.isNaN()) {
                Text(
                    text =
                    soilTemperatureTextFormat.format(stringResource(Res.string.soil_temperature), soilTemperature),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@Composable
private fun LuxUVLuxDisplay(envMetrics: EnvironmentMetrics) {
    val luxValue = envMetrics.lux ?: Float.NaN
    val uvLuxValue = envMetrics.uv_lux ?: Float.NaN
    val hasLux = !luxValue.isNaN()
    val hasUvLux = !uvLuxValue.isNaN()

    if (hasLux || hasUvLux) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (hasLux) {
                Text(
                    text = "%s %.0f lx".format(stringResource(Res.string.lux), luxValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
            if (hasUvLux) {
                Text(
                    text = "%s %.0f UVlx".format(stringResource(Res.string.uv_lux), uvLuxValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@Composable
private fun VoltageCurrentDisplay(envMetrics: EnvironmentMetrics) {
    val voltageValue = envMetrics.voltage ?: Float.NaN
    val currentValue = envMetrics.current ?: Float.NaN
    val hasVoltage = !voltageValue.isNaN()
    val hasCurrent = !currentValue.isNaN()

    if (hasVoltage || hasCurrent) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (hasVoltage) {
                Text(
                    text = "%s %.2f V".format(stringResource(Res.string.voltage), voltageValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
            if (hasCurrent) {
                Text(
                    text = "%s %.2f mA".format(stringResource(Res.string.current), currentValue),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@Composable
private fun GasCompositionDisplay(envMetrics: EnvironmentMetrics) {
    val iaqValue = envMetrics.iaq ?: Int.MIN_VALUE
    val gasResistance = envMetrics.gas_resistance ?: Float.NaN

    if ((iaqValue != Int.MIN_VALUE) || (gasResistance.isFinite())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (iaqValue != Int.MIN_VALUE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.iaq),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IndoorAirQuality(iaq = iaqValue, displayMode = IaqDisplayMode.Dot)
                }
            }
            if (!gasResistance.isNaN()) {
                Text(
                    text = "%s %.2f Ohm".format(stringResource(Res.string.gas_resistance), gasResistance),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@Composable
private fun RadiationDisplay(envMetrics: EnvironmentMetrics) {
    val radiation = envMetrics.radiation ?: Float.NaN
    if (!radiation.isNaN() && radiation > 0f) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "%s %.2f µR/h".format(stringResource(Res.string.radiation), radiation),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
        }
    }
}

@Composable
private fun EnvironmentMetricsCard(telemetry: Telemetry, environmentDisplayFahrenheit: Boolean) {
    val envMetrics = telemetry.environment_metrics ?: EnvironmentMetrics()
    val time = telemetry.time * MS_PER_SEC
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Surface { SelectionContainer { EnvironmentMetricsContent(telemetry, environmentDisplayFahrenheit) } }
    }
}

@Composable
private fun EnvironmentMetricsContent(telemetry: Telemetry, environmentDisplayFahrenheit: Boolean) {
    val envMetrics = telemetry.environment_metrics ?: EnvironmentMetrics()
    val time = telemetry.time * MS_PER_SEC
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp)) {
        /* Time and Temperature */
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = DATE_TIME_FORMAT.format(time),
                style = TextStyle(fontWeight = FontWeight.Bold),
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
            TemperatureDisplay(envMetrics, environmentDisplayFahrenheit)
        }

        HumidityAndBarometricPressureDisplay(envMetrics)

        SoilMetricsDisplay(envMetrics, environmentDisplayFahrenheit)

        GasCompositionDisplay(envMetrics)

        LuxUVLuxDisplay(envMetrics)

        VoltageCurrentDisplay(envMetrics)
        RadiationDisplay(envMetrics)
    }
}

@Suppress("MagicNumber") // preview data
@Preview(showBackground = true)
@Composable
private fun PreviewEnvironmentMetricsContent() {
    // Build a fake EnvironmentMetrics using Wire
    val fakeEnvMetrics =
        EnvironmentMetrics(
            temperature = 22.5f,
            relative_humidity = 55.0f,
            barometric_pressure = 1013.25f,
            soil_moisture = 33,
            soil_temperature = 18.0f,
            lux = 100.0f,
            uv_lux = 100.0f,
            voltage = 3.7f,
            current = 0.12f,
            iaq = 100,
            radiation = 0.15f,
            gas_resistance = 1200.0f,
        )
    val fakeTelemetry =
        Telemetry(time = (System.currentTimeMillis() / 1000).toInt(), environment_metrics = fakeEnvMetrics)
    MaterialTheme {
        Surface { EnvironmentMetricsContent(telemetry = fakeTelemetry, environmentDisplayFahrenheit = false) }
    }
}
