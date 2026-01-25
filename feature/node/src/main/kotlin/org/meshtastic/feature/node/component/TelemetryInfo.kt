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
package org.meshtastic.feature.node.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.env_metrics_log
import org.meshtastic.core.strings.node_id
import org.meshtastic.core.strings.pax_metrics_log
import org.meshtastic.core.strings.role

@Composable
fun TemperatureInfo(
    temp: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.Thermostat,
        contentDescription = stringResource(Res.string.env_metrics_log),
        text = temp,
        contentColor = contentColor,
    )
}

@Composable
fun HumidityInfo(
    humidity: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.WaterDrop,
        contentDescription = stringResource(Res.string.env_metrics_log),
        text = humidity,
        contentColor = contentColor,
    )
}

@Composable
fun SoilTemperatureInfo(
    temp: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.Grass,
        contentDescription = stringResource(Res.string.env_metrics_log),
        text = temp,
        contentColor = contentColor,
    )
}

@Composable
fun SoilMoistureInfo(
    moisture: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.Grass,
        contentDescription = stringResource(Res.string.env_metrics_log),
        text = moisture,
        contentColor = contentColor,
    )
}

@Composable
fun PaxcountInfo(
    pax: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.People,
        contentDescription = stringResource(Res.string.pax_metrics_log),
        text = pax,
        contentColor = contentColor,
    )
}

@Composable
fun AirQualityInfo(
    iaq: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.Air,
        contentDescription = stringResource(Res.string.env_metrics_log),
        text = iaq,
        contentColor = contentColor,
    )
}

@Composable
fun PowerInfo(value: String, modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.ElectricBolt,
        contentDescription = stringResource(Res.string.env_metrics_log),
        text = value,
        contentColor = contentColor,
    )
}

@Composable
fun HardwareInfo(
    hwModel: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.Router,
        contentDescription = "Hardware Model",
        text = hwModel,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}

@Composable
fun RoleInfo(role: String, modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.Work,
        contentDescription = stringResource(Res.string.role),
        text = role,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}

@Composable
fun NodeIdInfo(id: String, modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    IconInfo(
        modifier = modifier,
        icon = Icons.Rounded.Fingerprint,
        contentDescription = stringResource(Res.string.node_id),
        text = id,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}
