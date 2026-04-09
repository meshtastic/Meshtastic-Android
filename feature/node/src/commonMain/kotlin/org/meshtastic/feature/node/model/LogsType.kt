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
package org.meshtastic.feature.node.model

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.device_metrics_log
import org.meshtastic.core.resources.env_metrics_log
import org.meshtastic.core.resources.host_metrics_log
import org.meshtastic.core.resources.ic_charging_station
import org.meshtastic.core.resources.ic_groups
import org.meshtastic.core.resources.ic_location_on
import org.meshtastic.core.resources.ic_map
import org.meshtastic.core.resources.ic_memory
import org.meshtastic.core.resources.ic_people
import org.meshtastic.core.resources.ic_power
import org.meshtastic.core.resources.ic_route
import org.meshtastic.core.resources.ic_signal_cellular_alt
import org.meshtastic.core.resources.ic_thermostat
import org.meshtastic.core.resources.neighbor_info
import org.meshtastic.core.resources.node_map
import org.meshtastic.core.resources.pax_metrics_log
import org.meshtastic.core.resources.position_log
import org.meshtastic.core.resources.power_metrics_log
import org.meshtastic.core.resources.signal_quality
import org.meshtastic.core.resources.traceroute_log

enum class LogsType(val titleRes: StringResource, val icon: DrawableResource, val routeFactory: (Int) -> Route) {
    DEVICE(Res.string.device_metrics_log, Res.drawable.ic_charging_station, { NodeDetailRoutes.DeviceMetrics(it) }),
    NODE_MAP(Res.string.node_map, Res.drawable.ic_map, { NodeDetailRoutes.NodeMap(it) }),
    POSITIONS(Res.string.position_log, Res.drawable.ic_location_on, { NodeDetailRoutes.PositionLog(it) }),
    ENVIRONMENT(Res.string.env_metrics_log, Res.drawable.ic_thermostat, { NodeDetailRoutes.EnvironmentMetrics(it) }),
    SIGNAL(Res.string.signal_quality, Res.drawable.ic_signal_cellular_alt, { NodeDetailRoutes.SignalMetrics(it) }),
    POWER(Res.string.power_metrics_log, Res.drawable.ic_power, { NodeDetailRoutes.PowerMetrics(it) }),
    TRACEROUTE(Res.string.traceroute_log, Res.drawable.ic_route, { NodeDetailRoutes.TracerouteLog(it) }),
    NEIGHBOR_INFO(Res.string.neighbor_info, Res.drawable.ic_groups, { NodeDetailRoutes.NeighborInfoLog(it) }),
    HOST(Res.string.host_metrics_log, Res.drawable.ic_memory, { NodeDetailRoutes.HostMetricsLog(it) }),
    PAX(Res.string.pax_metrics_log, Res.drawable.ic_people, { NodeDetailRoutes.PaxMetrics(it) }),
}
