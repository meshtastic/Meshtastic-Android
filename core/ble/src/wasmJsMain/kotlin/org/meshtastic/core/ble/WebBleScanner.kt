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
@file:Suppress("UnusedParameter")

package org.meshtastic.core.ble

import co.touchlab.kermit.Logger
import com.juul.kable.ExperimentalKableApi
import com.juul.kable.Options
import com.juul.kable.Peripheral
import com.juul.kable.requestPeripheral
import com.juul.kable.toIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.awaitOrThrowException
import org.meshtastic.core.model.util.anonymize
import kotlin.js.Promise
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
 * completes empty. Like a scan on the other platforms it first looks for the device advertising: Chrome drops a picked
 * device from its adapter once the chooser's scan ends, and `gatt.connect()` then fails at once with "Bluetooth Device
 * is no longer in range", so the scan watches the device's advertisements, which puts it back. [timeout] is not
 * applied: the chooser cannot be closed from script.
 */
@Single(binds = [BleScanner::class])
class WebBleScanner(private val loggingConfig: BleLoggingConfig) : BleScanner {
    override val picksDevice: Boolean = true

    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> = flow {
        val peripheral = if (address != null) granted(address) else pick(serviceUuid)
        if (peripheral != null) emit(peripheral.toBleDevice())
    }

    private suspend fun pick(serviceUuid: Uuid?): Peripheral? {
        // An unfiltered chooser would list every nearby BLE device and grant access to none of their services, and
        // Kable dereferences navigator.bluetooth without a check, so a browser without it would throw.
        if (serviceUuid == null || !hasWebBluetooth()) {
            Logger.w {
                if (serviceUuid == null) {
                    "Web Bluetooth scan needs a service UUID filter; completing empty"
                } else {
                    "This browser has no Web Bluetooth; completing empty"
                }
            }
            return null
        }
        val options = Options { filters { match { services = listOf(serviceUuid) } } }
        // The handler fires only after a connection subscribes, long after the pick has named the device.
        var picked: String? = null
        return try {
            asException {
                requestPeripheral(options) {
                    meshtasticConfig(loggingConfig, identifier = null) { cause ->
                        picked?.let { WebBluetoothGrants.recordObservationFailure(it, cause) }
                    }
                }
            }
                ?.also { picked = it.identifier }
                ?.let(WebBluetoothGrants::remember)
        } catch (e: IllegalStateException) {
            // No Web Bluetooth here, or no user gesture / insecure context: Kable says which in the message.
            Logger.w(e) { "Web Bluetooth device request refused" }
            null
        }
    }

    private suspend fun granted(address: String): Peripheral? {
        val peripheral = WebBluetoothGrants.find(address) ?: restoreGrant(address)?.let(WebBluetoothGrants::remember)
        if (peripheral != null) {
            // Without the new permissions backend there is no watchAdvertisements; the connect then tries as it is.
            try {
                watchAdvertisementsOf(address, ADVERTISEMENT_WAIT_MS, WATCH_WINDOW_MS).awaitOrThrowException()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.d { "[${address.anonymize()}] watchAdvertisements unavailable: ${e.message}" }
            }
        }
        return peripheral
    }

    private suspend fun restoreGrant(address: String): Peripheral? {
        if (!hasGetDevices()) return null
        return try {
            asException {
                Peripheral(address.toIdentifier()) {
                    meshtasticConfig(loggingConfig, address.anonymize()) { cause ->
                        WebBluetoothGrants.recordObservationFailure(address, cause)
                    }
                }
            }
        } catch (e: IllegalStateException) {
            Logger.w(e) { "[${address.anonymize()}] Web Bluetooth getDevices() refused" }
            null
        }
    }
}

@OptIn(ExperimentalKableApi::class)
private fun Peripheral.toBleDevice(): BleDevice =
    MeshtasticBleDevice(address = identifier, name = name, isBonded = false)

private const val ADVERTISEMENT_WAIT_MS = 3_000
private const val WATCH_WINDOW_MS = 10_000

/**
 * Watches the granted device [id]'s advertisements for [windowMs], resolving on the first one or after [waitMs],
 * whichever comes first. A device that is not granted or a browser without the API resolves at once.
 */
private fun watchAdvertisementsOf(id: String, waitMs: Int, windowMs: Int): Promise<JsAny?> = js(
    """(async () => {
        if (!navigator.bluetooth || typeof navigator.bluetooth.getDevices !== 'function') return null;
        const device = (await navigator.bluetooth.getDevices()).find((d) => d.id === id);
        if (!device || typeof device.watchAdvertisements !== 'function') return null;
        const abort = new AbortController();
        const seen = new Promise((resolve) => {
            device.addEventListener('advertisementreceived', () => resolve(null), { once: true, signal: abort.signal });
            setTimeout(() => resolve(null), waitMs);
        });
        await device.watchAdvertisements({ signal: abort.signal });
        setTimeout(() => abort.abort(), windowMs);
        await seen;
        return null;
    })()""",
)

private fun hasWebBluetooth(): Boolean = js("typeof navigator !== 'undefined' && !!navigator.bluetooth")

// Kable turns a missing getDevices() into an InternalError; Chrome exposes it only with its new permissions backend.
private fun hasGetDevices(): Boolean = js(
    "typeof navigator !== 'undefined' && !!navigator.bluetooth && typeof navigator.bluetooth.getDevices === 'function'",
)
