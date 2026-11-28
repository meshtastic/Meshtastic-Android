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
package org.meshtastic.core.ble

import kotlin.uuid.Uuid

/** Constants for Meshtastic Bluetooth LE interaction. */
object MeshtasticBleConstants {
    /** Pattern for Meshtastic device names (e.g., Meshtastic_1234). */
    const val BLE_NAME_PATTERN = "^.*_([0-9a-fA-F]{4})$"

    /** The Meshtastic service UUID. */
    val SERVICE_UUID: Uuid = Uuid.parse("6ba1b218-15a8-461f-9fa8-5dcae273eafd")

    // --- OTA Characteristics ---

    /** The Meshtastic OTA service UUID (ESP32 Unified OTA). */
    val OTA_SERVICE_UUID: Uuid = Uuid.parse("4FAFC201-1FB5-459E-8FCC-C5C9C331914B")

    /** Characteristic for writing OTA commands and firmware data. */
    val OTA_WRITE_CHARACTERISTIC: Uuid = Uuid.parse("62ec0272-3ec5-11eb-b378-0242ac130005")

    /** Characteristic for receiving OTA status notifications/ACKs. */
    val OTA_NOTIFY_CHARACTERISTIC: Uuid = Uuid.parse("62ec0272-3ec5-11eb-b378-0242ac130003")
}
