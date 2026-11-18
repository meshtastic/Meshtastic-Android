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
package org.meshtastic.feature.connections.model

import org.meshtastic.feature.connections.model.host.Bearer
import org.meshtastic.feature.connections.model.host.ExtraRadioDevice
import org.meshtastic.feature.connections.model.host.NodePhase
import org.meshtastic.feature.connections.model.host.SensorKind
import org.meshtastic.feature.connections.model.host.SensorUnread
import org.meshtastic.node.host.ExtraRadioDevice as NodeExtraRadioDevice
import org.meshtastic.node.host.NodePhase as NodeNodePhase
import org.meshtastic.node.sensors.SensorKind as NodeSensorKind
import org.meshtastic.node.sensors.SensorUnread as NodeSensorUnread
import org.meshtastic.node.transport.Bearer as NodeBearer

// The in-process node's seam, with InProcessNode.kt: each node-kmp enum and its app-side twin, both ways, with no
// `else`, so a case added on either side fails to compile until it is mapped.

internal fun NodeNodePhase.toApp(): NodePhase = when (this) {
    NodeNodePhase.Starting -> NodePhase.Starting
    NodeNodePhase.Running -> NodePhase.Running
    NodeNodePhase.Rebooting -> NodePhase.Rebooting
    NodeNodePhase.Failed -> NodePhase.Failed
    NodeNodePhase.Stopped -> NodePhase.Stopped
}

internal fun NodePhase.toNode(): NodeNodePhase = when (this) {
    NodePhase.Starting -> NodeNodePhase.Starting
    NodePhase.Running -> NodeNodePhase.Running
    NodePhase.Rebooting -> NodeNodePhase.Rebooting
    NodePhase.Failed -> NodeNodePhase.Failed
    NodePhase.Stopped -> NodeNodePhase.Stopped
}

internal fun NodeBearer.toApp(): Bearer = when (this) {
    NodeBearer.UDP -> Bearer.UDP
    NodeBearer.BLE_ADV -> Bearer.BLE_ADV
    NodeBearer.GATT -> Bearer.GATT
    NodeBearer.LORA -> Bearer.LORA
    NodeBearer.MQTT -> Bearer.MQTT
    NodeBearer.WIFI_AWARE -> Bearer.WIFI_AWARE
    NodeBearer.AUDIO -> Bearer.AUDIO
    NodeBearer.VIDEO -> Bearer.VIDEO
}

/** The bearer a card switch writes to the node. */
internal fun Bearer.toNode(): NodeBearer = when (this) {
    Bearer.UDP -> NodeBearer.UDP
    Bearer.BLE_ADV -> NodeBearer.BLE_ADV
    Bearer.GATT -> NodeBearer.GATT
    Bearer.LORA -> NodeBearer.LORA
    Bearer.MQTT -> NodeBearer.MQTT
    Bearer.WIFI_AWARE -> NodeBearer.WIFI_AWARE
    Bearer.AUDIO -> NodeBearer.AUDIO
    Bearer.VIDEO -> NodeBearer.VIDEO
}

internal fun NodeExtraRadioDevice.toApp(): ExtraRadioDevice = when (this) {
    NodeExtraRadioDevice.RTL_SDR -> ExtraRadioDevice.RTL_SDR
    NodeExtraRadioDevice.USB_STICK -> ExtraRadioDevice.USB_STICK
    NodeExtraRadioDevice.BOARD -> ExtraRadioDevice.BOARD
}

internal fun ExtraRadioDevice.toNode(): NodeExtraRadioDevice = when (this) {
    ExtraRadioDevice.RTL_SDR -> NodeExtraRadioDevice.RTL_SDR
    ExtraRadioDevice.USB_STICK -> NodeExtraRadioDevice.USB_STICK
    ExtraRadioDevice.BOARD -> NodeExtraRadioDevice.BOARD
}

internal fun NodeSensorKind.toApp(): SensorKind = when (this) {
    NodeSensorKind.ENVIRONMENT -> SensorKind.ENVIRONMENT
    NodeSensorKind.AIR_QUALITY -> SensorKind.AIR_QUALITY
    NodeSensorKind.POWER -> SensorKind.POWER
}

internal fun SensorKind.toNode(): NodeSensorKind = when (this) {
    SensorKind.ENVIRONMENT -> NodeSensorKind.ENVIRONMENT
    SensorKind.AIR_QUALITY -> NodeSensorKind.AIR_QUALITY
    SensorKind.POWER -> NodeSensorKind.POWER
}

internal fun NodeSensorUnread.toApp(): SensorUnread = when (this) {
    NodeSensorUnread.UNIDENTIFIED -> SensorUnread.UNIDENTIFIED
    NodeSensorUnread.NO_DRIVER -> SensorUnread.NO_DRIVER
    NodeSensorUnread.INIT_FAILED -> SensorUnread.INIT_FAILED
    NodeSensorUnread.OTHER_ADDRESS -> SensorUnread.OTHER_ADDRESS
}

internal fun SensorUnread.toNode(): NodeSensorUnread = when (this) {
    SensorUnread.UNIDENTIFIED -> NodeSensorUnread.UNIDENTIFIED
    SensorUnread.NO_DRIVER -> NodeSensorUnread.NO_DRIVER
    SensorUnread.INIT_FAILED -> NodeSensorUnread.INIT_FAILED
    SensorUnread.OTHER_ADDRESS -> NodeSensorUnread.OTHER_ADDRESS
}
