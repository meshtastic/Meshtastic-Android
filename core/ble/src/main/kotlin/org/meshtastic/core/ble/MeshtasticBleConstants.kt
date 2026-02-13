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
package org.meshtastic.core.ble

import kotlin.uuid.Uuid

/** Constants for Meshtastic Bluetooth LE interaction. */
object MeshtasticBleConstants {
    /** Pattern for Meshtastic device names (e.g., Meshtastic_1234). */
    const val BLE_NAME_PATTERN = "^.*_([0-9a-fA-F]{4})$"

    /** The Meshtastic service UUID. */
    val SERVICE_UUID: Uuid = Uuid.parse("6ba1b218-15a8-461f-9fa8-5dcae273eafd")

    /** Characteristic for sending data to the radio. */
    val TORADIO_CHARACTERISTIC: Uuid = Uuid.parse("f75c76d2-129e-4dad-a1dd-7866124401e7")

    /** Characteristic for receiving packet count notifications. */
    val FROMNUM_CHARACTERISTIC: Uuid = Uuid.parse("ed9da18c-a800-4f66-a670-aa7547e34453")

    /** Characteristic for reading data from the radio. */
    val FROMRADIO_CHARACTERISTIC: Uuid = Uuid.parse("2c55e69e-4993-11ed-b878-0242ac120002")

    /** Characteristic for receiving log notifications from the radio. */
    val LOGRADIO_CHARACTERISTIC: Uuid = Uuid.parse("5a3d6e49-06e6-4423-9944-e9de8cdf9547")
}
