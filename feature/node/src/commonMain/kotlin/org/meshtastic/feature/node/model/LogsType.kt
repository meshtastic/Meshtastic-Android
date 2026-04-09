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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.device_metrics_log
import org.meshtastic.core.resources.env_metrics_log
import org.meshtastic.core.resources.host_metrics_log
import org.meshtastic.core.resources.neighbor_info
import org.meshtastic.core.resources.node_map
import org.meshtastic.core.resources.pax_metrics_log
import org.meshtastic.core.resources.position_log
import org.meshtastic.core.resources.power_metrics_log
import org.meshtastic.core.resources.signal_quality
import org.meshtastic.core.resources.traceroute_log
import org.meshtastic.core.ui.icon.ChannelUtilization
import org.meshtastic.core.ui.icon.ChargingStation
import org.meshtastic.core.ui.icon.Groups
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.Memory
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PeopleCount
import org.meshtastic.core.ui.icon.PowerSupply
import org.meshtastic.core.ui.icon.Route
import org.meshtastic.core.ui.icon.Temperature

enum class LogsType(
    val titleRes: StringResource,
    val icon: @Composable () -> ImageVector,
    val routeFactory: (Int) -> Route,
) {
    DEVICE(Res.string.device_metrics_log, { MeshtasticIcons.ChargingStation }, { NodeDetailRoutes.DeviceMetrics(it) }),
    NODE_MAP(Res.string.node_map, { MeshtasticIcons.Map }, { NodeDetailRoutes.NodeMap(it) }),
    POSITIONS(Res.string.position_log, { MeshtasticIcons.LocationOn }, { NodeDetailRoutes.PositionLog(it) }),
    ENVIRONMENT(
        Res.string.env_metrics_log,
        { MeshtasticIcons.Temperature },
        { NodeDetailRoutes.EnvironmentMetrics(it) },
    ),
    SIGNAL(Res.string.signal_quality, { MeshtasticIcons.ChannelUtilization }, { NodeDetailRoutes.SignalMetrics(it) }),
    POWER(Res.string.power_metrics_log, { MeshtasticIcons.PowerSupply }, { NodeDetailRoutes.PowerMetrics(it) }),
    TRACEROUTE(Res.string.traceroute_log, { MeshtasticIcons.Route }, { NodeDetailRoutes.TracerouteLog(it) }),
    NEIGHBOR_INFO(Res.string.neighbor_info, { MeshtasticIcons.Groups }, { NodeDetailRoutes.NeighborInfoLog(it) }),
    HOST(Res.string.host_metrics_log, { MeshtasticIcons.Memory }, { NodeDetailRoutes.HostMetricsLog(it) }),
    PAX(Res.string.pax_metrics_log, { MeshtasticIcons.PeopleCount }, { NodeDetailRoutes.PaxMetrics(it) }),
}
