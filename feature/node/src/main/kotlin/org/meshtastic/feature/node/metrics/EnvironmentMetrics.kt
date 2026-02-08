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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.UnitConversions.celsiusToFahrenheit
import org.meshtastic.core.model.util.UnitConversions.convertToBaseUnit
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.current
import org.meshtastic.core.strings.env_metrics_log
import org.meshtastic.core.strings.gas_resistance
import org.meshtastic.core.strings.humidity
import org.meshtastic.core.strings.iaq
import org.meshtastic.core.strings.iaq_definition
import org.meshtastic.core.strings.info
import org.meshtastic.core.strings.logs
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
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.metrics.CommonCharts.DATE_TIME_FORMAT
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.feature.node.metrics.CommonCharts.SCROLL_BIAS
import org.meshtastic.proto.Telemetry

@Suppress("LongMethod")
@Composable
fun EnvironmentMetricsScreen(viewModel: MetricsViewModel = hiltViewModel(), onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val environmentState by viewModel.environmentState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val graphData = environmentState.environmentMetricsForGraphing(state.isFahrenheit)
    val data = graphData.metrics

    val lazyListState = rememberLazyListState()
    val vicoScrollState = rememberVicoScrollState()
    val coroutineScope = rememberCoroutineScope()
    var selectedX by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NodeRequestEffect.ShowFeedback -> {
                    @Suppress("SpreadOperator")
                    snackbarHostState.showSnackbar(getString(effect.resource, *effect.args.toTypedArray()))
                }
            }
        }
    }

    val processedTelemetries: List<Telemetry> =
        if (state.isFahrenheit) {
            data.map { telemetry ->
                val em = telemetry.environment_metrics ?: return@map telemetry
                val temperatureFahrenheit = em.temperature?.let { celsiusToFahrenheit(it) }
                val soilTemperatureFahrenheit = em.soil_temperature?.let { celsiusToFahrenheit(it) }
                telemetry.copy(
                    environment_metrics =
                    em.copy(temperature = temperatureFahrenheit, soil_temperature = soilTemperatureFahrenheit),
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
                subtitle =
                stringResource(Res.string.env_metrics_log) +
                    " (${processedTelemetries.size} ${stringResource(Res.string.logs)})",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(onClick = { displayInfoDialog = true }) {
                        Icon(imageVector = Icons.Rounded.Info, contentDescription = stringResource(Res.string.info))
                    }
                    if (!state.isLocal) {
                        IconButton(onClick = { viewModel.requestTelemetry(TelemetryType.ENVIRONMENT) }) {
                            androidx.compose.material3.Icon(
                                imageVector = MeshtasticIcons.Refresh,
                                contentDescription = null,
                            )
                        }
                    }
                },
                onClickChip = {},
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (displayInfoDialog) {
                LegendInfoDialog(
                    infoData = listOf(InfoDialogData(Res.string.iaq, Res.string.iaq_definition, Environment.IAQ.color)),
                    onDismiss = { displayInfoDialog = false },
                )
            }

            AdaptiveMetricLayout(
                chartPart = { modifier ->
                    EnvironmentMetricsChart(
                        modifier = modifier,
                        telemetries = processedTelemetries.reversed(),
                        graphData = graphData,
                        vicoScrollState = vicoScrollState,
                        selectedX = selectedX,
                        onPointSelected = { x ->
                            selectedX = x
                            val index = processedTelemetries.indexOfFirst { (it.time ?: 0).toDouble() == x }
                            if (index != -1) {
                                coroutineScope.launch { lazyListState.animateScrollToItem(index) }
                            }
                        },
                    )
                },
                listPart = { modifier ->
                    LazyColumn(modifier = modifier.fillMaxSize(), state = lazyListState) {
                        itemsIndexed(processedTelemetries) { _, telemetry ->
                            EnvironmentMetricsCard(
                                telemetry = telemetry,
                                environmentDisplayFahrenheit = state.isFahrenheit,
                                isSelected = (telemetry.time ?: 0).toDouble() == selectedX,
                                onClick = {
                                    selectedX = (telemetry.time ?: 0).toDouble()
                                    coroutineScope.launch {
                                        vicoScrollState.animateScroll(
                                            Scroll.Absolute.x((telemetry.time ?: 0).toDouble(), SCROLL_BIAS),
                                        )
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
    }
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
                    text = textFormat.format(stringResource(Res.string.temperature), temperature),
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
                        text = "%s %.2f%%".format(stringResource(Res.string.humidity), humidity),
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
                        text = "%.2f hPa".format(pressure),
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
                            soilMoistureTextFormat.format(
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
                            soilTemperatureTextFormat.format(
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
                        text = "%s %.0f lx".format(stringResource(Res.string.lux), luxValue),
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
                        text = "%s %.0f UVlx".format(stringResource(Res.string.uv_lux), uvLuxValue),
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
                    text = "%s %.2f V".format(stringResource(Res.string.voltage), voltage),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
            if (hasCurrent) {
                val current = convertToBaseUnit(envMetrics.current!!)
                Text(
                    text = "%s %.2f A".format(stringResource(Res.string.current), current),
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
                        text = "%s %.2f Ohm".format(stringResource(Res.string.gas_resistance), gasResistance),
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
                Text(
                    text = "%s %.2f µR/h".format(stringResource(Res.string.radiation), radiation),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.labelLarge.fontSize,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EnvironmentMetricsCard(
    telemetry: Telemetry,
    environmentDisplayFahrenheit: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val envMetrics = telemetry.environment_metrics ?: org.meshtastic.proto.EnvironmentMetrics()
    val time = (telemetry.time ?: 0).toLong() * MS_PER_SEC
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onClick() },
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors =
        CardDefaults.cardColors(
            containerColor =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Surface(color = Color.Transparent) {
            SelectionContainer { EnvironmentMetricsContent(telemetry, environmentDisplayFahrenheit) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EnvironmentMetricsContent(telemetry: Telemetry, environmentDisplayFahrenheit: Boolean) {
    val envMetrics = telemetry.environment_metrics ?: org.meshtastic.proto.EnvironmentMetrics()
    val time = (telemetry.time ?: 0).toLong() * MS_PER_SEC
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        /* Time and Temperature */
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = DATE_TIME_FORMAT.format(time), style = MaterialTheme.typography.titleMediumEmphasized)
            TemperatureDisplay(envMetrics, environmentDisplayFahrenheit)
        }

        Spacer(modifier = Modifier.height(8.dp))

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
        )
    val fakeTelemetry =
        Telemetry(time = (System.currentTimeMillis() / 1000).toInt(), environment_metrics = fakeEnvMetrics)
    MaterialTheme {
        Surface { EnvironmentMetricsContent(telemetry = fakeTelemetry, environmentDisplayFahrenheit = false) }
    }
}
