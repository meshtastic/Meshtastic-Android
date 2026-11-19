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
    "TooManyFunctions",
    // The js("...") snippet references its Kotlin parameter inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.core.network.serial

import org.khronos.webgl.ArrayBufferView
import org.khronos.webgl.toInt8Array
import org.meshtastic.core.common.util.awaitOrThrowException
import org.meshtastic.core.common.util.copyBytes
import kotlin.js.Promise

/**
 * Raw Web Serial JS interop (https://wicg.github.io/serial/), isolated to this file: everything above it talks only to
 * the Kotlin-shaped API declared here, never to `external`/`js()` directly. Every promise is awaited through
 * [awaitOrThrowException], so a rejection reaches `catch (e: Exception)`.
 */
internal external interface JsNavigatorSerial : JsAny {
    val serial: JsSerial?
}

internal external interface JsSerial : JsAny {
    /** Ports already granted to this page in a previous session — no user gesture required. */
    fun getPorts(): Promise<JsArray<JsSerialPort>>

    /** Shows the browser's serial-port picker. Requires an active user gesture; see [WebSerialPortRegistry]. */
    fun requestPort(): Promise<JsSerialPort>
}

internal external interface JsSerialPort : JsAny {
    val readable: JsReadableStream?
    val writable: JsWritableStream?

    fun open(options: JsAny): Promise<JsAny?>

    fun close(): Promise<JsAny?>

    fun getInfo(): JsSerialPortInfo

    fun addEventListener(type: String, listener: (JsAny) -> Unit)

    fun removeEventListener(type: String, listener: (JsAny) -> Unit)
}

/** `SerialPortInfo`; both ids are absent for a non-USB serial backend. */
internal external interface JsSerialPortInfo : JsAny {
    val usbVendorId: JsNumber?
    val usbProductId: JsNumber?
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
    val value: ArrayBufferView?
}

internal external interface JsWritableStream : JsAny {
    fun getWriter(): JsWritableStreamDefaultWriter
}

internal external interface JsWritableStreamDefaultWriter : JsAny {
    fun write(chunk: JsAny): Promise<JsAny?>

    fun close(): Promise<JsAny?>

    fun releaseLock()
}

private external val navigator: JsNavigatorSerial

/** `navigator.serial`, or `null` on a browser/context without Web Serial support. */
internal fun webSerialOrNull(): JsSerial? = navigator.serial

private fun openOptions(baudRate: Int): JsAny = js("({ baudRate: baudRate })")

internal suspend fun JsSerialPort.openSuspend(baudRate: Int) {
    open(openOptions(baudRate)).awaitOrThrowException()
}

internal suspend fun JsSerialPort.closeSuspend() {
    close().awaitOrThrowException()
}

/** USB vendor id, or `0` when unavailable (e.g. a non-USB serial backend). */
internal fun JsSerialPort.vendorId(): Int = getInfo().usbVendorId?.toInt() ?: 0

/** USB product id, or `0` when unavailable. */
internal fun JsSerialPort.productId(): Int = getInfo().usbProductId?.toInt() ?: 0

internal suspend fun JsSerial.requestPortSuspend(): JsSerialPort = requestPort().awaitOrThrowException()

/** Every port this page has already been granted access to. */
internal suspend fun JsSerial.getPortsList(): List<JsSerialPort> = getPorts().awaitOrThrowException().toList()

internal suspend fun JsReadableStreamDefaultReader.readSuspend(): JsStreamReadResult = read().awaitOrThrowException()

internal suspend fun JsReadableStreamDefaultReader.cancelSuspend() {
    cancel().awaitOrThrowException()
}

internal suspend fun JsWritableStreamDefaultWriter.writeBytes(bytes: ByteArray) {
    write(bytes.toInt8Array()).awaitOrThrowException()
}

internal suspend fun JsWritableStreamDefaultWriter.closeSuspend() {
    close().awaitOrThrowException()
}

/** [JsStreamReadResult.value] as a [ByteArray], or `null` when the stream carried no chunk (e.g. at end-of-stream). */
internal fun JsStreamReadResult.valueAsByteArray(): ByteArray? = value?.copyBytes()
