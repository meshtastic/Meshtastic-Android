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

package org.meshtastic.feature.node.model

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
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.device_metrics_log
import org.meshtastic.core.strings.env_metrics_log
import org.meshtastic.core.strings.host_metrics_log
import org.meshtastic.core.strings.node_map
import org.meshtastic.core.strings.pax_metrics_log
import org.meshtastic.core.strings.position_log
import org.meshtastic.core.strings.power_metrics_log
import org.meshtastic.core.strings.sig_metrics_log
import org.meshtastic.core.strings.traceroute_log

enum class LogsType(val titleRes: StringResource, val icon: ImageVector, val routeFactory: (Int) -> Route) {
    DEVICE(Res.string.device_metrics_log, Icons.Default.ChargingStation, { NodeDetailRoutes.DeviceMetrics(it) }),
    NODE_MAP(Res.string.node_map, Icons.Default.Map, { NodeDetailRoutes.NodeMap(it) }),
    POSITIONS(Res.string.position_log, Icons.Default.LocationOn, { NodeDetailRoutes.PositionLog(it) }),
    ENVIRONMENT(Res.string.env_metrics_log, Icons.Default.Thermostat, { NodeDetailRoutes.EnvironmentMetrics(it) }),
    SIGNAL(Res.string.sig_metrics_log, Icons.Default.SignalCellularAlt, { NodeDetailRoutes.SignalMetrics(it) }),
    POWER(Res.string.power_metrics_log, Icons.Default.Power, { NodeDetailRoutes.PowerMetrics(it) }),
    TRACEROUTE(Res.string.traceroute_log, Icons.Default.Route, { NodeDetailRoutes.TracerouteLog(it) }),
    HOST(Res.string.host_metrics_log, Icons.Default.Memory, { NodeDetailRoutes.HostMetricsLog(it) }),
    PAX(Res.string.pax_metrics_log, Icons.Default.People, { NodeDetailRoutes.PaxMetrics(it) }),
}
