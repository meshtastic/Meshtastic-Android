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

            "connections" -> listOf(ConnectionsRoute.ConnectionsGraph)

            "map" -> routeMap(uri, pathSegments)

            "nodes" -> routeNodes(uri, pathSegments)

            "settings" -> routeSettings(pathSegments)

            "channels" -> listOf(ChannelsRoute.ChannelsGraph)

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
                listOf(ContactsRoute.ContactsGraph, ContactsRoute.Share(message))
            }

            "quickchat" -> {
                listOf(ContactsRoute.ContactsGraph, ContactsRoute.QuickChat)
            }

            "messages" -> {
                val contactKey = if (segments.size > 1) segments[1] else uri.getQueryParameter("contactKey") ?: ""
                val message = uri.getQueryParameter("message") ?: ""
                if (contactKey.isNotBlank()) {
                    listOf(
                        ContactsRoute.ContactsGraph,
                        ContactsRoute.Messages(contactKey = contactKey, message = message),
                    )
                } else {
                    listOf(ContactsRoute.ContactsGraph)
                }
            }

            else -> listOf(ContactsRoute.ContactsGraph)
        }
    }

    private fun routeMap(uri: CommonUri, segments: List<String>): List<NavKey> {
        val waypointIdStr = if (segments.size > 1) segments[1] else uri.getQueryParameter("waypointId")
        val waypointId = waypointIdStr?.toIntOrNull()
        return listOf(MapRoute.Map(waypointId))
    }

    private fun routeNodes(uri: CommonUri, segments: List<String>): List<NavKey> {
        val destNumStr = if (segments.size > 1) segments[1] else uri.getQueryParameter("destNum")
        val destNum = destNumStr?.toIntOrNull()

        return if (destNum == null) {
            listOf(NodesRoute.NodesGraph)
        } else if (segments.size > 2) {
            val subRouteStr = segments[2].lowercase()
            val detailRouteFn = nodeDetailSubRoutes[subRouteStr]
            if (detailRouteFn != null) {
                listOf(NodesRoute.NodesGraph, NodesRoute.NodeDetailGraph(destNum), detailRouteFn(destNum))
            } else {
                listOf(NodesRoute.NodesGraph, NodesRoute.NodeDetail(destNum))
            }
        } else {
            listOf(NodesRoute.NodesGraph, NodesRoute.NodeDetail(destNum))
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
            return listOf(SettingsRoute.SettingsGraph(destNum))
        }

        val subRoute = settingsSubRoutes[subRouteStr]
        return if (subRoute != null) {
            listOf(SettingsRoute.SettingsGraph(destNum), subRoute)
        } else {
            listOf(SettingsRoute.SettingsGraph(destNum))
        }
    }

    private fun routeWifiProvision(uri: CommonUri): List<NavKey> {
        val address = uri.getQueryParameter("address")
        return listOf(WifiProvisionRoute.WifiProvision(address))
    }

    private fun routeFirmware(segments: List<String>): List<NavKey> {
        val update = if (segments.size > 1) segments[1].lowercase() == "update" else false
        return if (update) {
            listOf(FirmwareRoute.FirmwareGraph, FirmwareRoute.FirmwareUpdate)
        } else {
            listOf(FirmwareRoute.FirmwareGraph)
        }
    }

    private val settingsSubRoutes: Map<String, Route> =
        mapOf(
            "device-config" to SettingsRoute.DeviceConfiguration,
            "module-config" to SettingsRoute.ModuleConfiguration,
            "admin" to SettingsRoute.Administration,
            "user" to SettingsRoute.User,
            "channel" to SettingsRoute.ChannelConfig,
            "device" to SettingsRoute.Device,
            "position" to SettingsRoute.Position,
            "power" to SettingsRoute.Power,
            "network" to SettingsRoute.Network,
            "display" to SettingsRoute.Display,
            "lora" to SettingsRoute.LoRa,
            "bluetooth" to SettingsRoute.Bluetooth,
            "security" to SettingsRoute.Security,
            "mqtt" to SettingsRoute.MQTT,
            "serial" to SettingsRoute.Serial,
            "ext-notification" to SettingsRoute.ExtNotification,
            "store-forward" to SettingsRoute.StoreForward,
            "range-test" to SettingsRoute.RangeTest,
            "telemetry" to SettingsRoute.Telemetry,
            "canned-message" to SettingsRoute.CannedMessage,
            "audio" to SettingsRoute.Audio,
            "remote-hardware" to SettingsRoute.RemoteHardware,
            "neighbor-info" to SettingsRoute.NeighborInfo,
            "ambient-lighting" to SettingsRoute.AmbientLighting,
            "detection-sensor" to SettingsRoute.DetectionSensor,
            "paxcounter" to SettingsRoute.Paxcounter,
            "status-message" to SettingsRoute.StatusMessage,
            "traffic-management" to SettingsRoute.TrafficManagement,
            "tak" to SettingsRoute.TAK,
            "clean-node-db" to SettingsRoute.CleanNodeDb,
            "debug-panel" to SettingsRoute.DebugPanel,
            "about" to SettingsRoute.About,
            "filter-settings" to SettingsRoute.FilterSettings,
        )

    private val nodeDetailSubRoutes: Map<String, (Int) -> Route> =
        mapOf(
            "device-metrics" to { destNum -> NodeDetailRoute.DeviceMetrics(destNum) },
            "map" to { destNum -> NodeDetailRoute.PositionLog(destNum) },
            "position" to { destNum -> NodeDetailRoute.PositionLog(destNum) },
            "environment" to { destNum -> NodeDetailRoute.EnvironmentMetrics(destNum) },
            "signal" to { destNum -> NodeDetailRoute.SignalMetrics(destNum) },
            "power" to { destNum -> NodeDetailRoute.PowerMetrics(destNum) },
            "traceroute" to { destNum -> NodeDetailRoute.TracerouteLog(destNum) },
            "host-metrics" to { destNum -> NodeDetailRoute.HostMetricsLog(destNum) },
            "pax" to { destNum -> NodeDetailRoute.PaxMetrics(destNum) },
            "neighbors" to { destNum -> NodeDetailRoute.NeighborInfoLog(destNum) },
        )
}
