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

@file:Suppress("TooManyFunctions")

package com.geeksville.mesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChargingStation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.model.MetricsState
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.preview.NodeEntityPreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.DistanceUnit
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.util.thenIf
import java.util.concurrent.TimeUnit
import kotlin.math.ln

@Composable
fun NodeDetailScreen(
    node: NodeEntity?,
    viewModel: MetricsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    onNavigate: (Any) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (node != null) {
        NodeDetailList(
            node = node,
            metricsState = state,
            onNavigate = onNavigate,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun NodeDetailList(
    node: NodeEntity,
    metricsState: MetricsState,
    modifier: Modifier = Modifier,
    onNavigate: (Any) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            PreferenceCategory("Details") {
                NodeDetailsContent(node)
            }
        }

        if (node.hasEnvironmentMetrics) {
            item {
                PreferenceCategory("Environment")
                EnvironmentMetrics(node, metricsState.isFahrenheit)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (node.hasPowerMetrics) {
            item {
                PreferenceCategory("Power")
                PowerMetrics(node)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item {
            PreferenceCategory(stringResource(id = R.string.logs))
            LogNavigationList(metricsState, onNavigate)
        }

        if (!metricsState.isManaged) {
            item {
                PreferenceCategory(stringResource(id = R.string.administration))
                NavCard(
                    title = stringResource(id = R.string.remote_admin),
                    icon = Icons.Default.Settings,
                    enabled = true
                ) {
                    onNavigate(Route.RadioConfig(node.num))
                }
            }
        }
    }
}

@Composable
private fun NodeDetailRow(label: String, icon: ImageVector, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
        Spacer(modifier = Modifier.weight(1f))
        Text(value)
    }
}

@Composable
private fun NodeDetailsContent(node: NodeEntity) {
    if (node.mismatchKey) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.KeyOff,
                contentDescription = stringResource(id = R.string.encryption_error),
                tint = Color.Red,
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = stringResource(id = R.string.encryption_error),
                    style = MaterialTheme.typography.h6.copy(color = Color.Red)
                )
                Text(
                    text = stringResource(id = R.string.encryption_error_text),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
    NodeDetailRow(
        label = "Node Number",
        icon = Icons.Default.Numbers,
        value = node.num.toUInt().toString()
    )
    NodeDetailRow(
        label = "User Id",
        icon = Icons.Default.Person,
        value = node.user.id
    )
    NodeDetailRow(
        label = "Role",
        icon = Icons.Default.Work,
        value = node.user.role.name
    )
    NodeDetailRow(
        label = "Hardware",
        icon = Icons.Default.Router,
        value = node.user.hwModel.name
    )
    if (node.deviceMetrics.uptimeSeconds > 0) {
        NodeDetailRow(
            label = "Uptime",
            icon = Icons.Default.CheckCircle,
            value = formatUptime(node.deviceMetrics.uptimeSeconds)
        )
    }
    NodeDetailRow(
        label = "Last heard",
        icon = Icons.Default.History,
        value = formatAgo(node.lastHeard)
    )
}

@Composable
fun LogNavigationList(state: MetricsState, onNavigate: (Any) -> Unit) {
    NavCard(
        title = stringResource(R.string.device_metrics_log),
        icon = Icons.Default.ChargingStation,
        enabled = state.hasDeviceMetrics()
    ) {
        onNavigate(Route.DeviceMetrics)
    }

    NavCard(
        title = stringResource(R.string.node_map),
        icon = Icons.Default.Map,
        enabled = state.hasPositionLogs()
    ) {
        onNavigate(Route.NodeMap)
    }

    NavCard(
        title = stringResource(R.string.position_log),
        icon = Icons.Default.LocationOn,
        enabled = state.hasPositionLogs()
    ) {
        onNavigate(Route.PositionLog)
    }

    NavCard(
        title = stringResource(R.string.env_metrics_log),
        icon = Icons.Default.Thermostat,
        enabled = state.hasEnvironmentMetrics()
    ) {
        onNavigate(Route.EnvironmentMetrics)
    }

    NavCard(
        title = stringResource(R.string.sig_metrics_log),
        icon = Icons.Default.SignalCellularAlt,
        enabled = state.hasSignalMetrics()
    ) {
        onNavigate(Route.SignalMetrics)
    }

    NavCard(
        title = stringResource(R.string.traceroute_log),
        icon = Icons.Default.Route,
        enabled = state.hasTracerouteLogs()
    ) {
        onNavigate(Route.TracerouteLog)
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    text: String,
    value: String,
    rotateIcon: Float = 0f,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 4.dp,
        modifier = Modifier
            .padding(4.dp)
            .widthIn(min = 100.dp, max = 150.dp)
            .heightIn(min = 100.dp, max = 150.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier
                    .size(24.dp)
                    .thenIf(rotateIcon != 0f) { rotate(rotateIcon) },
            )
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.subtitle2
            )
            Text(
                text = value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (value.length < 7) {
                    MaterialTheme.typography.h5
                } else {
                    MaterialTheme.typography.h6
                },
            )
        }
    }
}

private fun formatUptime(seconds: Int): String = formatUptime(seconds.toLong())

private fun formatUptime(seconds: Long): String {
    val days = TimeUnit.SECONDS.toDays(seconds)
    val hours = TimeUnit.SECONDS.toHours(seconds) % TimeUnit.DAYS.toHours(1)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % TimeUnit.HOURS.toMinutes(1)
    val secs = seconds % TimeUnit.MINUTES.toSeconds(1)

    return listOfNotNull(
        "${days}d".takeIf { days > 0 },
        "${hours}h".takeIf { hours > 0 },
        "${minutes}m".takeIf { minutes > 0 },
        "${secs}s".takeIf { secs > 0 },
    ).joinToString(" ")
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun EnvironmentMetrics(
    node: NodeEntity,
    isFahrenheit: Boolean = false,
) = with(node.environmentMetrics) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (temperature != 0f) {
            InfoCard(
                icon = Icons.Default.Thermostat,
                text = "Temperature",
                value = temperature.toTempString(isFahrenheit)
            )
        }
        if (relativeHumidity != 0f) {
            InfoCard(
                icon = Icons.Default.WaterDrop,
                text = "Humidity",
                value = "%.0f%%".format(relativeHumidity)
            )
        }
        if (temperature != 0f && relativeHumidity != 0f) {
            val dewPoint = calculateDewPoint(temperature, relativeHumidity)
            InfoCard(
                icon = ImageVector.vectorResource(R.drawable.ic_outlined_dew_point_24),
                text = "Dew Point",
                value = dewPoint.toTempString(isFahrenheit)
            )
        }
        if (barometricPressure != 0f) {
            InfoCard(
                icon = Icons.Default.Speed,
                text = "Pressure",
                value = "%.0f".format(barometricPressure)
            )
        }
        if (gasResistance != 0f) {
            InfoCard(
                icon = Icons.Default.BlurOn,
                text = "Gas Resistance",
                value = "%.0f".format(gasResistance)
            )
        }
        if (voltage != 0f) {
            InfoCard(
                icon = Icons.Default.Bolt,
                text = "Voltage",
                value = "%.2fV".format(voltage)
            )
        }
        if (current != 0f) {
            InfoCard(
                icon = Icons.Default.Power,
                text = "Current",
                value = "%.1fmA".format(current)
            )
        }
        if (iaq != 0) {
            InfoCard(
                icon = Icons.Default.Air,
                text = "IAQ",
                value = iaq.toString()
            )
        }
        if (distance != 0f) {
            InfoCard(
                icon = Icons.Default.Height,
                text = "Distance",
                value = "%.0f mm".format(distance)
            )
        }
        if (lux != 0f) {
            InfoCard(
                icon = Icons.Default.LightMode,
                text = "Lux",
                value = "%.0f".format(lux)
            )
        }
        if (hasWindSpeed()) {
            @Suppress("MagicNumber")
            val normalizedBearing = (windDirection % 360 + 360) % 360
            InfoCard(
                icon = Icons.Outlined.Navigation,
                text = "Wind",
                value = windSpeed.toSpeedString(),
                rotateIcon = normalizedBearing.toFloat(),
            )
        }
        if (weight != 0f) {
            InfoCard(
                icon = Icons.Default.Scale,
                text = "Weight",
                value = "%.2f kg".format(weight)
            )
        }
    }
}

@Suppress("MagicNumber")
private fun Float.toTempString(isFahrenheit: Boolean) = if (isFahrenheit) {
    val fahrenheit = this * 1.8F + 32
    "%.0f°F".format(fahrenheit)
} else {
    "%.0f°C".format(this)
}

@Suppress("MagicNumber")
private fun Float.toSpeedString() = when (DistanceUnit.getFromLocale()) {
    DisplayUnits.METRIC -> "%.0f km/h".format(this * 3.6)
    else -> "%.0f mph".format(this * 2.23694f)
}

// Magnus-Tetens approximation
@Suppress("MagicNumber")
private fun calculateDewPoint(tempCelsius: Float, humidity: Float): Float {
    val (a, b) = 17.27f to 237.7f
    val alpha = (a * tempCelsius) / (b + tempCelsius) + ln(humidity / 100f)
    return (b * alpha) / (a - alpha)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PowerMetrics(node: NodeEntity) = with(node.powerMetrics) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (ch1Voltage != 0f) {
            InfoCard(
                icon = Icons.Default.Bolt,
                text = "Channel 1",
                value = "%.2fV".format(ch1Voltage)
            )
        }
        if (ch1Current != 0f) {
            InfoCard(
                icon = Icons.Default.Power,
                text = "Channel 1",
                value = "%.1fmA".format(ch1Current)
            )
        }
        if (ch2Voltage != 0f) {
            InfoCard(
                icon = Icons.Default.Bolt,
                text = "Channel 2",
                value = "%.2fV".format(ch2Voltage)
            )
        }
        if (ch2Current != 0f) {
            InfoCard(
                icon = Icons.Default.Power,
                text = "Channel 2",
                value = "%.1fmA".format(ch2Current)
            )
        }
        if (ch3Voltage != 0f) {
            InfoCard(
                icon = Icons.Default.Bolt,
                text = "Channel 3",
                value = "%.2fV".format(ch3Voltage)
            )
        }
        if (ch3Current != 0f) {
            InfoCard(
                icon = Icons.Default.Power,
                text = "Channel 3",
                value = "%.1fmA".format(ch3Current)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NodeDetailsPreview(
    @PreviewParameter(NodeEntityPreviewParameterProvider::class)
    node: NodeEntity
) {
    AppTheme {
        NodeDetailList(node, MetricsState.Empty)
    }
}
