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
    // The js("...") snippet references its Kotlin parameter inside the JS source, which detekt cannot see.
    "UnusedParameter",
)

package org.meshtastic.core.model.util

import org.khronos.webgl.Uint8Array
import org.meshtastic.core.common.util.copyBytes

// Backs channel PSK/private-key generation -- must be cryptographically secure. crypto.getRandomValues()
// is the Web Crypto CSPRNG; never Math.random() here.
actual fun platformRandomBytes(size: Int): ByteArray = Uint8Array(size).also(::fillWithCryptoRandomValues).copyBytes()

private fun fillWithCryptoRandomValues(array: Uint8Array): Unit = js("crypto.getRandomValues(array)")
