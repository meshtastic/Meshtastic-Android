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
@file:Suppress("TooManyFunctions")

package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.current
import org.meshtastic.core.resources.env_metrics_log
import org.meshtastic.core.resources.gas_resistance
import org.meshtastic.core.resources.humidity
import org.meshtastic.core.resources.iaq
import org.meshtastic.core.resources.iaq_definition
import org.meshtastic.core.resources.lux
import org.meshtastic.core.resources.one_wire_temperature
import org.meshtastic.core.resources.radiation
import org.meshtastic.core.resources.rainfall_1h
import org.meshtastic.core.resources.rainfall_24h
import org.meshtastic.core.resources.soil_moisture
import org.meshtastic.core.resources.soil_temperature
import org.meshtastic.core.resources.temperature
import org.meshtastic.core.resources.uv_lux
import org.meshtastic.core.resources.voltage
import org.meshtastic.core.resources.wind_direction
import org.meshtastic.core.resources.wind_gust
import org.meshtastic.core.resources.wind_lull
import org.meshtastic.core.resources.wind_speed
import org.meshtastic.core.ui.component.IaqDisplayMode
import org.meshtastic.core.ui.component.IndoorAirQuality
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.rememberSaveFileLauncher
import org.meshtastic.proto.Telemetry

@Composable
fun EnvironmentMetricsScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val graphData by viewModel.environmentGraphingData.collectAsStateWithLifecycle()
    val filteredTelemetries by viewModel.filteredEnvironmentMetrics.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by viewModel.availableTimeFrames.collectAsStateWithLifecycle()

    val exportLauncher = rememberSaveFileLauncher { uri ->
        viewModel.saveEnvironmentMetricsCSV(uri, filteredTelemetries)
    }

    BaseMetricScreen(
        onNavigateUp = onNavigateUp,
        telemetryType = TelemetryType.ENVIRONMENT,
        titleRes = Res.string.env_metrics_log,
        nodeName = state.node?.user?.long_name ?: "",
        data = filteredTelemetries,
        timeProvider = { it.time.toDouble() },
        infoData = listOf(InfoDialogData(Res.string.iaq, Res.string.iaq_definition, Environment.IAQ.color)),
        onRequestTelemetry = { viewModel.requestTelemetry(TelemetryType.ENVIRONMENT) },
        onExportCsv = { exportLauncher("environment_metrics.csv", "text/csv") },
        controlPart = {
            TimeFrameSelector(
                selectedTimeFrame = timeFrame,
                availableTimeFrames = availableTimeFrames,
                onTimeFrameSelected = viewModel::setTimeFrame,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
        chartPart = { modifier, selectedX, vicoScrollState, onPointSelected ->
            EnvironmentMetricsChart(
                modifier = modifier,
                telemetries = filteredTelemetries.reversed(),
                graphData = graphData,
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
            )
        },
        listPart = { modifier, selectedX, lazyListState, onCardClick ->
            LazyColumn(modifier = modifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(filteredTelemetries) { _, telemetry ->
                    EnvironmentMetricsCard(
                        telemetry = telemetry,
                        environmentDisplayFahrenheit = state.isFahrenheit,
                        isSelected = telemetry.time.toDouble() == selectedX,
                        onClick = { onCardClick(telemetry.time.toDouble()) },
                    )
                }
            }
        },
    )
}

@Composable
private fun TemperatureDisplay(
    envMetrics: org.meshtastic.proto.EnvironmentMetrics,
    environmentDisplayFahrenheit: Boolean,
) {
    envMetrics.temperature?.let { temperature ->
        if (!temperature.isNaN()) {
            val textFormat = if (environmentDisplayFahrenheit) "%s %.1f°F" else "%s %.1f°C"
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricIndicator(Environment.TEMPERATURE.color)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = formatString(textFormat, stringResource(Res.string.temperature), temperature),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@Composable
private fun HumidityAndBarometricPressureDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    val hasHumidity = envMetrics.relative_humidity?.let { !it.isNaN() } == true
    val hasPressure = envMetrics.barometric_pressure?.let { !it.isNaN() && it > 0 } == true

    if (hasHumidity || hasPressure) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (hasHumidity) {
                val humidity = envMetrics.relative_humidity!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.HUMIDITY.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text =
                        "${stringResource(
                            Res.string.humidity,
                        )} ${MetricFormatter.percent(humidity, decimalPlaces = 2)}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        modifier = Modifier.padding(vertical = 0.dp),
                    )
                }
            }
            if (hasPressure) {
                val pressure = envMetrics.barometric_pressure!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.BAROMETRIC_PRESSURE.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = MetricFormatter.pressure(pressure, decimalPlaces = 2),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        modifier = Modifier.padding(vertical = 0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SoilMetricsDisplay(
    envMetrics: org.meshtastic.proto.EnvironmentMetrics,
    environmentDisplayFahrenheit: Boolean,
) {
    if (
        envMetrics.soil_temperature != null ||
        (envMetrics.soil_moisture != null && envMetrics.soil_moisture != Int.MIN_VALUE)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val soilTemperatureTextFormat = if (environmentDisplayFahrenheit) "%s %.1f°F" else "%s %.1f°C"
            val soilMoistureTextFormat = "%s %d%%"
            envMetrics.soil_moisture?.let { soilMoistureValue ->
                if (soilMoistureValue != Int.MIN_VALUE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetricIndicator(Environment.SOIL_MOISTURE.color)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text =
                            formatString(
                                soilMoistureTextFormat,
                                stringResource(Res.string.soil_moisture),
                                soilMoistureValue,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        )
                    }
                }
            }
            envMetrics.soil_temperature?.let { soilTemperature ->
                if (!soilTemperature.isNaN()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetricIndicator(Environment.SOIL_TEMPERATURE.color)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text =
                            formatString(
                                soilTemperatureTextFormat,
                                stringResource(Res.string.soil_temperature),
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
}

@Composable
private fun LuxUVLuxDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    val hasLux = envMetrics.lux != null && !envMetrics.lux!!.isNaN()
    val hasUvLux = envMetrics.uv_lux != null && !envMetrics.uv_lux!!.isNaN()

    if (hasLux || hasUvLux) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (hasLux) {
                val luxValue = envMetrics.lux!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.LUX.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatString("%s %.0f lx", stringResource(Res.string.lux), luxValue),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                }
            }
            if (hasUvLux) {
                val uvLuxValue = envMetrics.uv_lux!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.UV_LUX.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatString("%s %.0f UVlx", stringResource(Res.string.uv_lux), uvLuxValue),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoltageCurrentDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    val hasVoltage = envMetrics.voltage != null && !envMetrics.voltage!!.isNaN()
    val hasCurrent = envMetrics.current != null && !envMetrics.current!!.isNaN()

    if (hasVoltage || hasCurrent) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (hasVoltage) {
                val voltage = envMetrics.voltage!!
                Text(
                    text = "${stringResource(Res.string.voltage)} ${MetricFormatter.voltage(voltage)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
            if (hasCurrent) {
                val currentValue = envMetrics.current!!
                Text(
                    text =
                    "${stringResource(
                        Res.string.current,
                    )} ${MetricFormatter.current(currentValue, decimalPlaces = 2)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@Composable
private fun GasCompositionDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    val iaqValue = envMetrics.iaq
    val gasResistance = envMetrics.gas_resistance

    if ((iaqValue != null && iaqValue != Int.MIN_VALUE) || (gasResistance?.isFinite() == true)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (iaqValue != null && iaqValue != Int.MIN_VALUE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.IAQ.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.iaq),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                    Spacer(Modifier.width(4.dp))
                    IndoorAirQuality(iaq = iaqValue, displayMode = IaqDisplayMode.Dot)
                }
            }
            if (gasResistance != null && !gasResistance.isNaN()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.GAS_RESISTANCE.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatString("%s %.2f Ohm", stringResource(Res.string.gas_resistance), gasResistance),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadiationDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    envMetrics.radiation?.let { radiation ->
        if (!radiation.isNaN() && radiation > 0f) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.RADIATION.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatString("%s %.2f µR/h", stringResource(Res.string.radiation), radiation),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun WindDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    val hasSpeed = envMetrics.wind_speed != null && !envMetrics.wind_speed!!.isNaN()
    val hasGust = envMetrics.wind_gust != null && !envMetrics.wind_gust!!.isNaN()
    val hasLull = envMetrics.wind_lull != null && !envMetrics.wind_lull!!.isNaN()

    if (hasSpeed || hasGust || hasLull) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (hasSpeed) WindSpeedRow(envMetrics)
            if (hasGust || hasLull) WindGustLullRow(envMetrics, hasGust, hasLull)
        }
    }
}

@Composable
private fun WindSpeedRow(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricIndicator(Environment.WIND_SPEED.color)
            Spacer(Modifier.width(4.dp))
            val dirText =
                if (envMetrics.wind_direction != null) {
                    formatString(
                        "%s %.1f m/s (%s %d°)",
                        stringResource(Res.string.wind_speed),
                        envMetrics.wind_speed!!,
                        stringResource(Res.string.wind_direction),
                        envMetrics.wind_direction!!,
                    )
                } else {
                    formatString(
                        "%s %s",
                        stringResource(Res.string.wind_speed),
                        MetricFormatter.windSpeed(envMetrics.wind_speed!!),
                    )
                }
            Text(
                text = dirText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
        }
    }
}

@Composable
private fun WindGustLullRow(envMetrics: org.meshtastic.proto.EnvironmentMetrics, hasGust: Boolean, hasLull: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (hasGust) {
            Text(
                text = "${stringResource(Res.string.wind_gust)} ${MetricFormatter.windSpeed(envMetrics.wind_gust!!)}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
        }
        if (hasLull) {
            Text(
                text = "${stringResource(Res.string.wind_lull)} ${MetricFormatter.windSpeed(envMetrics.wind_lull!!)}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
        }
    }
}

@Composable
private fun RainfallDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    val has1h = envMetrics.rainfall_1h != null && !envMetrics.rainfall_1h!!.isNaN()
    val has24h = envMetrics.rainfall_24h != null && !envMetrics.rainfall_24h!!.isNaN()

    if (has1h || has24h) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (has1h) {
                Text(
                    text =
                    "${stringResource(
                        Res.string.rainfall_1h,
                    )} ${MetricFormatter.rainfall(envMetrics.rainfall_1h!!)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
            if (has24h) {
                Text(
                    text =
                    "${stringResource(
                        Res.string.rainfall_24h,
                    )} ${MetricFormatter.rainfall(envMetrics.rainfall_24h!!)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@Composable
private fun OneWireTemperatureDisplay(
    envMetrics: org.meshtastic.proto.EnvironmentMetrics,
    environmentDisplayFahrenheit: Boolean,
) {
    val sensors = envMetrics.one_wire_temperature.filterNot { it.isNaN() }
    if (sensors.isEmpty()) return
    val oneWireEntries =
        listOf(
            Environment.ONE_WIRE_TEMP_1,
            Environment.ONE_WIRE_TEMP_2,
            Environment.ONE_WIRE_TEMP_3,
            Environment.ONE_WIRE_TEMP_4,
            Environment.ONE_WIRE_TEMP_5,
            Environment.ONE_WIRE_TEMP_6,
            Environment.ONE_WIRE_TEMP_7,
            Environment.ONE_WIRE_TEMP_8,
        )
    val textFormat = if (environmentDisplayFahrenheit) "%s %d: %.1f°F" else "%s %d: %.1f°C"
    sensors.forEachIndexed { idx, temp ->
        val color = oneWireEntries.getOrNull(idx)?.color ?: Environment.ONE_WIRE_TEMP_1.color
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricIndicator(color)
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatString(textFormat, stringResource(Res.string.one_wire_temperature), idx + 1, temp),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
            )
        }
    }
}

@Composable
private fun EnvironmentMetricsCard(
    telemetry: Telemetry,
    environmentDisplayFahrenheit: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    SelectableMetricCard(isSelected = isSelected, onClick = onClick) {
        EnvironmentMetricsContent(telemetry, environmentDisplayFahrenheit)
    }
}

@Composable
private fun EnvironmentMetricsContent(telemetry: Telemetry, environmentDisplayFahrenheit: Boolean) {
    val envMetrics = telemetry.environment_metrics ?: org.meshtastic.proto.EnvironmentMetrics()
    val time = telemetry.time.toLong() * MS_PER_SEC
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        /* Time and Temperature */
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = DateFormatter.formatDateTime(time),
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.Bold,
            )
            TemperatureDisplay(envMetrics, environmentDisplayFahrenheit)
        }

        Spacer(modifier = Modifier.height(8.dp))

        HumidityAndBarometricPressureDisplay(envMetrics)

        SoilMetricsDisplay(envMetrics, environmentDisplayFahrenheit)

        GasCompositionDisplay(envMetrics)

        LuxUVLuxDisplay(envMetrics)

        VoltageCurrentDisplay(envMetrics)
        RadiationDisplay(envMetrics)
        WindDisplay(envMetrics)
        RainfallDisplay(envMetrics)
        OneWireTemperatureDisplay(envMetrics, environmentDisplayFahrenheit)
    }
}

@PreviewLightDark
@Suppress("MagicNumber") // Compose preview with fake data
@Composable
private fun PreviewEnvironmentMetricsContent() {
    val fakeEnvMetrics =
        org.meshtastic.proto.EnvironmentMetrics(
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
            wind_speed = 5.2f,
            wind_direction = 225,
            wind_gust = 8.1f,
            wind_lull = 2.3f,
            rainfall_1h = 1.5f,
            rainfall_24h = 12.3f,
        )
    val fakeTelemetry = Telemetry(time = nowSeconds.toInt(), environment_metrics = fakeEnvMetrics)
    AppTheme { Surface { EnvironmentMetricsContent(telemetry = fakeTelemetry, environmentDisplayFahrenheit = false) } }
}
