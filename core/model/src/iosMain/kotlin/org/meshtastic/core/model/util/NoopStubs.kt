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
package org.meshtastic.core.model.util

/** No-op stubs for core:model on iOS. */

actual fun getShortDateTime(time: Long): String = ""

actual fun platformRandomBytes(size: Int): ByteArray = ByteArray(size)

actual object SfppHasher {
    actual fun computeMessageHash(encryptedPayload: ByteArray, to: Int, from: Int, id: Int): ByteArray = ByteArray(32)
}
