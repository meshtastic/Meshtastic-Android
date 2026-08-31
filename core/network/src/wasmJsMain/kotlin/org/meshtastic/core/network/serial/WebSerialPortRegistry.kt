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
package org.meshtastic.core.network.serial

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Single

/**
 * Everything a caller outside `core:network` needs about a granted port — deliberately not the raw [JsSerialPort]
 * itself, which is `internal` to this module.
 *
 * [vendorId]/[productId] are `0` when unavailable (e.g. a non-USB serial backend) — the same "0 means unknown" contract
 * [JsSerialPort.vendorId]/[JsSerialPort.productId] use.
 */
data class GrantedSerialPort(val id: String, val vendorId: Int, val productId: Int)

/**
 * Session-scoped cache mapping a synthetic address id to the [JsSerialPort] instance Web Serial granted for it.
 *
 * Web Serial ports carry no persistent identifier string the way a Web Bluetooth device's `id` does — [getPorts]
 * returns the *same* `JsSerialPort` object instance across calls within a page session (the spec guarantees this,
 * https://wicg.github.io/serial/#getports-method), so object identity (`===`) is the only stable handle available. This
 * registry mints and remembers a small opaque id (`webserial-N`) the first time a port is observed, and does not
 * survive a page reload — matching Web Serial's own grant model, where [getPortsList] itself is what re-establishes
 * which ports are still authorized after a reload, not this registry's ids.
 *
 * [requestNewPort] shows the browser's device picker and **requires an active user gesture**: it must be reached from a
 * coroutine started directly inside a click (or similar) event handler, with no earlier suspension — Chrome enforces
 * "transient activation" the same way it does for Web Bluetooth's `requestDevice()` (see
 * `WebBleScanner`/`WebBluetoothRepository` in `core:ble` for the equivalent BLE story, including a compiler-crash
 * caveat found there that does not apply here: this class has no relationship to `BluetoothRepository.bondedDevices` or
 * `BleDeviceLocator`, so it is not subject to that bug).
 */
@Single
class WebSerialPortRegistry {
    private val portsById = mutableMapOf<String, JsSerialPort>()
    private var nextId = 0

    /** Returns the existing id for [port] if already known, or mints and remembers a new one. */
    private fun idFor(port: JsSerialPort): String {
        portsById.entries
            .firstOrNull { it.value === port }
            ?.let {
                return it.key
            }
        val id = "webserial-${nextId++}"
        portsById[id] = port
        return id
    }

    /**
     * The port previously registered under [id], or `null` if unknown (e.g. after a page reload).
     *
     * `internal`, unlike [refreshGrantedPorts]/[requestNewPort]: [JsSerialPort] is `core:network`-only JS interop, so
     * only a same-module transport (e.g. `WasmJsSerialTransport`) can use the raw handle this returns. Cross-module
     * callers (e.g. `feature:connections`'s USB scanner) get everything they need from [GrantedSerialPort] instead.
     */
    internal fun get(id: String): JsSerialPort? = portsById[id]

    /** Ports already granted to this page, gesture-free. Empty if Web Serial is unavailable in this browser/context. */
    suspend fun refreshGrantedPorts(): List<GrantedSerialPort> {
        val serial = webSerialOrNull() ?: return emptyList()
        return serial.getPortsList().map { port -> GrantedSerialPort(idFor(port), port.vendorId(), port.productId()) }
    }

    /**
     * Shows the browser's serial-port picker and returns the id for the device the user chose, or `null` if they
     * dismissed it or Web Serial is unavailable. See class KDoc for the user-gesture requirement.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun requestNewPort(): String? {
        val serial = webSerialOrNull() ?: return null
        return try {
            idFor(serial.requestPortSuspend())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The user dismissed the picker, or the browser refused it outside an active gesture — surfaces as a
            // rejected promise with no further detail. Complete empty rather than propagating a generic rejection.
            Logger.d(e) { "requestPort() did not yield a device" }
            null
        }
    }
}
