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
package org.meshtastic.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

const val DEEP_LINK_BASE_URI = "meshtastic://meshtastic"

interface Route : NavKey

interface Graph : Route

@Serializable
sealed interface ChannelsRoute : Route {
    @Serializable data object Channels : ChannelsRoute, Graph
}

@Serializable
sealed interface ConnectionsRoute : Route {
    /**
     * @param address Optional prefixed device address (e.g. `t192.168.1.1:4403`, `xAA:BB:CC:DD:EE:FF`) to auto-connect
     *   to when this route is deep-linked into, e.g. `/connections?address=t192.168.1.1:4403`.
     */
    @Serializable data class Connections(val address: String? = null) :
        ConnectionsRoute,
        Graph
}

@Serializable
sealed interface ContactsRoute : Route {
    @Serializable data object Contacts : ContactsRoute, Graph

    @Serializable data class Messages(val contactKey: String, val message: String = "") : ContactsRoute

    @Serializable data class Share(val message: String) : ContactsRoute

    @Serializable data object QuickChat : ContactsRoute
}

@Serializable
sealed interface MapRoute : Route {
    @Serializable data class Map(val waypointId: Int? = null) : MapRoute
}

@Serializable
sealed interface NodesRoute : Route {
    @Serializable data object Nodes : NodesRoute, Graph

    @Serializable data class NodeDetail(val destNum: Int? = null) : NodesRoute
}

@Serializable
sealed interface NodeDetailRoute : Route {
    @Serializable data class DeviceMetrics(val destNum: Int) : NodeDetailRoute

    @Serializable data class PositionLog(val destNum: Int) : NodeDetailRoute

    @Serializable data class EnvironmentMetrics(val destNum: Int) : NodeDetailRoute

    @Serializable data class SignalMetrics(val destNum: Int) : NodeDetailRoute

    @Serializable data class PowerMetrics(val destNum: Int) : NodeDetailRoute

    @Serializable data class TracerouteLog(val destNum: Int) : NodeDetailRoute

    @Serializable
    data class TracerouteMap(val destNum: Int, val requestId: Int, val logUuid: String? = null) : NodeDetailRoute

    @Serializable data class HostMetricsLog(val destNum: Int) : NodeDetailRoute

    @Serializable data class PaxMetrics(val destNum: Int) : NodeDetailRoute

    @Serializable data class AirQualityMetrics(val destNum: Int) : NodeDetailRoute

    @Serializable data class NeighborInfoLog(val destNum: Int) : NodeDetailRoute
}

@Serializable
sealed interface SettingsRoute : Route {
    @Serializable data class Settings(val destNum: Int? = null) :
        SettingsRoute,
        Graph

    @Serializable data object DeviceConfiguration : SettingsRoute

    @Serializable data object ModuleConfiguration : SettingsRoute

    @Serializable data object Administration : SettingsRoute

    // region radio Config Routes

    @Serializable data object User : SettingsRoute

    @Serializable data object ChannelConfig : SettingsRoute

    @Serializable data object Device : SettingsRoute

    @Serializable data object Position : SettingsRoute

    @Serializable data object Power : SettingsRoute

    @Serializable data object Network : SettingsRoute

    @Serializable data object Display : SettingsRoute

    @Serializable data object LoRa : SettingsRoute

    @Serializable data object Bluetooth : SettingsRoute

    @Serializable data object Security : SettingsRoute

    // endregion

    // region module config routes

    @Serializable data object MQTT : SettingsRoute

    @Serializable data object Serial : SettingsRoute

    @Serializable data object ExtNotification : SettingsRoute

    @Serializable data object StoreForward : SettingsRoute

    @Serializable data object RangeTest : SettingsRoute

    @Serializable data object Telemetry : SettingsRoute

    @Serializable data object CannedMessage : SettingsRoute

    @Serializable data object Audio : SettingsRoute

    @Serializable data object RemoteHardware : SettingsRoute

    @Serializable data object NeighborInfo : SettingsRoute

    @Serializable data object AmbientLighting : SettingsRoute

    @Serializable data object DetectionSensor : SettingsRoute

    @Serializable data object Paxcounter : SettingsRoute

    @Serializable data object StatusMessage : SettingsRoute

    @Serializable data object TAK : SettingsRoute

    @Serializable data object MeshBeacon : SettingsRoute

    // endregion

    // region advanced config routes

    @Serializable data object CleanNodeDb : SettingsRoute

    @Serializable data object TakServer : SettingsRoute

    @Serializable data object DebugPanel : SettingsRoute

    @Serializable data object About : SettingsRoute

    @Serializable data object FilterSettings : SettingsRoute

    @Serializable data object NodeList : SettingsRoute

    @Serializable data object DeviceLinks : SettingsRoute

    @Serializable data object AppFunctionsSettings : SettingsRoute

    // endregion

    // region help & documentation routes

    @Serializable data object HelpDocs : SettingsRoute

    @Serializable data class HelpDocPage(val pageId: String) : SettingsRoute

    // endregion
}

@Serializable
sealed interface FirmwareRoute : Route {
    @Serializable data object FirmwareGraph : FirmwareRoute, Graph

    @Serializable data object FirmwareUpdate : FirmwareRoute
}

@Serializable
sealed interface WifiProvisionRoute : Route {
    @Serializable data object WifiProvisionGraph : WifiProvisionRoute, Graph

    @Serializable data class WifiProvision(val address: String? = null) : WifiProvisionRoute
}

@Serializable
sealed interface DiscoveryRoute : Route {
    @Serializable data object DiscoveryGraph : DiscoveryRoute, Graph

    @Serializable data object DiscoveryScan : DiscoveryRoute

    @Serializable data class DiscoverySummary(val sessionId: Long) : DiscoveryRoute

    @Serializable data object DiscoveryHistory : DiscoveryRoute

    @Serializable data class DiscoveryHistoryDetail(val sessionId: Long) : DiscoveryRoute

    @Serializable data class DiscoveryMap(val sessionId: Long) : DiscoveryRoute
}
