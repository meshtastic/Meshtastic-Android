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
package org.meshtastic.core.service

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.meshtastic.core.database.desktopDataDir
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.StoredPassphrase
import java.io.File
import javax.crypto.SecretKey

/**
 * File-backed encrypted passphrase store for JVM/Desktop.
 *
 * Each passphrase entry is an AES-256-GCM `.enc` file under `$MESHTASTIC_DATA_DIR/lockdown/` (default:
 * `~/.meshtastic/lockdown/`), keyed by a sanitized device address; see [DesktopKeystoreCipher] for the key handling.
 */
@Single(binds = [LockdownPassphraseStore::class])
@Suppress("TooGenericExceptionCaught")
class LockdownPassphraseStoreImpl(dataDir: File = File(desktopDataDir())) : LockdownPassphraseStore {

    private val lockdownDir: File by lazy { File(dataDir, LOCKDOWN_DIR).also { it.mkdirs() } }

    private val cipher by lazy { DesktopKeystoreCipher(lockdownDir, KEY_ALIAS, KEYSTORE_PASSWORD) }

    private val masterKey: SecretKey? by lazy {
        try {
            cipher.loadOrCreateMasterKey()
        } catch (e: Exception) {
            Logger.e(e) { "Lockdown: Failed to initialize desktop keystore" }
            null
        }
    }

    @Suppress("ReturnCount")
    override fun getPassphrase(deviceAddress: String): StoredPassphrase? {
        val key = masterKey ?: return null
        val file = entryFile(deviceAddress)
        if (!file.exists()) return null
        return try {
            val plaintext = cipher.decrypt(key, file.readBytes())
            deserialize(plaintext)
        } catch (e: Exception) {
            Logger.e(e) { "Lockdown: Failed to read passphrase for device" }
            null
        }
    }

    override fun savePassphrase(
        deviceAddress: String,
        passphrase: String,
        boots: Int,
        hours: Int,
        maxSessionSeconds: Int,
    ) {
        val key = masterKey ?: error("Lockdown: Cannot save passphrase - keystore unavailable")
        val plaintext = serialize(passphrase, boots, hours, maxSessionSeconds)
        entryFile(deviceAddress).writeBytes(cipher.encrypt(key, plaintext))
    }

    override fun clearPassphrase(deviceAddress: String) {
        val file = entryFile(deviceAddress)
        if (file.exists() && !file.delete()) {
            Logger.w { "Lockdown: Passphrase file was not deleted for device" }
        }
    }

    private fun entryFile(deviceAddress: String): File {
        val sanitized = deviceAddress.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(lockdownDir, "$sanitized.enc")
    }

    // region Serialization (simple line-based to avoid adding kotlinx-serialization dependency)

    // Format v2: "boots\nhours\nmaxSessionSeconds\npassphrase" (4 lines).
    // Backward-compat: legacy 3-line entries (no maxSessionSeconds) decode with maxSessionSeconds=0.
    private fun serialize(passphrase: String, boots: Int, hours: Int, maxSessionSeconds: Int): ByteArray =
        "$boots\n$hours\n$maxSessionSeconds\n$passphrase".encodeToByteArray()

    @Suppress("ReturnCount")
    private fun deserialize(plaintext: ByteArray): StoredPassphrase? {
        val text = plaintext.decodeToString()
        // Try v2 (4-line) format first.
        val v2 = text.split("\n", limit = 4)
        if (v2.size == SERIALIZED_LINE_COUNT_V2) {
            val boots = v2[0].toIntOrNull()
            val hours = v2[1].toIntOrNull()
            val maxSession = v2[2].toIntOrNull()
            if (boots != null && hours != null && maxSession != null) {
                return StoredPassphrase(
                    passphrase = v2[3],
                    boots = boots,
                    hours = hours,
                    maxSessionSeconds = maxSession,
                )
            }
        }
        // Fall back to v1 (3-line, no maxSessionSeconds).
        val v1 = text.split("\n", limit = 3)
        if (v1.size < SERIALIZED_LINE_COUNT_V1) {
            Logger.w { "Lockdown: Invalid passphrase entry format" }
            return null
        }
        val boots = v1[0].toIntOrNull()
        val hours = v1[1].toIntOrNull()
        if (boots == null || hours == null) {
            Logger.w { "Lockdown: Invalid passphrase entry metadata" }
            return null
        }
        return StoredPassphrase(passphrase = v1[2], boots = boots, hours = hours)
    }

    // endregion

    private companion object {
        private const val LOCKDOWN_DIR = "lockdown"
        private const val KEY_ALIAS = "lockdown_master"

        // Intentional: this mirrors the documented desktop threat model for at-rest protection only.
        private val KEYSTORE_PASSWORD = "meshtastic-lockdown".toCharArray()
        private const val SERIALIZED_LINE_COUNT_V1 = 3
        private const val SERIALIZED_LINE_COUNT_V2 = 4
    }
}
