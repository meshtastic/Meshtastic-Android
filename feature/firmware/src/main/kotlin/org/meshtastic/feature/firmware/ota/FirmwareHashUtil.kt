/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.firmware.ota

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Utility functions for firmware hash calculation and verification.
 */
object FirmwareHashUtil {
    
    /**
     * Calculate SHA-256 hash of a file.
     * @param file Firmware file to hash
     * @return 64-character hex string of the SHA-256 hash
     */
    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().toHexString()
    }

    /**
     * Calculate SHA-256 hash of a byte array.
     * @param data Firmware data to hash
     * @return 64-character hex string of the SHA-256 hash
     */
    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data)
        return digest.digest().toHexString()
    }

    /**
     * Verify that a file's SHA-256 hash matches the expected hash.
     * @param file Firmware file to verify
     * @param expectedHash Expected SHA-256 hash (64 hex characters)
     * @return true if hashes match, false otherwise
     */
    fun verifySha256(file: File, expectedHash: String): Boolean {
        val actualHash = calculateSha256(file)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    /**
     * Convert byte array to hex string.
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
