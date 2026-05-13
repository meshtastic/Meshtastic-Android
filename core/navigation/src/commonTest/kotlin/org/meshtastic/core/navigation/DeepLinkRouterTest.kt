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

import org.meshtastic.core.common.util.CommonUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkRouterTest {

    private fun route(path: String): List<*>? {
        val uri = CommonUri.parse("$DEEP_LINK_BASE_URI$path")
        return DeepLinkRouter.route(uri)
    }

    // region empty / unrecognized

    @Test
    fun `empty path returns null`() {
        assertNull(route(""))
    }

    @Test
    fun `unrecognized segment returns null`() {
        assertNull(route("/unknown-page"))
    }

    // endregion

    // region contacts / messages

    @Test
    fun `share with message`() {
        assertEquals(
            listOf(ContactsRoute.ContactsGraph, ContactsRoute.Share("hello world")),
            route("/share?message=hello%20world"),
        )
    }

    @Test
    fun `share without message defaults to empty string`() {
        assertEquals(listOf(ContactsRoute.ContactsGraph, ContactsRoute.Share("")), route("/share"))
    }

    @Test
    fun `quickchat routes to QuickChat`() {
        assertEquals(listOf(ContactsRoute.ContactsGraph, ContactsRoute.QuickChat), route("/quickchat"))
    }

    @Test
    fun `messages with contactKey path segment`() {
        assertEquals(
            listOf(ContactsRoute.ContactsGraph, ContactsRoute.Messages(contactKey = "abc123", message = "")),
            route("/messages/abc123"),
        )
    }

    @Test
    fun `messages with contactKey query param`() {
        assertEquals(
            listOf(ContactsRoute.ContactsGraph, ContactsRoute.Messages(contactKey = "contact1", message = "")),
            route("/messages?contactKey=contact1"),
        )
    }

    @Test
    fun `messages with contactKey and message`() {
        assertEquals(
            listOf(ContactsRoute.ContactsGraph, ContactsRoute.Messages(contactKey = "contact1", message = "hi")),
            route("/messages/contact1?message=hi"),
        )
    }

    @Test
    fun `messages without contactKey returns graph only`() {
        assertEquals(listOf(ContactsRoute.ContactsGraph), route("/messages"))
    }

    // endregion

    // region connections

    @Test
    fun `connections routes to ConnectionsGraph`() {
        assertEquals(listOf(ConnectionsRoute.ConnectionsGraph), route("/connections"))
    }

    // endregion

    // region map

    @Test
    fun `map without waypointId`() {
        assertEquals(listOf(MapRoute.Map(waypointId = null)), route("/map"))
    }

    @Test
    fun `map with waypointId path segment`() {
        assertEquals(listOf(MapRoute.Map(waypointId = 42)), route("/map/42"))
    }

    @Test
    fun `map with waypointId query param`() {
        assertEquals(listOf(MapRoute.Map(waypointId = 99)), route("/map?waypointId=99"))
    }

    @Test
    fun `map with invalid waypointId falls back to null`() {
        assertEquals(listOf(MapRoute.Map(waypointId = null)), route("/map/not-a-number"))
    }

    // endregion

    // region nodes

    @Test
    fun `nodes root returns NodesGraph`() {
        assertEquals(listOf(NodesRoute.NodesGraph), route("/nodes"))
    }

    @Test
    fun `nodes with destNum returns NodeDetail`() {
        assertEquals(listOf(NodesRoute.NodesGraph, NodesRoute.NodeDetail(destNum = 1234)), route("/nodes/1234"))
    }

    @Test
    fun `nodes with destNum and device-metrics sub-route`() {
        assertEquals(
            listOf(
                NodesRoute.NodesGraph,
                NodesRoute.NodeDetailGraph(destNum = 1234),
                NodeDetailRoute.DeviceMetrics(destNum = 1234),
            ),
            route("/nodes/1234/device-metrics"),
        )
    }

    @Test
    fun `nodes with destNum and map sub-route`() {
        assertEquals(
            listOf(
                NodesRoute.NodesGraph,
                NodesRoute.NodeDetailGraph(destNum = 5678),
                NodeDetailRoute.PositionLog(destNum = 5678),
            ),
            route("/nodes/5678/map"),
        )
    }

    @Test
    fun `nodes with destNum and position sub-route`() {
        assertEquals(
            listOf(
                NodesRoute.NodesGraph,
                NodesRoute.NodeDetailGraph(destNum = 100),
                NodeDetailRoute.PositionLog(destNum = 100),
            ),
            route("/nodes/100/position"),
        )
    }

    @Test
    fun `nodes with destNum and environment sub-route`() {
        assertEquals(
            listOf(
                NodesRoute.NodesGraph,
                NodesRoute.NodeDetailGraph(destNum = 100),
                NodeDetailRoute.EnvironmentMetrics(destNum = 100),
            ),
            route("/nodes/100/environment"),
        )
    }

    @Test
    fun `nodes with destNum and signal sub-route`() {
        assertEquals(
            listOf(
                NodesRoute.NodesGraph,
                NodesRoute.NodeDetailGraph(destNum = 100),
                NodeDetailRoute.SignalMetrics(destNum = 100),
            ),
            route("/nodes/100/signal"),
        )
    }

    @Test
    fun `nodes with destNum and power sub-route`() {
        assertEquals(
            listOf(
                NodesRoute.NodesGraph,
                NodesRoute.NodeDetailGraph(destNum = 100),
                NodeDetailRoute.PowerMetrics(destNum = 100),
            ),
            route("/nodes/100/power"),
        )
    }

    @Test
    fun `nodes with destNum and traceroute sub-route`() {
        assertEquals(
            listOf(
                NodesRoute.NodesGraph,
                NodesRoute.NodeDetailGraph(destNum = 100),
                NodeDetailRoute.TracerouteLog(destNum = 100),
            ),
            route("/nodes/100/traceroute"),
        )
    }

    @Test
    fun `nodes with destNum and host-metrics sub-route`() {
        assertEquals(
            listOf(
                NodesRoute.NodesGraph,
                NodesRoute.NodeDetailGraph(destNum = 100),
                NodeDetailRoute.HostMetricsLog(destNum = 100),
            ),
            route("/nodes/100/host-metrics"),
        )
    }

    @Test
    fun `nodes with destNum and pax sub-route`() {
        assertEquals(
            listOf(
                NodesRoute.NodesGraph,
                NodesRoute.NodeDetailGraph(destNum = 100),
                NodeDetailRoute.PaxMetrics(destNum = 100),
            ),
            route("/nodes/100/pax"),
        )
    }

    @Test
    fun `nodes with destNum and neighbors sub-route`() {
        assertEquals(
            listOf(
                NodesRoute.NodesGraph,
                NodesRoute.NodeDetailGraph(destNum = 100),
                NodeDetailRoute.NeighborInfoLog(destNum = 100),
            ),
            route("/nodes/100/neighbors"),
        )
    }

    @Test
    fun `nodes with destNum and unknown sub-route falls back to NodeDetail`() {
        assertEquals(
            listOf(NodesRoute.NodesGraph, NodesRoute.NodeDetail(destNum = 1234)),
            route("/nodes/1234/unknown-sub"),
        )
    }

    @Test
    fun `nodes with non-numeric destNum returns NodesGraph only`() {
        assertEquals(listOf(NodesRoute.NodesGraph), route("/nodes/not-a-number"))
    }

    @Test
    fun `nodes with destNum query param`() {
        assertEquals(listOf(NodesRoute.NodesGraph, NodesRoute.NodeDetail(destNum = 9999)), route("/nodes?destNum=9999"))
    }

    // endregion

    // region settings

    @Test
    fun `settings root returns SettingsGraph`() {
        assertEquals(listOf(SettingsRoute.SettingsGraph(destNum = null)), route("/settings"))
    }

    @Test
    fun `settings with destNum`() {
        assertEquals(listOf(SettingsRoute.SettingsGraph(destNum = 1234)), route("/settings/1234"))
    }

    @Test
    fun `settings with destNum and sub-route`() {
        assertEquals(
            listOf(SettingsRoute.SettingsGraph(destNum = 1234), SettingsRoute.About),
            route("/settings/1234/about"),
        )
    }

    @Test
    fun `settings with sub-route without destNum`() {
        assertEquals(listOf(SettingsRoute.SettingsGraph(destNum = null), SettingsRoute.LoRa), route("/settings/lora"))
    }

    @Test
    fun `settings with unknown sub-route returns SettingsGraph only`() {
        assertEquals(listOf(SettingsRoute.SettingsGraph(destNum = null)), route("/settings/nonexistent-page"))
    }

    @Test
    fun `settings all known sub-routes resolve correctly`() {
        val expectedSubRoutes =
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

        expectedSubRoutes.forEach { (slug, expectedRoute) ->
            assertEquals(
                listOf(SettingsRoute.SettingsGraph(destNum = null), expectedRoute),
                route("/settings/$slug"),
                "Settings sub-route '$slug' did not resolve to $expectedRoute",
            )
        }
    }

    // endregion

    // region channels

    @Test
    fun `channels routes to ChannelsGraph`() {
        assertEquals(listOf(ChannelsRoute.ChannelsGraph), route("/channels"))
    }

    // endregion

    // region firmware

    @Test
    fun `firmware root returns FirmwareGraph`() {
        assertEquals(listOf(FirmwareRoute.FirmwareGraph), route("/firmware"))
    }

    @Test
    fun `firmware update returns FirmwareGraph and FirmwareUpdate`() {
        assertEquals(listOf(FirmwareRoute.FirmwareGraph, FirmwareRoute.FirmwareUpdate), route("/firmware/update"))
    }

    // endregion

    // region wifi-provision

    @Test
    fun `wifi-provision without address`() {
        assertEquals(listOf(WifiProvisionRoute.WifiProvision(address = null)), route("/wifi-provision"))
    }

    @Test
    fun `wifi-provision with address query param`() {
        assertEquals(
            listOf(WifiProvisionRoute.WifiProvision(address = "AA:BB:CC:DD:EE:FF")),
            route("/wifi-provision?address=AA:BB:CC:DD:EE:FF"),
        )
    }

    // endregion

    // region case insensitivity

    @Test
    fun `route segments are case insensitive`() {
        assertEquals(listOf(NodesRoute.NodesGraph), route("/Nodes"))
        assertEquals(listOf(ConnectionsRoute.ConnectionsGraph), route("/CONNECTIONS"))
    }

    // endregion
}
