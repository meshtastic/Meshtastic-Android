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
@file:Suppress(
    // Same rationale as `core:ble`'s `WebBluetoothApi.kt`: the whole Web Serial surface this file wraps genuinely
    // needs this many declarations in one place.
    "TooManyFunctions",
    // Every js("...") snippet below references its Kotlin parameter INSIDE the JS source string, which detekt's
    // static analysis can't see — a real false positive, not a real gap. Same as `WebBluetoothApi.kt`.
    "UnusedParameter",
)

package org.meshtastic.core.network.serial

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.js.Promise

/**
 * Raw Web Serial JS interop (https://wicg.github.io/serial/), isolated to this one file the same way `core:ble`'s
 * `WebBluetoothApi.kt` isolates Web Bluetooth — everything above this layer talks only to the small Kotlin-shaped API
 * declared here, never to `external`/`js()` directly.
 */
internal external interface JsNavigatorSerial : JsAny {
    val serial: JsSerial?
}

internal external interface JsSerial : JsAny {
    /** Ports already granted to this page in a previous session — no user gesture required. */
    fun getPorts(): Promise<JsAny>

    /**
     * Shows the browser's serial-port picker. Requires an active user gesture, exactly like Web Bluetooth's
     * `requestDevice()` — see [WebSerialPortRegistry]'s KDoc.
     */
    fun requestPort(): Promise<JsSerialPort>
}

internal external interface JsSerialPort : JsAny {
    val readable: JsReadableStream?
    val writable: JsWritableStream?

    fun open(options: JsAny): Promise<JsAny?>

    fun close(): Promise<JsAny?>

    /** Opaque `{ usbVendorId?, usbProductId? }` — read via [vendorId]/[productId], never destructured directly. */
    fun getInfo(): JsAny

    fun addEventListener(type: String, listener: (JsAny) -> Unit)

    fun removeEventListener(type: String, listener: (JsAny) -> Unit)
}

internal external interface JsReadableStream : JsAny {
    fun getReader(): JsReadableStreamDefaultReader
}

internal external interface JsReadableStreamDefaultReader : JsAny {
    fun read(): Promise<JsStreamReadResult>

    fun cancel(): Promise<JsAny?>

    fun releaseLock()
}

/** The `{ value, done }` Web Streams read result. [value] is a `Uint8Array` when [done] is `false`. */
internal external interface JsStreamReadResult : JsAny {
    val done: Boolean
    val value: JsAny?
}

internal external interface JsWritableStream : JsAny {
    fun getWriter(): JsWritableStreamDefaultWriter
}

internal external interface JsWritableStreamDefaultWriter : JsAny {
    fun write(chunk: JsAny): Promise<JsAny?>

    fun close(): Promise<JsAny?>

    fun releaseLock()
}

/** Access to `navigator`, isolated here so nothing else needs a raw `external val`. */
private external val navigator: JsNavigatorSerial

/** `navigator.serial`, or `null` on a browser/context without Web Serial support. */
internal fun webSerialOrNull(): JsSerial? = navigator.serial

private fun openOptions(baudRate: Int): JsAny = js("({ baudRate: baudRate })")

internal suspend fun JsSerialPort.openSuspend(baudRate: Int) {
    open(openOptions(baudRate)).await()
}

internal suspend fun JsSerialPort.closeSuspend() {
    close().await()
}

private fun vendorIdFromInfo(info: JsAny): Int = js("(info.usbVendorId || 0)")

private fun productIdFromInfo(info: JsAny): Int = js("(info.usbProductId || 0)")

/** USB vendor id, or `0` when unavailable (e.g. a non-USB serial backend). */
internal fun JsSerialPort.vendorId(): Int = vendorIdFromInfo(getInfo())

/** USB product id, or `0` when unavailable. */
internal fun JsSerialPort.productId(): Int = productIdFromInfo(getInfo())

internal suspend fun JsSerial.requestPortSuspend(): JsSerialPort = requestPort().await()

private fun jsArrayLength(array: JsAny): Int = js("array.length")

private fun jsArrayGetPort(array: JsAny, index: Int): JsSerialPort = js("array[index]")

/** Every port this page has already been granted access to. */
internal suspend fun JsSerial.getPortsList(): List<JsSerialPort> {
    val jsArray = getPorts().await()
    val length = jsArrayLength(jsArray)
    return List(length) { i -> jsArrayGetPort(jsArray, i) }
}

internal suspend fun JsReadableStreamDefaultReader.readSuspend(): JsStreamReadResult = read().await()

internal suspend fun JsReadableStreamDefaultReader.cancelSuspend() {
    cancel().await()
}

internal suspend fun JsWritableStreamDefaultWriter.writeBytes(bytes: ByteArray) {
    write(bytes.toInt8Array()).await()
}

internal suspend fun JsWritableStreamDefaultWriter.closeSuspend() {
    close().await()
}

// --- Byte-level conversion (ArrayBufferView <-> ByteArray) ---
//
// Same approach as `core:ble`'s `WebBluetoothApi.kt`: kotlinx-browser's Int8Array.toByteArray() does the real work;
// a read result's `value` is a Uint8Array, which is viewed as an Int8Array over the same underlying buffer first
// (any ArrayBufferView exposes .buffer/.byteOffset/.byteLength, so this works regardless of the specific typed-array
// class the stream handed back).

private fun arrayBufferViewAsInt8Array(view: JsAny): Int8Array =
    js("new Int8Array(view.buffer, view.byteOffset, view.byteLength)")

private fun arrayBufferViewToByteArray(view: JsAny): ByteArray = arrayBufferViewAsInt8Array(view).toByteArray()

/** [JsStreamReadResult.value] as a [ByteArray], or `null` when the stream carried no chunk (e.g. at end-of-stream). */
internal fun JsStreamReadResult.valueAsByteArray(): ByteArray? = value?.let(::arrayBufferViewToByteArray)
