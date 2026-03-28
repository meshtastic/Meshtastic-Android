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

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.Inflater

internal actual object ZlibCodec {
    actual fun compress(data: ByteArray): ByteArray? = try {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        deflater.setInput(data)
        deflater.finish()

        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        outputStream.close()
        deflater.end()
        outputStream.toByteArray()
    } catch (e: Exception) {
        null
    }

    actual fun decompress(data: ByteArray): ByteArray? = try {
        val inflater = Inflater(false)
        inflater.setInput(data)

        val outputStream = ByteArrayOutputStream(data.size * 2)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) {
                break
            }
            outputStream.write(buffer, 0, count)
        }
        outputStream.close()
        inflater.end()
        outputStream.toByteArray()
    } catch (e: Exception) {
        null
    }
}

internal actual object CryptoCodec {
    actual fun sha256Prefix8(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).copyOf(8)
    }
}
