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
package org.meshtastic.desktop.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.navigation.ChannelsRoutes
import org.meshtastic.core.navigation.ConnectionsRoutes
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.FirmwareRoutes
import org.meshtastic.core.navigation.MapRoutes
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.ui.navigation.icon
import org.meshtastic.desktop.navigation.desktopNavGraph

/**
 * Polymorphic serialization configuration for Navigation 3 saved-state support. Registers all route types used in the
 * desktop navigation graph.
 */
private val navSavedStateConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            // Nodes
            subclass(NodesRoutes.NodesGraph::class, NodesRoutes.NodesGraph.serializer())
            subclass(NodesRoutes.Nodes::class, NodesRoutes.Nodes.serializer())
            subclass(NodesRoutes.NodeDetailGraph::class, NodesRoutes.NodeDetailGraph.serializer())
            subclass(NodesRoutes.NodeDetail::class, NodesRoutes.NodeDetail.serializer())
            // Node detail sub-screens
            subclass(NodeDetailRoutes.DeviceMetrics::class, NodeDetailRoutes.DeviceMetrics.serializer())
            subclass(NodeDetailRoutes.NodeMap::class, NodeDetailRoutes.NodeMap.serializer())
            subclass(NodeDetailRoutes.PositionLog::class, NodeDetailRoutes.PositionLog.serializer())
            subclass(NodeDetailRoutes.EnvironmentMetrics::class, NodeDetailRoutes.EnvironmentMetrics.serializer())
            subclass(NodeDetailRoutes.SignalMetrics::class, NodeDetailRoutes.SignalMetrics.serializer())
            subclass(NodeDetailRoutes.PowerMetrics::class, NodeDetailRoutes.PowerMetrics.serializer())
            subclass(NodeDetailRoutes.TracerouteLog::class, NodeDetailRoutes.TracerouteLog.serializer())
            subclass(NodeDetailRoutes.TracerouteMap::class, NodeDetailRoutes.TracerouteMap.serializer())
            subclass(NodeDetailRoutes.HostMetricsLog::class, NodeDetailRoutes.HostMetricsLog.serializer())
            subclass(NodeDetailRoutes.PaxMetrics::class, NodeDetailRoutes.PaxMetrics.serializer())
            subclass(NodeDetailRoutes.NeighborInfoLog::class, NodeDetailRoutes.NeighborInfoLog.serializer())
            // Conversations
            subclass(ContactsRoutes.ContactsGraph::class, ContactsRoutes.ContactsGraph.serializer())
            subclass(ContactsRoutes.Contacts::class, ContactsRoutes.Contacts.serializer())
            subclass(ContactsRoutes.Messages::class, ContactsRoutes.Messages.serializer())
            subclass(ContactsRoutes.Share::class, ContactsRoutes.Share.serializer())
            subclass(ContactsRoutes.QuickChat::class, ContactsRoutes.QuickChat.serializer())
            // Map
            subclass(MapRoutes.Map::class, MapRoutes.Map.serializer())
            // Firmware
            subclass(FirmwareRoutes.FirmwareGraph::class, FirmwareRoutes.FirmwareGraph.serializer())
            subclass(FirmwareRoutes.FirmwareUpdate::class, FirmwareRoutes.FirmwareUpdate.serializer())
            // Settings
            subclass(SettingsRoutes.SettingsGraph::class, SettingsRoutes.SettingsGraph.serializer())
            subclass(SettingsRoutes.Settings::class, SettingsRoutes.Settings.serializer())
            subclass(SettingsRoutes.DeviceConfiguration::class, SettingsRoutes.DeviceConfiguration.serializer())
            subclass(SettingsRoutes.ModuleConfiguration::class, SettingsRoutes.ModuleConfiguration.serializer())
            subclass(SettingsRoutes.Administration::class, SettingsRoutes.Administration.serializer())
            // Settings - Config routes
            subclass(SettingsRoutes.User::class, SettingsRoutes.User.serializer())
            subclass(SettingsRoutes.ChannelConfig::class, SettingsRoutes.ChannelConfig.serializer())
            subclass(SettingsRoutes.Device::class, SettingsRoutes.Device.serializer())
            subclass(SettingsRoutes.Position::class, SettingsRoutes.Position.serializer())
            subclass(SettingsRoutes.Power::class, SettingsRoutes.Power.serializer())
            subclass(SettingsRoutes.Network::class, SettingsRoutes.Network.serializer())
            subclass(SettingsRoutes.Display::class, SettingsRoutes.Display.serializer())
            subclass(SettingsRoutes.LoRa::class, SettingsRoutes.LoRa.serializer())
            subclass(SettingsRoutes.Bluetooth::class, SettingsRoutes.Bluetooth.serializer())
            subclass(SettingsRoutes.Security::class, SettingsRoutes.Security.serializer())
            // Settings - Module routes
            subclass(SettingsRoutes.MQTT::class, SettingsRoutes.MQTT.serializer())
            subclass(SettingsRoutes.Serial::class, SettingsRoutes.Serial.serializer())
            subclass(SettingsRoutes.ExtNotification::class, SettingsRoutes.ExtNotification.serializer())
            subclass(SettingsRoutes.StoreForward::class, SettingsRoutes.StoreForward.serializer())
            subclass(SettingsRoutes.RangeTest::class, SettingsRoutes.RangeTest.serializer())
            subclass(SettingsRoutes.Telemetry::class, SettingsRoutes.Telemetry.serializer())
            subclass(SettingsRoutes.CannedMessage::class, SettingsRoutes.CannedMessage.serializer())
            subclass(SettingsRoutes.Audio::class, SettingsRoutes.Audio.serializer())
            subclass(SettingsRoutes.RemoteHardware::class, SettingsRoutes.RemoteHardware.serializer())
            subclass(SettingsRoutes.NeighborInfo::class, SettingsRoutes.NeighborInfo.serializer())
            subclass(SettingsRoutes.AmbientLighting::class, SettingsRoutes.AmbientLighting.serializer())
            subclass(SettingsRoutes.DetectionSensor::class, SettingsRoutes.DetectionSensor.serializer())
            subclass(SettingsRoutes.Paxcounter::class, SettingsRoutes.Paxcounter.serializer())
            subclass(SettingsRoutes.StatusMessage::class, SettingsRoutes.StatusMessage.serializer())
            subclass(SettingsRoutes.TrafficManagement::class, SettingsRoutes.TrafficManagement.serializer())
            subclass(SettingsRoutes.TAK::class, SettingsRoutes.TAK.serializer())
            // Settings - Advanced routes
            subclass(SettingsRoutes.CleanNodeDb::class, SettingsRoutes.CleanNodeDb.serializer())
            subclass(SettingsRoutes.DebugPanel::class, SettingsRoutes.DebugPanel.serializer())
            subclass(SettingsRoutes.About::class, SettingsRoutes.About.serializer())
            subclass(SettingsRoutes.FilterSettings::class, SettingsRoutes.FilterSettings.serializer())
            // Channels
            subclass(ChannelsRoutes.ChannelsGraph::class, ChannelsRoutes.ChannelsGraph.serializer())
            subclass(ChannelsRoutes.Channels::class, ChannelsRoutes.Channels.serializer())
            // Connections
            subclass(ConnectionsRoutes.ConnectionsGraph::class, ConnectionsRoutes.ConnectionsGraph.serializer())
            subclass(ConnectionsRoutes.Connections::class, ConnectionsRoutes.Connections.serializer())
        }
    }
}

/**
 * Desktop main screen — Navigation 3 shell with a persistent [NavigationRail] and [NavDisplay].
 *
 * Uses the same shared routes from `core:navigation` and the same `NavDisplay` + `entryProvider` pattern as the Android
 * app, proving the shared backstack architecture works across targets.
 */
@Composable
fun DesktopMainScreen(radioService: RadioInterfaceService = koinInject()) {
    val backStack = rememberNavBackStack(navSavedStateConfig, NodesRoutes.NodesGraph as NavKey)
    val currentKey = backStack.lastOrNull()
    val selected = TopLevelDestination.fromNavKey(currentKey)

    val connectionState by radioService.connectionState.collectAsStateWithLifecycle()
    val selectedDevice by radioService.currentDeviceAddressFlow.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = destination == selected,
                        onClick = {
                            if (destination != selected) {
                                backStack.clear()
                                backStack.add(destination.route)
                            }
                        },
                        icon = {
                            if (destination == TopLevelDestination.Connections) {
                                org.meshtastic.feature.connections.ui.components.AnimatedConnectionsNavIcon(
                                    connectionState = connectionState,
                                    deviceType = DeviceType.fromAddress(selectedDevice ?: "NoDevice"),
                                    meshActivityFlow = radioService.meshActivity,
                                    colorScheme = colorScheme,
                                )
                            } else {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.label),
                                )
                            }
                        },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }

            val provider = entryProvider<NavKey> { desktopNavGraph(backStack) }

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = provider,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }
}
