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

package org.meshtastic.core.ui.component

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
import org.meshtastic.core.ui.icon.AirQuality
import org.meshtastic.core.ui.icon.HardwareModel
import org.meshtastic.core.ui.icon.Humidity
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NodeId
import org.meshtastic.core.ui.icon.Paxcount
import org.meshtastic.core.ui.icon.Power
import org.meshtastic.core.ui.icon.Role
import org.meshtastic.core.ui.icon.Soil
import org.meshtastic.core.ui.icon.Temperature

@Composable
fun TemperatureInfo(
    temp: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.Temperature,
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
        icon = MeshtasticIcons.Humidity,
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
        icon = MeshtasticIcons.Soil,
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
        icon = MeshtasticIcons.Soil,
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
        icon = MeshtasticIcons.Paxcount,
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
        icon = MeshtasticIcons.AirQuality,
        contentDescription = stringResource(Res.string.env_metrics_log),
        text = iaq,
        contentColor = contentColor,
    )
}

@Composable
fun PowerInfo(
    value: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.Power,
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
        icon = MeshtasticIcons.HardwareModel,
        contentDescription = "Hardware Model",
        text = hwModel,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}

@Composable
fun RoleInfo(
    role: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.Role,
        contentDescription = stringResource(Res.string.role),
        text = role,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}

@Composable
fun NodeIdInfo(
    id: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.NodeId,
        contentDescription = stringResource(Res.string.node_id),
        text = id,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}
