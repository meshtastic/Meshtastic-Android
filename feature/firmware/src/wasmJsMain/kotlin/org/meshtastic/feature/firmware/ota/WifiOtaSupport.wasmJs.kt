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

// The web app offers no TCP radios, so FirmwareUpdateMethod.Wifi is never chosen there.
internal actual fun createWifiOtaTransport(deviceIp: String): UnifiedOtaProtocol =
    throw UnsupportedOperationException("A browser cannot open the TCP socket Wi-Fi OTA needs")

internal actual suspend fun discoverWifiOtaDeviceOnLan(): String? = null
