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
package org.meshtastic.core.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_air
import org.meshtastic.core.resources.ic_alt_route
import org.meshtastic.core.resources.ic_blur_on
import org.meshtastic.core.resources.ic_bolt
import org.meshtastic.core.resources.ic_charging_station
import org.meshtastic.core.resources.ic_compress
import org.meshtastic.core.resources.ic_data_array
import org.meshtastic.core.resources.ic_electric_bolt
import org.meshtastic.core.resources.ic_explore
import org.meshtastic.core.resources.ic_grass
import org.meshtastic.core.resources.ic_height
import org.meshtastic.core.resources.ic_light_mode
import org.meshtastic.core.resources.ic_line_axis
import org.meshtastic.core.resources.ic_navigation
import org.meshtastic.core.resources.ic_power
import org.meshtastic.core.resources.ic_satellite_alt
import org.meshtastic.core.resources.ic_scale
import org.meshtastic.core.resources.ic_social_distance
import org.meshtastic.core.resources.ic_speed
import org.meshtastic.core.resources.ic_stacked_line_chart
import org.meshtastic.core.resources.ic_thermostat
import org.meshtastic.core.resources.ic_volume_up
import org.meshtastic.core.resources.ic_water_drop

val MeshtasticIcons.Humidity: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_water_drop)
val MeshtasticIcons.Pressure: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_compress)
val MeshtasticIcons.SoilMoisture: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_grass)
val MeshtasticIcons.ElectricPower: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_electric_bolt)
val MeshtasticIcons.Distance: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_social_distance)
val MeshtasticIcons.Satellites: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_satellite_alt)
val MeshtasticIcons.DataArray: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_data_array)
val MeshtasticIcons.Chart: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_stacked_line_chart)
val MeshtasticIcons.LineAxis: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_line_axis)

val MeshtasticIcons.Altitude: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_height)
val MeshtasticIcons.Weight: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_scale)
val MeshtasticIcons.Particulate: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_blur_on)
val MeshtasticIcons.WindDirection: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_navigation)
val MeshtasticIcons.Voltage: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_bolt)
val MeshtasticIcons.Compass: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_explore)
val MeshtasticIcons.Temperature: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_thermostat)
val MeshtasticIcons.PowerSupply: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_power)
val MeshtasticIcons.AirQuality: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_air)
val MeshtasticIcons.Speed: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_speed)
val MeshtasticIcons.LightMode: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_light_mode)
val MeshtasticIcons.ChargingStation: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_charging_station)
val MeshtasticIcons.TrafficManagement: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_alt_route)
val MeshtasticIcons.VolumeUp: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_volume_up)
