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
package org.meshtastic.core.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.LineAxis
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.SocialDistance
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StackedLineChart
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.twotone.SatelliteAlt
import androidx.compose.ui.graphics.vector.ImageVector

val MeshtasticIcons.Temperature: ImageVector
    get() = Icons.Rounded.Thermostat
val MeshtasticIcons.Humidity: ImageVector
    get() = Icons.Rounded.WaterDrop
val MeshtasticIcons.Pressure: ImageVector
    get() = Icons.Rounded.Speed
val MeshtasticIcons.Soil: ImageVector
    get() = Icons.Rounded.Grass
val MeshtasticIcons.Paxcount: ImageVector
    get() = Icons.Rounded.People
val MeshtasticIcons.AirQuality: ImageVector
    get() = Icons.Rounded.Air
val MeshtasticIcons.Power: ImageVector
    get() = Icons.Rounded.ElectricBolt
val MeshtasticIcons.Distance: ImageVector
    get() = Icons.Rounded.SocialDistance
val MeshtasticIcons.Satellites: ImageVector
    get() = Icons.TwoTone.SatelliteAlt
val MeshtasticIcons.DataArray: ImageVector
    get() = Icons.Rounded.DataArray
val MeshtasticIcons.Speed: ImageVector
    get() = Icons.Rounded.Speed
val MeshtasticIcons.Chart: ImageVector
    get() = Icons.Rounded.StackedLineChart

val MeshtasticIcons.LineAxis: ImageVector
    get() = Icons.Rounded.LineAxis
