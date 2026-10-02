/*
 * Copyright (c) 2026 Meshtastic LLC
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.formatString
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.core.model.util.adcVoltage
import org.meshtastic.core.model.util.oneWireTemperature
import org.meshtastic.core.model.util.toStormDistanceString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.adc_voltage
import org.meshtastic.core.resources.current
import org.meshtastic.core.resources.device_metrics_label_value
import org.meshtastic.core.resources.env_metrics_log
import org.meshtastic.core.resources.gas_resistance
import org.meshtastic.core.resources.humidity
import org.meshtastic.core.resources.iaq
import org.meshtastic.core.resources.iaq_definition
import org.meshtastic.core.resources.lightning_distance
import org.meshtastic.core.resources.lightning_strikes_1h
import org.meshtastic.core.resources.lux
import org.meshtastic.core.resources.metric_channel_label
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

/** "<label> <value>" — the value arrives already formatted and unit-suffixed, so no numeric format here. */
private const val LABELLED_VALUE = "%s %s"

/**
 * The degree symbol for the display unit.
 *
 * Temperatures on this screen arrive from `MetricsViewModel.filteredEnvironmentMetrics` **already converted**, so this
 * only labels them — converting again here would double-count, exactly as the 1-Wire rows warn. That is why these rows
 * cannot use `MetricFormatter.temperature`, which converts.
 */
private fun degreeUnit(isFahrenheit: Boolean) = MetricFormatter.degreeSymbol(isFahrenheit)

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

    val isImperial = state.displayUnits == MeasurementSystem.IMPERIAL

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
                isFahrenheit = state.isFahrenheit,
                isImperial = isImperial,
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onPointSelected = onPointSelected,
            )
        },
        listPart = { modifier, selectedX, lazyListState, onCardClick ->
            LazyColumn(modifier = modifier.fillMaxSize(), state = lazyListState) {
                itemsIndexed(
                    filteredTelemetries,
                    key = { index, telemetry -> "${telemetry.time}_$index" },
                    contentType = { _, _ -> "environment_metrics" },
                ) { _, telemetry ->
                    EnvironmentMetricsCard(
                        telemetry = telemetry,
                        environmentDisplayFahrenheit = state.isFahrenheit,
                        isImperial = isImperial,
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
            val unit = degreeUnit(environmentDisplayFahrenheit)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricIndicator(Environment.TEMPERATURE.color)
                Spacer(Modifier.width(4.dp))
                Text(
                    text =
                    formatString(
                        LABELLED_VALUE,
                        stringResource(Res.string.temperature),
                        "${NumberFormatter.format(temperature, 1)}$unit",
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun HumidityAndBarometricPressureDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    val humidity = envMetrics.relative_humidity?.takeUnless { it.isNaN() }
    val pressure = envMetrics.barometric_pressure?.takeIf { !it.isNaN() && it > 0 }

    if (humidity != null || pressure != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (humidity != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.HUMIDITY.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text =
                        "${stringResource(
                            Res.string.humidity,
                        )} ${MetricFormatter.percent(humidity, decimalPlaces = 2)}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 0.dp),
                    )
                }
            }
            if (pressure != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.BAROMETRIC_PRESSURE.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = MetricFormatter.pressure(pressure, decimalPlaces = 2),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
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
                            style = MaterialTheme.typography.labelLarge,
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
                                LABELLED_VALUE,
                                stringResource(Res.string.soil_temperature),
                                "${NumberFormatter.format(
                                    soilTemperature,
                                    1,
                                )}${degreeUnit(environmentDisplayFahrenheit)}",
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LuxUVLuxDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    val luxValue = envMetrics.lux?.takeUnless { it.isNaN() }
    val uvLuxValue = envMetrics.uv_lux?.takeUnless { it.isNaN() }

    if (luxValue != null || uvLuxValue != null) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (luxValue != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.LUX.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatString("%s %.0f lx", stringResource(Res.string.lux), luxValue),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (uvLuxValue != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIndicator(Environment.UV_LUX.color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatString("%s %.0f UVlx", stringResource(Res.string.uv_lux), uvLuxValue),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoltageCurrentDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    val voltage = envMetrics.voltage?.takeUnless { it.isNaN() }
    val currentValue = envMetrics.current?.takeUnless { it.isNaN() }

    if (voltage != null || currentValue != null) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (voltage != null) {
                Text(
                    text = "${stringResource(Res.string.voltage)} ${MetricFormatter.voltage(voltage)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (currentValue != null) {
                Text(
                    text =
                    "${stringResource(
                        Res.string.current,
                    )} ${MetricFormatter.current(currentValue, decimalPlaces = 2)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
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
                        style = MaterialTheme.typography.labelLarge,
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
                        style = MaterialTheme.typography.labelLarge,
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
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun WindDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics, isImperial: Boolean) {
    val speed = envMetrics.wind_speed?.takeUnless { it.isNaN() }
    val gust = envMetrics.wind_gust?.takeUnless { it.isNaN() }
    val lull = envMetrics.wind_lull?.takeUnless { it.isNaN() }

    if (speed != null || gust != null || lull != null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (speed != null) WindSpeedRow(speed, envMetrics.wind_direction, isImperial)
            if (gust != null || lull != null) WindGustLullRow(gust, lull, isImperial)
        }
    }
}

@Composable
private fun WindSpeedRow(speed: Float, direction: Int?, isImperial: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricIndicator(Environment.WIND_SPEED.color)
            Spacer(Modifier.width(4.dp))
            val dirText =
                if (direction != null) {
                    formatString(
                        "%s %s (%s %d°)",
                        stringResource(Res.string.wind_speed),
                        MetricFormatter.windSpeed(speed, isImperial),
                        stringResource(Res.string.wind_direction),
                        direction,
                    )
                } else {
                    formatString(
                        "%s %s",
                        stringResource(Res.string.wind_speed),
                        MetricFormatter.windSpeed(speed, isImperial),
                    )
                }
            Text(
                text = dirText,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun WindGustLullRow(gust: Float?, lull: Float?, isImperial: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (gust != null) {
            Text(
                text =
                "${stringResource(Res.string.wind_gust)} ${
                    MetricFormatter.windSpeed(gust, isImperial)
                }",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (lull != null) {
            Text(
                text =
                "${stringResource(Res.string.wind_lull)} ${
                    MetricFormatter.windSpeed(lull, isImperial)
                }",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun RainfallDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics, isImperial: Boolean) {
    val rainfall1h = envMetrics.rainfall_1h?.takeUnless { it.isNaN() }
    val rainfall24h = envMetrics.rainfall_24h?.takeUnless { it.isNaN() }

    if (rainfall1h != null || rainfall24h != null) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (rainfall1h != null) {
                Text(
                    text =
                    "${stringResource(
                        Res.string.rainfall_1h,
                    )} ${MetricFormatter.rainfall(rainfall1h, isImperial)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (rainfall24h != null) {
                Text(
                    text =
                    "${stringResource(
                        Res.string.rainfall_24h,
                    )} ${MetricFormatter.rainfall(rainfall24h, isImperial)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * Strikes in the last hour and distance to the storm front, from the AS3935. Absent readings are null; 0 strikes is a
 * real reading.
 */
@Composable
private fun LightningDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics, isImperial: Boolean) {
    val strikes = envMetrics.lightning_strike_count_1h
    val distanceKm = envMetrics.lightning_distance_km?.takeIf { !it.isNaN() }
    if (strikes == null && distanceKm == null) return
    val system = if (isImperial) MeasurementSystem.IMPERIAL else MeasurementSystem.METRIC

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        strikes?.let {
            Text(
                text = formatString(LABELLED_VALUE, stringResource(Res.string.lightning_strikes_1h), it.toString()),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        distanceKm?.let {
            Text(
                text =
                formatString(
                    LABELLED_VALUE,
                    stringResource(Res.string.lightning_distance),
                    it.toStormDistanceString(system),
                ),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * One row per reporting 1-Wire probe. Values arrive already converted to the display unit by the view model, so they
 * are only formatted here — a second conversion would double-count. An absent channel is `null`; 0°C is a real reading.
 */
@Composable
private fun OneWireTemperatureDisplay(
    envMetrics: org.meshtastic.proto.EnvironmentMetrics,
    environmentDisplayFahrenheit: Boolean,
) {
    val unit = MetricFormatter.degreeSymbol(environmentDisplayFahrenheit)
    Environment.oneWireTemperatures.forEachIndexed { idx, entry ->
        val temp = envMetrics.oneWireTemperature(idx)?.takeIf { !it.isNaN() } ?: return@forEachIndexed
        ChannelMetricRow(
            color = entry.color,
            label = stringResource(Res.string.one_wire_temperature),
            channelNumber = idx + 1,
            value = "${NumberFormatter.format(temp, 1)}$unit",
        )
    }
}

/** One row per reporting ADC channel. Volts need no unit conversion, and 0 V is a real reading. */
@Composable
private fun AdcVoltageDisplay(envMetrics: org.meshtastic.proto.EnvironmentMetrics) {
    Environment.adcVoltages.forEachIndexed { idx, entry ->
        val volts = envMetrics.adcVoltage(idx)?.takeIf { !it.isNaN() } ?: return@forEachIndexed
        ChannelMetricRow(
            color = entry.color,
            label = stringResource(Res.string.adc_voltage),
            channelNumber = idx + 1,
            value = MetricFormatter.voltage(volts),
        )
    }
}

@Composable
private fun ChannelMetricRow(color: Color, label: String, channelNumber: Int, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MetricIndicator(color)
        Spacer(Modifier.width(4.dp))
        Text(
            text =
            stringResource(
                Res.string.device_metrics_label_value,
                stringResource(Res.string.metric_channel_label, label, channelNumber),
                value,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun EnvironmentMetricsCard(
    telemetry: Telemetry,
    environmentDisplayFahrenheit: Boolean,
    isImperial: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    SelectableMetricCard(isSelected = isSelected, onClick = onClick) {
        EnvironmentMetricsContent(telemetry, environmentDisplayFahrenheit, isImperial)
    }
}

@Composable
private fun EnvironmentMetricsContent(
    telemetry: Telemetry,
    environmentDisplayFahrenheit: Boolean,
    isImperial: Boolean,
    timeTextOverride: String? = null,
) {
    val envMetrics = telemetry.environment_metrics ?: org.meshtastic.proto.EnvironmentMetrics.Builder().build()
    val time = telemetry.time.toLong() * MS_PER_SEC
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        /* Time and Temperature */
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = timeTextOverride ?: DateFormatter.formatDateTime(time),
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
        WindDisplay(envMetrics, isImperial)
        RainfallDisplay(envMetrics, isImperial)
        LightningDisplay(envMetrics, isImperial)
        OneWireTemperatureDisplay(envMetrics, environmentDisplayFahrenheit)
        AdcVoltageDisplay(envMetrics)
    }
}

@PreviewLightDark
@Suppress("MagicNumber") // Compose preview with fake data
@Composable
fun PreviewEnvironmentMetricsContent() {
    val fakeEnvMetrics =
        org.meshtastic.proto.EnvironmentMetrics.Builder()
            .also { wb ->
                wb.temperature = 22.5f
                wb.relative_humidity = 55.0f
                wb.barometric_pressure = 1013.25f
                wb.soil_moisture = 33
                wb.soil_temperature = 18.0f
                wb.lux = 100.0f
                wb.uv_lux = 100.0f
                wb.voltage = 3.7f
                wb.current = 0.12f
                wb.iaq = 100
                wb.radiation = 0.15f
                wb.gas_resistance = 1200.0f
                wb.wind_speed = 5.2f
                wb.wind_direction = 225
                wb.wind_gust = 8.1f
                wb.wind_lull = 2.3f
                wb.rainfall_1h = 1.5f
                wb.rainfall_24h = 12.3f
            }
            .build()
    val fakeTelemetry =
        Telemetry.Builder()
            .also { wb ->
                wb.time = 1700000000
                wb.environment_metrics = fakeEnvMetrics
            }
            .build()
    AppTheme {
        Surface {
            EnvironmentMetricsContent(
                telemetry = fakeTelemetry,
                environmentDisplayFahrenheit = false,
                isImperial = false,
                timeTextOverride = "2023-11-14 22:13",
            )
        }
    }
}

/** A log entry from an AS3935-equipped node: strikes in the last hour and the distance to the storm front. */
@PreviewLightDark
@Suppress("MagicNumber", "PreviewPublic") // fake data; public so :screenshot-tests can reference it
@Composable
fun PreviewEnvironmentMetricsContentLightning() {
    val fakeEnvMetrics =
        org.meshtastic.proto.EnvironmentMetrics.Builder()
            .also { wb ->
                wb.temperature = 19.4f
                wb.relative_humidity = 71.0f
                wb.barometric_pressure = 998.0f
                wb.lightning_strike_count_1h = 3
                wb.lightning_distance_km = 12.0f
            }
            .build()
    val fakeTelemetry =
        Telemetry.Builder()
            .also { wb ->
                wb.time = 1700000000
                wb.environment_metrics = fakeEnvMetrics
            }
            .build()
    AppTheme {
        Surface {
            EnvironmentMetricsContent(
                telemetry = fakeTelemetry,
                environmentDisplayFahrenheit = false,
                isImperial = false,
                timeTextOverride = "2023-11-14 22:13",
            )
        }
    }
}
