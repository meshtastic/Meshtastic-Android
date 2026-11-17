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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.air_quality
import org.meshtastic.core.resources.environment
import org.meshtastic.core.resources.formatDuration
import org.meshtastic.core.resources.node_sensor_failing
import org.meshtastic.core.resources.node_sensor_last_good_read
import org.meshtastic.core.resources.node_sensor_reading
import org.meshtastic.core.resources.node_sensor_unread_init_failed
import org.meshtastic.core.resources.node_sensor_unread_no_driver
import org.meshtastic.core.resources.node_sensor_unread_other_address
import org.meshtastic.core.resources.node_sensor_unread_unidentified
import org.meshtastic.core.resources.node_sensor_waiting
import org.meshtastic.core.resources.node_sensors_bus
import org.meshtastic.core.resources.node_sensors_bus_failed
import org.meshtastic.core.resources.node_sensors_none_found
import org.meshtastic.core.resources.node_sensors_scanning
import org.meshtastic.core.resources.node_sensors_title
import org.meshtastic.core.resources.power
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.core.ui.icon.AirQuality
import org.meshtastic.core.ui.icon.ElectricPower
import org.meshtastic.core.ui.icon.Memory
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Temperature
import org.meshtastic.feature.connections.model.SensorRowState
import org.meshtastic.feature.connections.model.SensorRowUi
import org.meshtastic.feature.connections.model.SensorsUi
import org.meshtastic.node.sensors.SensorKind
import org.meshtastic.node.sensors.SensorUnread
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** The host's I2C sensors under the node card's bearer rows: one row per device, or one for the bus itself. */
@Composable
internal fun NodeSensorsSection(sensors: SensorsUi) {
    Text(
        text = stringResource(Res.string.node_sensors_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    when (sensors) {
        SensorsUi.Scanning -> BusRow(stringResource(Res.string.node_sensors_scanning))

        is SensorsUi.BusFailed ->
            BusRow(stringResource(Res.string.node_sensors_bus_failed, sensors.reason), failing = true)

        SensorsUi.NoneFound -> BusRow(stringResource(Res.string.node_sensors_none_found))

        is SensorsUi.Found -> sensors.sensors.forEach { SensorRow(it) }
    }
}

@Composable
private fun BusRow(state: String, failing: Boolean = false) {
    BasicListItem(
        text = stringResource(Res.string.node_sensors_bus),
        supportingText = state,
        supportingTextColor = if (failing) MaterialTheme.colorScheme.error else Color.Unspecified,
        leadingIcon = MeshtasticIcons.Memory,
    )
}

@Composable
private fun SensorRow(row: SensorRowUi) {
    BasicListItem(
        text = row.name ?: row.address,
        supportingText = row.supportingText(),
        supportingTextColor =
        if (row.state is SensorRowState.Failing) MaterialTheme.colorScheme.error else Color.Unspecified,
        leadingIcon = row.feeds.firstOrNull().icon(),
    )
}

/** The state, what the sensor feeds, its address unless that is already the title, and the age of its last read. */
@Composable
private fun SensorRowUi.supportingText(): String {
    val state = state
    val parts = mutableListOf<String>()
    parts +=
        when (state) {
            SensorRowState.Waiting -> stringResource(Res.string.node_sensor_waiting)
            is SensorRowState.Reading -> stringResource(Res.string.node_sensor_reading)
            is SensorRowState.Failing -> stringResource(Res.string.node_sensor_failing, state.error)
            is SensorRowState.Unread -> stringResource(state.reason.label())
        }
    if (feeds.isNotEmpty()) {
        val labels = feeds.map { stringResource(it.label()) }
        parts += labels.joinToString()
    }
    if (name != null) parts += address
    val age =
        when (state) {
            is SensorRowState.Reading -> state.sinceGoodRead
            is SensorRowState.Failing -> state.sinceGoodRead
            else -> null
        }
    age?.let { parts += lastGoodRead(it) }
    return parts.joinToString(" · ")
}

@Composable
private fun lastGoodRead(age: Duration): String =
    stringResource(Res.string.node_sensor_last_good_read, formatDuration(age.inWholeSeconds, DurationUnit.MINUTES))

private fun SensorKind.label(): StringResource = when (this) {
    SensorKind.ENVIRONMENT -> Res.string.environment
    SensorKind.AIR_QUALITY -> Res.string.air_quality
    SensorKind.POWER -> Res.string.power
}

private fun SensorUnread.label(): StringResource = when (this) {
    SensorUnread.UNIDENTIFIED -> Res.string.node_sensor_unread_unidentified
    SensorUnread.NO_DRIVER -> Res.string.node_sensor_unread_no_driver
    SensorUnread.INIT_FAILED -> Res.string.node_sensor_unread_init_failed
    SensorUnread.OTHER_ADDRESS -> Res.string.node_sensor_unread_other_address
}

@Composable
private fun SensorKind?.icon(): ImageVector = when (this) {
    SensorKind.ENVIRONMENT -> MeshtasticIcons.Temperature
    SensorKind.AIR_QUALITY -> MeshtasticIcons.AirQuality
    SensorKind.POWER -> MeshtasticIcons.ElectricPower
    null -> MeshtasticIcons.Memory
}
