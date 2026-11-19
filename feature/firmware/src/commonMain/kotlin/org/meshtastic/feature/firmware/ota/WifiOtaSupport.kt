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
package org.meshtastic.feature.firmware.ota

/** Bytes per write the Wi-Fi OTA transport streams; larger than BLE's. */
internal const val WIFI_OTA_CHUNK_SIZE = 1024

/** The Unified OTA transport over TCP to [deviceIp]. A browser opens no raw sockets, so there it throws. */
internal expect fun createWifiOtaTransport(deviceIp: String): UnifiedOtaProtocol

/** The OTA loader's address from its UDP discovery broadcast, or null on timeout or where UDP is unavailable. */
internal expect suspend fun discoverWifiOtaDeviceOnLan(): String?
