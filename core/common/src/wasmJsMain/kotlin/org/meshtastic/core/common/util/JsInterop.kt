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
package org.meshtastic.core.common.util

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBufferView
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.js.Promise

/**
 * Awaits this promise, rethrowing a rejection that is neither an [Exception] nor an [Error] as an
 * [IllegalStateException]. Kotlin/Wasm surfaces a rejected browser promise as `JsException`, a bare [Throwable] that
 * `catch (e: Exception)` misses.
 */
// "Neither Exception nor Error" has no catch clause of its own; separate clauses would only rethrow.
@Suppress("TooGenericExceptionCaught", "InstanceOfCheckForException")
suspend fun <T : JsAny?> Promise<T>.awaitOrThrowException(): T = try {
    await()
} catch (t: Throwable) {
    throw if (t is Exception || t is Error) t else IllegalStateException(t.message, t)
}

/** Copies this array into a new `Uint8Array`. */
fun ByteArray.toUint8Array(): Uint8Array {
    val signed = toInt8Array()
    return Uint8Array(signed.buffer, signed.byteOffset, signed.length)
}

/** Copies the bytes this view covers, whatever its element type. */
fun ArrayBufferView.copyBytes(): ByteArray = Int8Array(buffer, byteOffset, byteLength).toByteArray()
