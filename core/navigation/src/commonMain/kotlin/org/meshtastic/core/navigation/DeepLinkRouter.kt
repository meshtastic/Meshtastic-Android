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
import co.touchlab.kermit.Logger
import org.meshtastic.core.common.util.CommonUri

/**
 * Type-safe deep link parser for KMP Navigation 3.
 *
 * Maps an incoming OS intent URI to a list of NavKeys representing the target backstack. This ensures that when a user
 * deep links into a detail view, the logical "up" hierarchy is synthesized and correctly populated in the user-owned
 * NavBackStack list.
 *
 * Supports both legacy query-parameter URIs and modern RESTful path patterns:
 * - `/nodes` -> List of all nodes
 * - `/nodes/{destNum}` -> Node details
 * - `/nodes/{destNum}/{metric}` -> Specific node metric (e.g., `/nodes/1234/device-metrics`)
 * - `/messages` -> Conversation list
 * - `/messages/{contactKey}` -> Specific conversation
 * - `/settings` -> Settings root
 * - `/settings/{destNum}/{page}` -> Specific settings page for a node
 * - `/wifi-provision` -> WiFi provisioning screen
 * - `/wifi-provision?address={mac}` -> WiFi provisioning targeting a specific device MAC address
 */
object DeepLinkRouter {
    /**
     * Synthesizes a backstack list from an incoming Meshtastic URI.
     *
     * @param uri The incoming OS intent URI (e.g. "meshtastic://meshtastic/share?message=hello")
     * @return A list of strongly-typed NavKeys representing the backstack, or null if the URI is not recognized.
     */
    fun route(uri: CommonUri): List<NavKey>? {
        val pathSegments = uri.pathSegments.filter { it.isNotBlank() }

        if (pathSegments.isEmpty()) {
            return null
        }

        val firstSegment = pathSegments[0].lowercase()

        return when (firstSegment) {
            "share",
            "messages",
            "quickchat",
            -> routeContacts(uri, pathSegments)
            "connections" -> listOf(ConnectionsRoutes.ConnectionsGraph)
            "map" -> routeMap(uri, pathSegments)
            "nodes" -> routeNodes(uri, pathSegments)
            "settings" -> routeSettings(pathSegments)
            "channels" -> listOf(ChannelsRoutes.ChannelsGraph)
            "firmware" -> routeFirmware(pathSegments)
            "wifi-provision" -> routeWifiProvision(uri)
            else -> {
                Logger.w { "Unrecognized deep link segment: $firstSegment" }
                null
            }
        }
    }

    private fun routeContacts(uri: CommonUri, segments: List<String>): List<NavKey> {
        val firstSegment = segments[0].lowercase()
        return when (firstSegment) {
            "share" -> {
                val message = uri.getQueryParameter("message") ?: ""
                listOf(ContactsRoutes.ContactsGraph, ContactsRoutes.Share(message))
            }
            "quickchat" -> {
                listOf(ContactsRoutes.ContactsGraph, ContactsRoutes.QuickChat)
            }
            "messages" -> {
                val contactKey = if (segments.size > 1) segments[1] else uri.getQueryParameter("contactKey") ?: ""
                val message = uri.getQueryParameter("message") ?: ""
                if (contactKey.isNotBlank()) {
                    listOf(
                        ContactsRoutes.ContactsGraph,
                        ContactsRoutes.Messages(contactKey = contactKey, message = message),
                    )
                } else {
                    listOf(ContactsRoutes.ContactsGraph)
                }
            }
            else -> listOf(ContactsRoutes.ContactsGraph)
        }
    }

    private fun routeMap(uri: CommonUri, segments: List<String>): List<NavKey> {
        val waypointIdStr = if (segments.size > 1) segments[1] else uri.getQueryParameter("waypointId")
        val waypointId = waypointIdStr?.toIntOrNull()
        return listOf(MapRoutes.Map(waypointId))
    }

    private fun routeNodes(uri: CommonUri, segments: List<String>): List<NavKey> {
        val destNumStr = if (segments.size > 1) segments[1] else uri.getQueryParameter("destNum")
        val destNum = destNumStr?.toIntOrNull()

        return if (destNum == null) {
            listOf(NodesRoutes.NodesGraph)
        } else if (segments.size > 2) {
            val subRouteStr = segments[2].lowercase()
            val detailRouteFn = nodeDetailSubRoutes[subRouteStr]
            if (detailRouteFn != null) {
                listOf(NodesRoutes.NodesGraph, NodesRoutes.NodeDetailGraph(destNum), detailRouteFn(destNum))
            } else {
                listOf(NodesRoutes.NodesGraph, NodesRoutes.NodeDetail(destNum))
            }
        } else {
            listOf(NodesRoutes.NodesGraph, NodesRoutes.NodeDetail(destNum))
        }
    }

    private fun routeSettings(segments: List<String>): List<NavKey> {
        var destNum: Int? = null
        var subRouteStr: String? = null

        if (segments.size > 1) {
            val secondSegment = segments[1]
            val parsedNum = secondSegment.toIntOrNull()
            if (parsedNum != null) {
                destNum = parsedNum
                if (segments.size > 2) {
                    subRouteStr = segments[2].lowercase()
                }
            } else {
                subRouteStr = secondSegment.lowercase()
            }
        }

        if (subRouteStr == null) {
            return listOf(SettingsRoutes.SettingsGraph(destNum))
        }

        val subRoute = settingsSubRoutes[subRouteStr]
        return if (subRoute != null) {
            listOf(SettingsRoutes.SettingsGraph(destNum), subRoute)
        } else {
            listOf(SettingsRoutes.SettingsGraph(destNum))
        }
    }

    private fun routeWifiProvision(uri: CommonUri): List<NavKey> {
        val address = uri.getQueryParameter("address")
        return listOf(WifiProvisionRoutes.WifiProvision(address))
    }

    private fun routeFirmware(segments: List<String>): List<NavKey> {
        val update = if (segments.size > 1) segments[1].lowercase() == "update" else false
        return if (update) {
            listOf(FirmwareRoutes.FirmwareGraph, FirmwareRoutes.FirmwareUpdate)
        } else {
            listOf(FirmwareRoutes.FirmwareGraph)
        }
    }

    private val settingsSubRoutes: Map<String, Route> =
        mapOf(
            "device-config" to SettingsRoutes.DeviceConfiguration,
            "module-config" to SettingsRoutes.ModuleConfiguration,
            "admin" to SettingsRoutes.Administration,
            "user" to SettingsRoutes.User,
            "channel" to SettingsRoutes.ChannelConfig,
            "device" to SettingsRoutes.Device,
            "position" to SettingsRoutes.Position,
            "power" to SettingsRoutes.Power,
            "network" to SettingsRoutes.Network,
            "display" to SettingsRoutes.Display,
            "lora" to SettingsRoutes.LoRa,
            "bluetooth" to SettingsRoutes.Bluetooth,
            "security" to SettingsRoutes.Security,
            "mqtt" to SettingsRoutes.MQTT,
            "serial" to SettingsRoutes.Serial,
            "ext-notification" to SettingsRoutes.ExtNotification,
            "store-forward" to SettingsRoutes.StoreForward,
            "range-test" to SettingsRoutes.RangeTest,
            "telemetry" to SettingsRoutes.Telemetry,
            "canned-message" to SettingsRoutes.CannedMessage,
            "audio" to SettingsRoutes.Audio,
            "remote-hardware" to SettingsRoutes.RemoteHardware,
            "neighbor-info" to SettingsRoutes.NeighborInfo,
            "ambient-lighting" to SettingsRoutes.AmbientLighting,
            "detection-sensor" to SettingsRoutes.DetectionSensor,
            "paxcounter" to SettingsRoutes.Paxcounter,
            "status-message" to SettingsRoutes.StatusMessage,
            "traffic-management" to SettingsRoutes.TrafficManagement,
            "tak" to SettingsRoutes.TAK,
            "clean-node-db" to SettingsRoutes.CleanNodeDb,
            "debug-panel" to SettingsRoutes.DebugPanel,
            "about" to SettingsRoutes.About,
            "filter-settings" to SettingsRoutes.FilterSettings,
        )

    private val nodeDetailSubRoutes: Map<String, (Int) -> Route> =
        mapOf(
            "device-metrics" to { destNum -> NodeDetailRoutes.DeviceMetrics(destNum) },
            "map" to { destNum -> NodeDetailRoutes.NodeMap(destNum) },
            "position" to { destNum -> NodeDetailRoutes.PositionLog(destNum) },
            "environment" to { destNum -> NodeDetailRoutes.EnvironmentMetrics(destNum) },
            "signal" to { destNum -> NodeDetailRoutes.SignalMetrics(destNum) },
            "power" to { destNum -> NodeDetailRoutes.PowerMetrics(destNum) },
            "traceroute" to { destNum -> NodeDetailRoutes.TracerouteLog(destNum) },
            "host-metrics" to { destNum -> NodeDetailRoutes.HostMetricsLog(destNum) },
            "pax" to { destNum -> NodeDetailRoutes.PaxMetrics(destNum) },
            "neighbors" to { destNum -> NodeDetailRoutes.NeighborInfoLog(destNum) },
        )
}
