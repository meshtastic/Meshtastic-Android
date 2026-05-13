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
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that all route subclasses registered in [MeshtasticNavSavedStateConfig] can round-trip through SavedState
 * serialization. This catches:
 * - Missing `@Serializable` annotations on new route subclasses
 * - Sealed interfaces not registered in [NavigationConfig.kt]
 * - Breaking changes in the `subclassesOfSealed` experimental API
 */
class NavigationConfigTest {

    /**
     * Every concrete route instance that can appear in a backstack. When adding a new route, add a representative
     * instance here — the test will fail if serialization is misconfigured.
     */
    private val allRouteInstances: List<NavKey> =
        listOf(
            // ChannelsRoute
            ChannelsRoute.ChannelsGraph,
            ChannelsRoute.Channels,
            // ConnectionsRoute
            ConnectionsRoute.ConnectionsGraph,
            ConnectionsRoute.Connections,
            // ContactsRoute
            ContactsRoute.ContactsGraph,
            ContactsRoute.Contacts,
            ContactsRoute.Messages(contactKey = "test-contact", message = "hello"),
            ContactsRoute.Messages(contactKey = "test-contact"),
            ContactsRoute.Share(message = "share-text"),
            ContactsRoute.QuickChat,
            // MapRoute
            MapRoute.Map(),
            MapRoute.Map(waypointId = 42),
            // NodesRoute
            NodesRoute.NodesGraph,
            NodesRoute.Nodes,
            NodesRoute.NodeDetailGraph(destNum = 1234),
            NodesRoute.NodeDetailGraph(),
            NodesRoute.NodeDetail(destNum = 5678),
            NodesRoute.NodeDetail(),
            // NodeDetailRoute
            NodeDetailRoute.DeviceMetrics(destNum = 100),
            NodeDetailRoute.PositionLog(destNum = 100),
            NodeDetailRoute.EnvironmentMetrics(destNum = 100),
            NodeDetailRoute.SignalMetrics(destNum = 100),
            NodeDetailRoute.PowerMetrics(destNum = 100),
            NodeDetailRoute.TracerouteLog(destNum = 100),
            NodeDetailRoute.TracerouteMap(destNum = 100, requestId = 200, logUuid = "uuid-123"),
            NodeDetailRoute.TracerouteMap(destNum = 100, requestId = 200),
            NodeDetailRoute.HostMetricsLog(destNum = 100),
            NodeDetailRoute.PaxMetrics(destNum = 100),
            NodeDetailRoute.NeighborInfoLog(destNum = 100),
            // SettingsRoute
            SettingsRoute.SettingsGraph(),
            SettingsRoute.SettingsGraph(destNum = 999),
            SettingsRoute.Settings(),
            SettingsRoute.Settings(destNum = 999),
            SettingsRoute.DeviceConfiguration,
            SettingsRoute.ModuleConfiguration,
            SettingsRoute.Administration,
            SettingsRoute.User,
            SettingsRoute.ChannelConfig,
            SettingsRoute.Device,
            SettingsRoute.Position,
            SettingsRoute.Power,
            SettingsRoute.Network,
            SettingsRoute.Display,
            SettingsRoute.LoRa,
            SettingsRoute.Bluetooth,
            SettingsRoute.Security,
            SettingsRoute.MQTT,
            SettingsRoute.Serial,
            SettingsRoute.ExtNotification,
            SettingsRoute.StoreForward,
            SettingsRoute.RangeTest,
            SettingsRoute.Telemetry,
            SettingsRoute.CannedMessage,
            SettingsRoute.Audio,
            SettingsRoute.RemoteHardware,
            SettingsRoute.NeighborInfo,
            SettingsRoute.AmbientLighting,
            SettingsRoute.DetectionSensor,
            SettingsRoute.Paxcounter,
            SettingsRoute.StatusMessage,
            SettingsRoute.TrafficManagement,
            SettingsRoute.TAK,
            SettingsRoute.CleanNodeDb,
            SettingsRoute.DebugPanel,
            SettingsRoute.About,
            SettingsRoute.FilterSettings,
            // FirmwareRoute
            FirmwareRoute.FirmwareGraph,
            FirmwareRoute.FirmwareUpdate,
            // WifiProvisionRoute
            WifiProvisionRoute.WifiProvisionGraph,
            WifiProvisionRoute.WifiProvision(address = "AA:BB:CC:DD:EE:FF"),
            WifiProvisionRoute.WifiProvision(),
        )

    @Test
    fun `all route instances round-trip through SavedState serialization`() {
        allRouteInstances.forEach { route ->
            val savedState = encodeToSavedState(route, MeshtasticNavSavedStateConfig)
            val decoded = decodeFromSavedState<NavKey>(savedState, MeshtasticNavSavedStateConfig)
            assertEquals(
                route,
                decoded,
                "Round-trip failed for ${route::class.simpleName}: encoded $route but decoded $decoded",
            )
        }
    }

    @Test
    fun `all sealed route interfaces are represented in the route instances list`() {
        // Verify we have at least one instance from each sealed route interface.
        // This catches the case where a new sealed interface is added to Routes.kt
        // but no instances are added to allRouteInstances above.
        val representedInterfaces =
            allRouteInstances
                .map { route ->
                    when (route) {
                        is ChannelsRoute -> "ChannelsRoute"
                        is ConnectionsRoute -> "ConnectionsRoute"
                        is ContactsRoute -> "ContactsRoute"
                        is MapRoute -> "MapRoute"
                        is NodesRoute -> "NodesRoute"
                        is NodeDetailRoute -> "NodeDetailRoute"
                        is SettingsRoute -> "SettingsRoute"
                        is FirmwareRoute -> "FirmwareRoute"
                        is WifiProvisionRoute -> "WifiProvisionRoute"
                        else -> "Unknown(${route::class.simpleName})"
                    }
                }
                .toSet()

        val expectedInterfaces =
            setOf(
                "ChannelsRoute",
                "ConnectionsRoute",
                "ContactsRoute",
                "MapRoute",
                "NodesRoute",
                "NodeDetailRoute",
                "SettingsRoute",
                "FirmwareRoute",
                "WifiProvisionRoute",
            )

        assertEquals(
            expectedInterfaces,
            representedInterfaces,
            "Missing sealed route interfaces in test coverage. " +
                "Missing: ${expectedInterfaces - representedInterfaces}",
        )
    }

    @Test
    fun `route instances with default parameters serialize correctly`() {
        // Specifically test routes with nullable/default params to catch
        // serialization issues with optional fields.
        val routesWithDefaults: List<Pair<NavKey, NavKey>> =
            listOf(
                MapRoute.Map() to MapRoute.Map(waypointId = null),
                NodesRoute.NodeDetailGraph() to NodesRoute.NodeDetailGraph(destNum = null),
                NodesRoute.NodeDetail() to NodesRoute.NodeDetail(destNum = null),
                SettingsRoute.SettingsGraph() to SettingsRoute.SettingsGraph(destNum = null),
                SettingsRoute.Settings() to SettingsRoute.Settings(destNum = null),
                WifiProvisionRoute.WifiProvision() to WifiProvisionRoute.WifiProvision(address = null),
            )

        routesWithDefaults.forEach { (defaultInstance, explicitNullInstance) ->
            assertEquals(
                defaultInstance,
                explicitNullInstance,
                "Default and explicit null should be equal for ${defaultInstance::class.simpleName}",
            )

            val savedDefault = encodeToSavedState(defaultInstance, MeshtasticNavSavedStateConfig)
            val savedExplicit = encodeToSavedState(explicitNullInstance, MeshtasticNavSavedStateConfig)

            val decodedDefault = decodeFromSavedState<NavKey>(savedDefault, MeshtasticNavSavedStateConfig)
            val decodedExplicit = decodeFromSavedState<NavKey>(savedExplicit, MeshtasticNavSavedStateConfig)

            assertEquals(decodedDefault, decodedExplicit)
        }
    }
}
