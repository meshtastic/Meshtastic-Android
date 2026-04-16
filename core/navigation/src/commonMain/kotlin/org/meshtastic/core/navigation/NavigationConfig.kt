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
package org.meshtastic.core.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * Shared polymorphic serialization configuration for Navigation 3 saved-state support. Registers all route types used
 * across Android and Desktop navigation graphs.
 */
val MeshtasticNavSavedStateConfig = SavedStateConfiguration {
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
            subclass(SettingsRoutes.TakServer::class, SettingsRoutes.TakServer.serializer())
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
