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
package org.meshtastic.core.takserver.fountain

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_OK
import platform.zlib.compress
import platform.zlib.compressBound
import platform.zlib.uncompress

internal actual object ZlibCodec {
    @OptIn(ExperimentalForeignApi::class)
    actual fun compress(data: ByteArray): ByteArray? {
        if (data.isEmpty()) return ByteArray(0)

        return memScoped {
            val destLen = alloc<platform.zlib.uLongVar>()
            destLen.value = compressBound(data.size.toULong())

            val destBuffer = ByteArray(destLen.value.toInt())

            val result =
                destBuffer.usePinned { destPin ->
                    data.usePinned { srcPin ->
                        compress(
                            destPin.addressOf(0).reinterpret(),
                            destLen.ptr,
                            srcPin.addressOf(0).reinterpret(),
                            data.size.toULong(),
                        )
                    }
                }

            if (result == Z_OK) {
                destBuffer.copyOf(destLen.value.toInt())
            } else {
                null
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun decompress(data: ByteArray): ByteArray? {
        if (data.isEmpty()) return ByteArray(0)

        var currentSize = data.size * 4
        var maxAttempts = 5

        while (maxAttempts > 0) {
            val success = memScoped {
                val destLen = alloc<platform.zlib.uLongVar>()
                destLen.value = currentSize.toULong()

                val destBuffer = ByteArray(currentSize)

                val result =
                    destBuffer.usePinned { destPin ->
                        data.usePinned { srcPin ->
                            uncompress(
                                destPin.addressOf(0).reinterpret(),
                                destLen.ptr,
                                srcPin.addressOf(0).reinterpret(),
                                data.size.toULong(),
                            )
                        }
                    }

                if (result == Z_OK) {
                    return@memScoped destBuffer.copyOf(destLen.value.toInt())
                } else if (result == Z_BUF_ERROR) {
                    currentSize *= 2
                    maxAttempts--
                    null
                } else {
                    maxAttempts = 0
                    null
                }
            }
            if (success != null) return success
        }
        return null
    }
}
