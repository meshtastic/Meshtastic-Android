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

package com.geeksville.mesh.ui.node.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChargingStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.ui.graphics.vector.ImageVector
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.strings.R

internal enum class LogsType(@StringRes val titleRes: Int, val icon: ImageVector, val route: Route) {
    DEVICE(R.string.device_metrics_log, Icons.Default.ChargingStation, NodeDetailRoutes.DeviceMetrics),
    NODE_MAP(R.string.node_map, Icons.Default.Map, NodeDetailRoutes.NodeMap),
    POSITIONS(R.string.position_log, Icons.Default.LocationOn, NodeDetailRoutes.PositionLog),
    ENVIRONMENT(R.string.env_metrics_log, Icons.Default.Thermostat, NodeDetailRoutes.EnvironmentMetrics),
    SIGNAL(R.string.sig_metrics_log, Icons.Default.SignalCellularAlt, NodeDetailRoutes.SignalMetrics),
    POWER(R.string.power_metrics_log, Icons.Default.Power, NodeDetailRoutes.PowerMetrics),
    TRACEROUTE(R.string.traceroute_log, Icons.Default.Route, NodeDetailRoutes.TracerouteLog),
    HOST(R.string.host_metrics_log, Icons.Default.Memory, NodeDetailRoutes.HostMetricsLog),
    PAX(R.string.pax_metrics_log, Icons.Default.People, NodeDetailRoutes.PaxMetrics),
}
