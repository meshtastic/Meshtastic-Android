/*
 * Copyright (c) 2025 Meshtastic LLC
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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.rounded.SocialDistance
import androidx.compose.material.icons.twotone.SatelliteAlt
import androidx.compose.ui.graphics.vector.ImageVector

val MeshtasticIcons.Temperature: ImageVector get() = Icons.Default.Thermostat
val MeshtasticIcons.Humidity: ImageVector get() = Icons.Default.WaterDrop
val MeshtasticIcons.Soil: ImageVector get() = Icons.Default.Grass
val MeshtasticIcons.Paxcount: ImageVector get() = Icons.Default.People
val MeshtasticIcons.AirQuality: ImageVector get() = Icons.Default.Air
val MeshtasticIcons.Power: ImageVector get() = Icons.Default.ElectricBolt
val MeshtasticIcons.Distance: ImageVector get() = Icons.Rounded.SocialDistance
val MeshtasticIcons.Satellites: ImageVector get() = Icons.TwoTone.SatelliteAlt
