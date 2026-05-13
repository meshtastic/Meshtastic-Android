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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val DEFAULT_SCAN_RETRY_COUNT = 3
internal val DEFAULT_SCAN_RETRY_DELAY: Duration = 2.seconds
internal val DEFAULT_SCAN_TIMEOUT: Duration = 10.seconds

private const val MAC_PARTS_COUNT = 6
private const val HEX_RADIX = 16
private const val BYTE_MASK = 0xFF

/**
 * Increment the last byte of a BLE MAC address by one.
 *
 * Both ESP32 (OTA) and nRF52 (DFU) devices advertise with the original MAC + 1 after rebooting into their respective
 * firmware-update modes.
 */
@Suppress("ReturnCount")
internal fun calculateMacPlusOne(macAddress: String): String {
    val parts = macAddress.split(":")
    if (parts.size != MAC_PARTS_COUNT) return macAddress
    val lastByte = parts[MAC_PARTS_COUNT - 1].toIntOrNull(HEX_RADIX) ?: return macAddress
    val incremented = ((lastByte + 1) and BYTE_MASK).toString(HEX_RADIX).uppercase().padStart(2, '0')
    return "${parts.take(MAC_PARTS_COUNT - 1).joinToString(":")}:$incremented"
}

/**
 * Scan for a BLE device matching [predicate] with retry logic.
 *
 * Shared by both [BleOtaTransport] and
 * [SecureDfuTransport][org.meshtastic.feature.firmware.ota.dfu.SecureDfuTransport].
 */
internal suspend fun scanForBleDevice(
    scanner: BleScanner,
    tag: String,
    serviceUuid: kotlin.uuid.Uuid,
    retryCount: Int = DEFAULT_SCAN_RETRY_COUNT,
    retryDelay: Duration = DEFAULT_SCAN_RETRY_DELAY,
    scanTimeout: Duration = DEFAULT_SCAN_TIMEOUT,
    predicate: (BleDevice) -> Boolean,
): BleDevice? {
    repeat(retryCount) { attempt ->
        Logger.d { "$tag: Scan attempt ${attempt + 1}/$retryCount" }
        val foundDevices = mutableSetOf<String>()
        val device =
            scanner
                .scan(timeout = scanTimeout, serviceUuid = serviceUuid)
                .onEach { d ->
                    if (foundDevices.add(d.address)) {
                        Logger.d { "$tag: Scan found device: ${d.address} (name=${d.name})" }
                    }
                }
                .firstOrNull(predicate)
        if (device != null) {
            Logger.i { "$tag: Found target device at ${device.address}" }
            return device
        }
        Logger.w { "$tag: Target not in ${foundDevices.size} devices found" }
        if (attempt < retryCount - 1) delay(retryDelay)
    }
    return null
}
