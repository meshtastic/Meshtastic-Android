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

sealed interface Route {
    @Serializable data object Contacts : Route
    @Serializable data object Nodes : Route
    @Serializable data object Map : Route
    @Serializable data object Channels : Route
    @Serializable data object Settings : Route

    @Serializable data object DebugPanel : Route
    @Serializable
    data class Messages(val contactKey: String, val message: String = "") : Route
    @Serializable data object QuickChat : Route
    @Serializable
    data class Share(val message: String) : Route

    @Serializable
    data class RadioConfig(val destNum: Int? = null) : Route
    @Serializable data object User : Route
    @Serializable data object ChannelConfig : Route
    @Serializable data object Device : Route
    @Serializable data object Position : Route
    @Serializable data object Power : Route
    @Serializable data object Network : Route
    @Serializable data object Display : Route
    @Serializable data object LoRa : Route
    @Serializable data object Bluetooth : Route
    @Serializable data object Security : Route

    @Serializable data object MQTT : Route
    @Serializable data object Serial : Route
    @Serializable data object ExtNotification : Route
    @Serializable data object StoreForward : Route
    @Serializable data object RangeTest : Route
    @Serializable data object Telemetry : Route
    @Serializable data object CannedMessage : Route
    @Serializable data object Audio : Route
    @Serializable data object RemoteHardware : Route
    @Serializable data object NeighborInfo : Route
    @Serializable data object AmbientLighting : Route
    @Serializable data object DetectionSensor : Route
    @Serializable data object Paxcounter : Route

    @Serializable data class NodeDetail(val destNum: Int) : Route
    @Serializable data object DeviceMetrics : Route
    @Serializable data object NodeMap : Route
    @Serializable data object PositionLog : Route
    @Serializable data object EnvironmentMetrics : Route
    @Serializable data object SignalMetrics : Route
    @Serializable data object TracerouteLog : Route
}
