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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * File-backed encrypted passphrase store for JVM/Desktop.
 *
 * Uses a PKCS12 KeyStore to hold an AES-256 master key and AES-256-GCM to encrypt each passphrase entry. Entries are
 * stored as individual `.enc` files under `$MESHTASTIC_DATA_DIR/lockdown/` (default: `~/.meshtastic/lockdown/`), keyed
 * by a sanitized device address.
 *
 * The keystore password is fixed because the threat model mirrors Android's `EncryptedSharedPreferences`: file-system
 * permission is the primary access control; the encryption layer protects data at rest against casual file browsing or
 * backup leakage, not against a compromised user account.
 */
@Single(binds = [LockdownPassphraseStore::class])
@Suppress("TooGenericExceptionCaught")
class LockdownPassphraseStoreImpl : LockdownPassphraseStore {

    private val lockdownDir: File by lazy { File(desktopDataDir(), LOCKDOWN_DIR).also { it.mkdirs() } }

    private val masterKey: SecretKey? by lazy {
        try {
            loadOrCreateMasterKey()
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
            val encrypted = file.readBytes()
            val plaintext = decrypt(key, encrypted)
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
        val encrypted = encrypt(key, plaintext)
        entryFile(deviceAddress).writeBytes(encrypted)
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

    // region Encryption

    private fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        // Format: [1 byte IV length][IV][ciphertext]
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun decrypt(key: SecretKey, data: ByteArray): ByteArray {
        val ivLength = data[0].toInt() and BYTE_MASK
        val iv = data.copyOfRange(1, 1 + ivLength)
        val ciphertext = data.copyOfRange(1 + ivLength, data.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    // endregion

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

    // region KeyStore

    private fun loadOrCreateMasterKey(): SecretKey {
        val ksFile = File(lockdownDir, KEYSTORE_FILE)
        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        val protection = KeyStore.PasswordProtection(KEYSTORE_PASSWORD)
        if (ksFile.exists()) {
            FileInputStream(ksFile).use { ks.load(it, KEYSTORE_PASSWORD) }
            val entry = ks.getEntry(KEY_ALIAS, protection)
            if (entry is KeyStore.SecretKeyEntry) return entry.secretKey
        }
        // Generate new master key
        val keyGen = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGen.init(AES_KEY_BITS)
        val secretKey = keyGen.generateKey()
        ks.load(null, KEYSTORE_PASSWORD)
        ks.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(secretKey), protection)
        FileOutputStream(ksFile).use { ks.store(it, KEYSTORE_PASSWORD) }
        return secretKey
    }

    // endregion

    private companion object {
        private const val LOCKDOWN_DIR = "lockdown"
        private const val KEYSTORE_FILE = "keystore.p12"
        private const val KEYSTORE_TYPE = "PKCS12"
        private const val KEY_ALIAS = "lockdown_master"

        // Intentional: this mirrors the documented desktop threat model for at-rest protection only.
        private val KEYSTORE_PASSWORD = "meshtastic-lockdown".toCharArray()
        private const val AES_ALGORITHM = "AES"
        private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val BYTE_MASK = 0xFF
        private const val SERIALIZED_LINE_COUNT_V1 = 3
        private const val SERIALIZED_LINE_COUNT_V2 = 4
    }
}
