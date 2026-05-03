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

package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.baro_pressure
import org.meshtastic.core.resources.env_metrics_log
import org.meshtastic.core.resources.hardware_model
import org.meshtastic.core.resources.humidity
import org.meshtastic.core.resources.iaq
import org.meshtastic.core.resources.node_id
import org.meshtastic.core.resources.pax
import org.meshtastic.core.resources.pax_metrics_log
import org.meshtastic.core.resources.role
import org.meshtastic.core.resources.soil_moisture
import org.meshtastic.core.resources.soil_temperature
import org.meshtastic.core.resources.temperature
import org.meshtastic.core.resources.uptime
import org.meshtastic.core.ui.icon.AirQuality
import org.meshtastic.core.ui.icon.ArrowCircleUp
import org.meshtastic.core.ui.icon.ElectricPower
import org.meshtastic.core.ui.icon.HardwareModel
import org.meshtastic.core.ui.icon.Humidity
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NodeId
import org.meshtastic.core.ui.icon.PeopleCount
import org.meshtastic.core.ui.icon.Pressure
import org.meshtastic.core.ui.icon.Role
import org.meshtastic.core.ui.icon.SoilMoisture
import org.meshtastic.core.ui.icon.Temperature
import org.meshtastic.core.ui.icon.role
import org.meshtastic.proto.Config

private const val SIZE_ICON = 14

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
        label = stringResource(Res.string.temperature),
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
        label = stringResource(Res.string.humidity),
        text = humidity,
        contentColor = contentColor,
    )
}

@Composable
fun PressureInfo(
    pressure: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.Pressure,
        contentDescription = stringResource(Res.string.env_metrics_log),
        label = stringResource(Res.string.baro_pressure),
        text = pressure,
        contentColor = contentColor,
    )
}

@Composable
fun SoilTemperatureInfo(
    temp: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    OverlayIconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.SoilMoisture,
        overlayIcon = MeshtasticIcons.Temperature,
        contentDescription = stringResource(Res.string.env_metrics_log),
        label = stringResource(Res.string.soil_temperature),
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
    OverlayIconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.SoilMoisture,
        overlayIcon = MeshtasticIcons.Humidity,
        contentDescription = stringResource(Res.string.env_metrics_log),
        label = stringResource(Res.string.soil_moisture),
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
        icon = MeshtasticIcons.PeopleCount,
        contentDescription = stringResource(Res.string.pax_metrics_log),
        label = stringResource(Res.string.pax),
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
        label = stringResource(Res.string.iaq),
        text = iaq,
        contentColor = contentColor,
    )
}

@Composable
fun PowerInfo(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.ElectricPower,
        contentDescription = stringResource(Res.string.env_metrics_log),
        label = label,
        text = value,
        contentColor = contentColor,
    )
}

@Composable
fun UptimeInfo(
    uptime: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.ArrowCircleUp,
        contentDescription = stringResource(Res.string.uptime),
        label = stringResource(Res.string.uptime),
        text = uptime,
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
        contentDescription = stringResource(Res.string.hardware_model),
        text = hwModel,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}

@Composable
fun RoleInfo(
    role: Config.DeviceConfig.Role,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.role(role),
        contentDescription = stringResource(Res.string.role),
        text = role.name,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}

@Composable
fun RoleInfo(role: String, modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
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
fun NodeIdInfo(id: String, modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.NodeId,
        contentDescription = stringResource(Res.string.node_id),
        text = id,
        style = MaterialTheme.typography.labelSmall,
        contentColor = contentColor,
    )
}

@Composable
@Suppress("MagicNumber")
fun OverlayIconInfo(
    icon: ImageVector,
    overlayIcon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    text: String? = null,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val foregroundPainter = rememberVectorPainter(overlayIcon)
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor.copy(alpha = 0.65f),
            modifier =
            Modifier.size(SIZE_ICON.dp).drawWithContent {
                drawContent()
                val badgeSize = size.width * .5f
                with(foregroundPainter) {
                    draw(size = Size(badgeSize, badgeSize), colorFilter = ColorFilter.tint(contentColor))
                }
            },
        )
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
                color = contentColor.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
            )
        }
        text?.let {
            Text(
                text = it,
                style = style.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                color = contentColor.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
