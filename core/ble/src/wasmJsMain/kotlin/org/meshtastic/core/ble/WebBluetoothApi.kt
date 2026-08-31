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
    // The entire Web Bluetooth surface this file wraps (see its own KDoc) genuinely needs this many
    // declarations in one place — splitting it would scatter `external`/`js()` interop across files instead
    // of isolating it here, the opposite of this file's own stated purpose.
    "TooManyFunctions",
    // Every js("...") snippet below references its Kotlin parameter (view/array/index/value/serviceUuid) INSIDE
    // the JS source string, which detekt's static analysis can't see — it only sees the js() call, not what the
    // embedded JS actually references, and flags each parameter as unused. A real false positive, not a real gap.
    "UnusedParameter",
)

package org.meshtastic.core.ble

import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.js.Promise

/**
 * Raw Web Bluetooth JS interop, isolated to this one file so nothing else in `wasmJsMain` needs to touch `external`
 * declarations or `js()` snippets directly. Everything above this layer (`WebBleConnection`, `WebBleService`,
 * `WebBleScanner`, ...) talks only to the small Kotlin-shaped API declared here.
 *
 * Modeled on the standard Web Bluetooth surface (https://webbluetoothcg.github.io/web-bluetooth/) rather than
 * reinvented, so the shape is recognisable to anyone who has used `navigator.bluetooth` from JS directly.
 */
internal external interface JsNavigator : JsAny {
    val bluetooth: JsBluetooth?
}

internal external interface JsBluetooth : JsAny {
    fun getAvailability(): Promise<JsBoolean>

    fun requestDevice(options: JsAny): Promise<JsBluetoothDevice>
}

internal external interface JsBluetoothDevice : JsAny {
    val id: String
    val name: String?
    val gatt: JsBluetoothRemoteGATTServer?

    fun addEventListener(type: String, listener: (JsAny) -> Unit)

    fun removeEventListener(type: String, listener: (JsAny) -> Unit)
}

internal external interface JsBluetoothRemoteGATTServer : JsAny {
    val connected: Boolean

    fun connect(): Promise<JsBluetoothRemoteGATTServer>

    fun disconnect()

    fun getPrimaryService(serviceUuid: String): Promise<JsBluetoothRemoteGATTService>
}

internal external interface JsBluetoothRemoteGATTService : JsAny {
    fun getCharacteristic(characteristicUuid: String): Promise<JsBluetoothRemoteGATTCharacteristic>

    /**
     * All characteristics for this service, with no filter — used instead of hardcoding a fixed characteristic list, so
     * this layer stays as protocol-agnostic as Kable's own service discovery (which likewise enumerates everything the
     * peripheral advertises).
     */
    fun getCharacteristics(): Promise<JsAny>
}

internal external interface JsBluetoothCharacteristicProperties : JsAny {
    val write: Boolean
    val writeWithoutResponse: Boolean
}

internal external interface JsBluetoothRemoteGATTCharacteristic : JsAny {
    /** The characteristic's own UUID, canonical (lowercase, dashed) form per the Web Bluetooth spec. */
    val uuid: String

    val properties: JsBluetoothCharacteristicProperties

    fun readValue(): Promise<JsAny>

    fun writeValueWithResponse(value: JsAny): Promise<JsAny?>

    fun writeValueWithoutResponse(value: JsAny): Promise<JsAny?>

    fun startNotifications(): Promise<JsBluetoothRemoteGATTCharacteristic>

    fun stopNotifications(): Promise<JsBluetoothRemoteGATTCharacteristic>

    fun addEventListener(type: String, listener: (JsAny) -> Unit)

    fun removeEventListener(type: String, listener: (JsAny) -> Unit)

    /** The characteristic's cached value (a `DataView`), synchronously up to date after a read or a notification. */
    val value: JsAny?
}

/** Access to `navigator`, isolated here so nothing else needs a raw `external val`. */
private external val navigator: JsNavigator

/** `navigator.bluetooth`, or `null` on a browser/context without Web Bluetooth support. */
internal fun webBluetoothOrNull(): JsBluetooth? = navigator.bluetooth

/** Builds the `RequestDeviceOptions` JS object: `{ filters: [{ services: [serviceUuid] }] }`. */
private fun requestDeviceOptions(serviceUuid: String): JsAny = js("({ filters: [{ services: [serviceUuid] }] })")

internal suspend fun JsBluetooth.requestDeviceForService(serviceUuid: String): JsBluetoothDevice =
    requestDevice(requestDeviceOptions(serviceUuid)).await()

internal suspend fun JsBluetooth.isAvailable(): Boolean = jsBooleanToBoolean(getAvailability().await())

internal suspend fun JsBluetoothRemoteGATTServer.connectSuspend(): JsBluetoothRemoteGATTServer = connect().await()

internal suspend fun JsBluetoothRemoteGATTServer.getPrimaryServiceSuspend(
    serviceUuid: String,
): JsBluetoothRemoteGATTService = getPrimaryService(serviceUuid).await()

internal suspend fun JsBluetoothRemoteGATTService.getCharacteristicSuspend(
    characteristicUuid: String,
): JsBluetoothRemoteGATTCharacteristic = getCharacteristic(characteristicUuid).await()

/** Enumerates every characteristic Web Bluetooth reports for this service, unfiltered. */
internal suspend fun JsBluetoothRemoteGATTService.getAllCharacteristics(): List<JsBluetoothRemoteGATTCharacteristic> {
    val jsArray = getCharacteristics().await()
    val length = jsArrayLength(jsArray)
    return List(length) { i -> jsArrayGetCharacteristic(jsArray, i) }
}

internal suspend fun JsBluetoothRemoteGATTCharacteristic.readValueAsByteArray(): ByteArray =
    dataViewToByteArray(readValue().await())

internal suspend fun JsBluetoothRemoteGATTCharacteristic.writeWithResponse(data: ByteArray) {
    writeValueWithResponse(byteArrayToUint8Array(data)).await()
}

internal suspend fun JsBluetoothRemoteGATTCharacteristic.writeWithoutResponseSuspend(data: ByteArray) {
    writeValueWithoutResponse(byteArrayToUint8Array(data)).await()
}

internal suspend fun JsBluetoothRemoteGATTCharacteristic.startNotificationsSuspend() {
    startNotifications().await()
}

internal suspend fun JsBluetoothRemoteGATTCharacteristic.stopNotificationsSuspend() {
    stopNotifications().await()
}

/** Reads the characteristic's cached `value` (a `DataView`) as a [ByteArray], or an empty array when unset. */
internal fun JsBluetoothRemoteGATTCharacteristic.currentValueAsByteArray(): ByteArray =
    value?.let { dataViewToByteArray(it) } ?: ByteArray(0)

// --- Byte-level conversion helpers (DataView <-> ByteArray) ---
//
// kotlinx-browser (JetBrains, the documented Kotlin/Wasm typed-array interop library) provides
// Int8Array.toByteArray()/ByteArray.toInt8Array() directly. DataView itself has no kotlinx-browser
// conversion, so it's viewed as an Int8Array over the same underlying buffer first (one small js()
// snippet), then handed to the real library function.

private fun dataViewAsInt8Array(view: JsAny): Int8Array =
    js("new Int8Array(view.buffer, view.byteOffset, view.byteLength)")

private fun dataViewToByteArray(view: JsAny): ByteArray = dataViewAsInt8Array(view).toByteArray()

private fun byteArrayToUint8Array(bytes: ByteArray): JsAny = bytes.toInt8Array()

private fun jsBooleanToBoolean(value: JsAny): Boolean = js("value")

private fun jsArrayLength(array: JsAny): Int = js("array.length")

private fun jsArrayGetCharacteristic(array: JsAny, index: Int): JsBluetoothRemoteGATTCharacteristic = js("array[index]")

/** Registers [listener] for [type] on this event target and returns a handle that removes it on [remove]. */
internal fun JsBluetoothDevice.addListener(type: String, listener: (JsAny) -> Unit): () -> Unit {
    addEventListener(type, listener)
    return { removeEventListener(type, listener) }
}

internal fun JsBluetoothRemoteGATTCharacteristic.addListener(type: String, listener: (JsAny) -> Unit): () -> Unit {
    addEventListener(type, listener)
    return { removeEventListener(type, listener) }
}
