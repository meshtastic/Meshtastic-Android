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
package org.meshtastic.feature.firmware

/**
 * Whether the nRF52 DFU and ESP32 BLE OTA flows can run here. False in a browser: Web Bluetooth names a device by an
 * opaque id rather than its MAC address, grants only the services named at the pick, and the radio's updater comes back
 * as a new device the chooser must be clicked again to reach.
 */
internal expect val bluetoothFirmwareUpdateSupported: Boolean
