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
 * Maps an address id to the [JsSerialPort] instance Web Serial granted for it.
 *
 * Web Serial ports carry no identifier string the way a Web Bluetooth device's `id` does; within a page session
 * `getPorts()` returns the same object for a port (https://wicg.github.io/serial/#getports-method), so object identity
 * is the handle. The id is `<vid>:<pid>-<ordinal>`, USB IDs in hex and the ordinal counting granted ports with that USB
 * ID in `getPorts()` order, so a saved address names the same radio after a reload instead of whichever port was seen
 * first. A port keeps its id for the session once seen, and a replugged one takes its old id back through [rebind].
 *
 * [requestNewPort] shows the browser's device picker and **requires an active user gesture**: it must be reached from a
 * coroutine started directly inside a click (or similar) event handler, with no earlier suspension — Chrome enforces
 * "transient activation" the same way it does for Web Bluetooth's `requestDevice()`.
 */
@Single
class WebSerialPortRegistry {
    private val portsById = mutableMapOf<String, JsSerialPort>()

    /**
     * The id [port] already has, or a new one at [ordinal] among its USB ID. An ordinal another port holds this session
     * (one unplugged and not yet replugged) moves the new port to the next free one.
     */
    private fun idFor(port: JsSerialPort, ordinal: Int): String {
        portsById.entries
            .firstOrNull { it.value === port }
            ?.let {
                return it.key
            }
        val prefix = serialPortIdPrefix(port.vendorId(), port.productId())
        val id = generateSequence(ordinal) { it + 1 }.map { "$prefix-$it" }.first { it !in portsById }
        portsById[id] = port
        return id
    }

    /**
     * The port registered under [id], or `null` if unknown.
     *
     * `internal`, unlike [refreshGrantedPorts]/[requestNewPort]: [JsSerialPort] is `core:network`-only JS interop, so
     * only a same-module transport (e.g. `WasmJsSerialTransport`) can use the raw handle this returns. Cross-module
     * callers (e.g. `feature:connections`'s USB scanner) get everything they need from [GrantedSerialPort] instead.
     */
    internal fun get(id: String): JsSerialPort? = portsById[id]

    /** [get], re-reading the granted ports first when [id] is unknown, as it is for a saved address after a reload. */
    internal suspend fun find(id: String): JsSerialPort? = get(id)
        ?: run {
            refreshGrantedPorts()
            get(id)
        }

    /** Every id minted this session, with the port it currently stands for. */
    internal fun knownPorts(): Map<String, JsSerialPort> = portsById.toMap()

    /**
     * Points [id] at [port]. A replugged USB device comes back from Web Serial as a new object, so this keeps the
     * address the transport was started with valid for it.
     */
    internal fun rebind(id: String, port: JsSerialPort) {
        portsById[id] = port
    }

    /** Ports already granted to this page, gesture-free. Empty if Web Serial is unavailable in this browser/context. */
    suspend fun refreshGrantedPorts(): List<GrantedSerialPort> {
        val serial = webSerialOrNull() ?: return emptyList()
        val seen = mutableMapOf<Pair<Int, Int>, Int>()
        return serial.getPortsList().map { port ->
            val usbId = port.vendorId() to port.productId()
            val ordinal = seen.getOrElse(usbId) { 0 }
            seen[usbId] = ordinal + 1
            GrantedSerialPort(idFor(port, ordinal), usbId.first, usbId.second)
        }
    }

    /**
     * Shows the browser's serial-port picker and returns the id for the device the user chose, or `null` if they
     * dismissed it or Web Serial is unavailable. See class KDoc for the user-gesture requirement.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun requestNewPort(): String? {
        val serial = webSerialOrNull() ?: return null
        return try {
            val picked = serial.requestPortSuspend()
            refreshGrantedPorts()
            idFor(picked, ordinal = 0)
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

private const val HEX_RADIX = 16
private const val USB_ID_DIGITS = 4

/** `239a:8029`: the USB vendor and product IDs as lsusb prints them; `0000:0000` for a port with no USB ID. */
internal fun serialPortIdPrefix(vendorId: Int, productId: Int): String =
    "${vendorId.usbIdHex()}:${productId.usbIdHex()}"

private fun Int.usbIdHex(): String = toString(HEX_RADIX).padStart(USB_ID_DIGITS, '0')
