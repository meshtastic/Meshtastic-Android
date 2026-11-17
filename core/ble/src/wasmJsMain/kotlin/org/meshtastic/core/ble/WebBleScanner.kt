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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * [BleScanner] implementation backed by `navigator.bluetooth.requestDevice()`.
 *
 * **This is a deliberately narrower contract than the Kable-based scanners on Android/JVM/iOS.** Web Bluetooth's
 * `requestDevice()` shows the browser's native device-chooser UI and is a one-shot, user-gesture-gated *picker* — it is
 * not a continuous background scan. [scan] therefore emits **at most one** [BleDevice] (the device the user picked),
 * then completes — it does not stream every matching advertisement for the duration of [timeout] the way
 * `KableBleScanner.scan` does. Callers written against "many devices may arrive over time" will only ever see one
 * result here. If the user dismisses the picker without choosing a device, or no matching device exists, [scan]
 * completes empty rather than throwing — that mirrors a scan that simply found nothing.
 *
 * [address] filtering is not supported: the picker's device list is entirely user-driven, so there is no way to
 * pre-filter to a known address before the user makes a choice. A non-null [serviceUuid] is required — Web Bluetooth
 * has no unfiltered `requestDevice()` call compatible with this API without opting into `acceptAllDevices`, which this
 * scanner deliberately does not do (it would defeat the point of a Meshtastic-service filter and expose every nearby
 * BLE device in the picker).
 */
@Single(binds = [BleScanner::class])
class WebBleScanner : BleScanner {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> = flow {
        val bluetooth =
            webBluetoothOrNull()
                ?: run {
                    Logger.w { "Web Bluetooth is unavailable in this browser/context" }
                    return@flow
                }
        if (serviceUuid == null) {
            Logger.w { "WebBleScanner.scan requires a serviceUuid filter; completing empty" }
            return@flow
        }
        try {
            val jsDevice = bluetooth.requestDeviceForService(serviceUuid.toString())
            emit(WebBleDevice(jsDevice))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The user dismissed the picker, or no matching device was found — Web Bluetooth surfaces both as a
            // rejected promise with no further detail to distinguish them by. Complete empty rather than
            // propagating a generic rejection as a scan failure.
            Logger.d(e) { "requestDevice() did not yield a device" }
        }
    }
}
