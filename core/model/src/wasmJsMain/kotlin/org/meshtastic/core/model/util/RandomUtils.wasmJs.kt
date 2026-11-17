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
    // Each js("...") snippet below references its Kotlin parameter (size/array/index) INSIDE the embedded JS
    // source string, which detekt's static analysis can't see — a false positive, not a real unused parameter.
    "UnusedParameter",
)

package org.meshtastic.core.model.util

// Backs channel PSK/private-key generation -- must be cryptographically secure. crypto.getRandomValues()
// is the Web Crypto CSPRNG; never Math.random() here.
actual fun platformRandomBytes(size: Int): ByteArray {
    val array = newUint8Array(size)
    fillWithCryptoRandomValues(array)
    return ByteArray(size) { i -> uint8ArrayGet(array, i).toByte() }
}

internal external interface JsUint8Array : JsAny

private fun newUint8Array(size: Int): JsUint8Array = js("new Uint8Array(size)")

private fun fillWithCryptoRandomValues(array: JsUint8Array): Unit = js("crypto.getRandomValues(array)")

private fun uint8ArrayGet(array: JsUint8Array, index: Int): Int = js("array[index]")
