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

package com.geeksville.mesh.navigation

import kotlinx.serialization.Serializable

interface Route

interface Graph : Route

object ChannelsRoutes {
    @Serializable data object ChannelsGraph : Graph

    @Serializable data object Channels : Route
}

object ConnectionsRoutes {
    @Serializable data object ConnectionsGraph : Graph

    @Serializable data object Connections : Route
}

object ContactsRoutes {
    @Serializable data object ContactsGraph : Graph

    @Serializable data object Contacts : Route

    @Serializable data class Messages(val contactKey: String, val message: String = "") : Route

    @Serializable data class Share(val message: String) : Route

    @Serializable data object QuickChat : Route
}

object MapRoutes {
    @Serializable data object Map : Route
}

object NodesRoutes {
    @Serializable data object NodesGraph : Graph

    @Serializable data object Nodes : Route

    @Serializable data class NodeDetailGraph(val destNum: Int? = null) : Graph

    @Serializable data class NodeDetail(val destNum: Int? = null) : Route
}

object NodeDetailRoutes {
    @Serializable data object DeviceMetrics : Route

    @Serializable data object NodeMap : Route

    @Serializable data object PositionLog : Route

    @Serializable data object EnvironmentMetrics : Route

    @Serializable data object SignalMetrics : Route

    @Serializable data object PowerMetrics : Route

    @Serializable data object TracerouteLog : Route

    @Serializable data object HostMetricsLog : Route

    @Serializable data object PaxMetrics : Route
}
