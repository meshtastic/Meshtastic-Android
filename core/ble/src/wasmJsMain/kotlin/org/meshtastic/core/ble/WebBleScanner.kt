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
import com.juul.kable.ExperimentalKableApi
import com.juul.kable.Options
import com.juul.kable.Peripheral
import com.juul.kable.requestPeripheral
import com.juul.kable.toIdentifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Single
import org.meshtastic.core.model.util.anonymize
import kotlin.time.Duration
import kotlin.uuid.Uuid

/**
 * [BleScanner] over Kable's Web Bluetooth `requestPeripheral` picker.
 *
 * Kable's web `Scanner` needs `requestLEScan`, which Chrome ships only behind a flag, so discovery here is the
 * browser's device chooser: one-shot, gated on a user gesture, and limited to devices offering [serviceUuid]. A scan
 * with no [address] opens the chooser and emits at most the one device picked, then completes; dismissing it completes
 * empty. Kable calls `requestDevice()` before its first suspension, so the gesture that started the scan still counts.
 *
 * A scan for an [address] never opens the chooser, since reconnects run without a gesture. It emits the peripheral the
 * page was already granted, from [WebBluetoothGrants] or `getDevices()` where the browser has it, and otherwise
 * completes empty. [timeout] is not applied: the chooser cannot be closed from script.
 */
@Single(binds = [BleScanner::class])
class WebBleScanner(private val loggingConfig: BleLoggingConfig) : BleScanner {
    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> = flow {
        val peripheral = if (address != null) granted(address) else pick(serviceUuid)
        if (peripheral != null) emit(peripheral.toBleDevice())
    }

    private suspend fun pick(serviceUuid: Uuid?): Peripheral? {
        if (serviceUuid == null) {
            // An unfiltered chooser would list every nearby BLE device and grant access to none of their services.
            Logger.w { "Web Bluetooth scan needs a service UUID filter; completing empty" }
            return null
        }
        val options = Options { filters { match { services = listOf(serviceUuid) } } }
        return try {
            requestPeripheral(options) { meshtasticConfig(loggingConfig, identifier = null) }
                ?.let(WebBluetoothGrants::remember)
        } catch (e: IllegalStateException) {
            // No Web Bluetooth here, or no user gesture / insecure context: Kable says which in the message.
            Logger.w(e) { "Web Bluetooth device request refused" }
            null
        }
    }

    private suspend fun granted(address: String): Peripheral? =
        WebBluetoothGrants.find(address) ?: restoreGrant(address)?.let(WebBluetoothGrants::remember)

    private suspend fun restoreGrant(address: String): Peripheral? {
        if (!hasGetDevices()) return null
        return try {
            Peripheral(address.toIdentifier()) { meshtasticConfig(loggingConfig, address.anonymize()) }
        } catch (e: IllegalStateException) {
            Logger.w(e) { "[${address.anonymize()}] Web Bluetooth getDevices() refused" }
            null
        }
    }
}

@OptIn(ExperimentalKableApi::class)
private fun Peripheral.toBleDevice(): BleDevice =
    MeshtasticBleDevice(address = identifier, name = name, isBonded = false)

// Kable turns a missing getDevices() into an InternalError; Chrome exposes it only with its new permissions backend.
private fun hasGetDevices(): Boolean = js(
    "typeof navigator !== 'undefined' && !!navigator.bluetooth && typeof navigator.bluetooth.getDevices === 'function'",
)
