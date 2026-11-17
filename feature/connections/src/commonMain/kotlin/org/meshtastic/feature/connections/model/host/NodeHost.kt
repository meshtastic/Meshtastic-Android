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
package org.meshtastic.feature.connections.model.host

import org.meshtastic.proto.Config

/**
 * A node-kmp host's vocabulary as the node card reads it, by the names node-kmp gives it, so the card builds without
 * node-kmp and an in-process node and a remote host map onto the same types.
 */
enum class NodePhase {
    Starting,
    Running,
    Rebooting,
    Failed,
    Stopped,
}

/** A medium a node-kmp node carries the mesh over, by its transport name in a host's status. */
enum class Bearer(val transportName: String) {
    UDP("udp"),
    BLE_ADV("ble-adv"),
    GATT("gatt"),
    LORA("lora"),
    MQTT("mqtt"),
    WIFI_AWARE("wifi-aware"),
    AUDIO("audio"),
    VIDEO("video"),
    ;

    /** MQTT follows its module config; every other bearer is an entry in the stored transport section. */
    val switchable: Boolean
        get() = this != MQTT
}

/** What kind of radio a host's LoRa radio is. */
enum class ExtraRadioDevice {
    RTL_SDR,
    USB_STICK,
    BOARD,
}

/** What a host says one of its extra LoRa radios is. */
data class ExtraRadio(
    val device: ExtraRadioDevice,
    val id: String? = null,
    val preset: Config.LoRaConfig.ModemPreset? = null,
    val bridge: Boolean = false,
    val duplex: Boolean = false,
)

/** The host's first LoRa radio: a USB stick or a board, by the [id] the host named it with, if any. */
data class PrimaryRadio(val device: ExtraRadioDevice, val id: String? = null)

/** The telemetry a sensor's readings feed. */
enum class SensorKind {
    ENVIRONMENT,
    AIR_QUALITY,
    POWER,
}

/** Why a device on the host's I2C bus answered and is not read. */
enum class SensorUnread {
    UNIDENTIFIED,
    NO_DRIVER,
    INIT_FAILED,
    OTHER_ADDRESS,
}
