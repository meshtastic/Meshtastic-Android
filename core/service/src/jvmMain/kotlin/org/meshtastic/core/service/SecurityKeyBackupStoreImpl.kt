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
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.StoredSecurityKeys
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * File-backed encrypted key-backup store for JVM/Desktop, mirroring [LockdownPassphraseStoreImpl]'s desktop
 * counterpart. Uses a PKCS12 KeyStore to hold an AES-256 master key and AES-256-GCM to encrypt each node's key backup,
 * stored as individual `.enc` files under `$MESHTASTIC_DATA_DIR/security_keys/`.
 */
@Single(binds = [SecurityKeyBackupStore::class])
@Suppress("TooGenericExceptionCaught")
class SecurityKeyBackupStoreImpl : SecurityKeyBackupStore {

    private val storeDir: File by lazy { File(desktopDataDir(), STORE_DIR).also { it.mkdirs() } }

    private val masterKey: SecretKey? by lazy {
        try {
            loadOrCreateMasterKey()
        } catch (e: Exception) {
            Logger.e(e) { "SecurityKeyBackup: Failed to initialize desktop keystore" }
            null
        }
    }

    @Suppress("ReturnCount")
    override fun get(nodeNum: Int): StoredSecurityKeys? {
        val key = masterKey ?: return null
        val file = entryFile(nodeNum)
        if (!file.exists()) return null
        return try {
            val plaintext = decrypt(key, file.readBytes())
            deserialize(plaintext)
        } catch (e: Exception) {
            Logger.e(e) { "SecurityKeyBackup: Failed to read key backup for node" }
            null
        }
    }

    override fun save(nodeNum: Int, publicKeyBase64: String, privateKeyBase64: String, timestamp: Long) {
        val key = masterKey ?: error("SecurityKeyBackup: Cannot save keys - keystore unavailable")
        val plaintext = "$timestamp\n$publicKeyBase64\n$privateKeyBase64".encodeToByteArray()
        entryFile(nodeNum).writeBytes(encrypt(key, plaintext))
    }

    override fun delete(nodeNum: Int) {
        val file = entryFile(nodeNum)
        if (file.exists() && !file.delete()) {
            Logger.w { "SecurityKeyBackup: Key backup file was not deleted for node" }
        }
    }

    private fun entryFile(nodeNum: Int): File = File(storeDir, "$nodeNum.enc")

    @Suppress("ReturnCount")
    private fun deserialize(plaintext: ByteArray): StoredSecurityKeys? {
        val parts = plaintext.decodeToString().split("\n", limit = 3)
        if (parts.size != SERIALIZED_LINE_COUNT) {
            Logger.w { "SecurityKeyBackup: Invalid key backup entry format" }
            return null
        }
        val timestamp = parts[0].toLongOrNull() ?: return null
        return StoredSecurityKeys(publicKeyBase64 = parts[1], privateKeyBase64 = parts[2], timestamp = timestamp)
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

    // region KeyStore

    private fun loadOrCreateMasterKey(): SecretKey {
        val ksFile = File(storeDir, KEYSTORE_FILE)
        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        val protection = KeyStore.PasswordProtection(KEYSTORE_PASSWORD)
        if (ksFile.exists()) {
            FileInputStream(ksFile).use { ks.load(it, KEYSTORE_PASSWORD) }
            val entry = ks.getEntry(KEY_ALIAS, protection)
            // Fail loudly rather than regenerate: overwriting the master key would orphan every existing .enc backup.
            check(entry is KeyStore.SecretKeyEntry) { "Keystore exists but master key $KEY_ALIAS is missing/invalid" }
            return entry.secretKey
        }
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
        private const val STORE_DIR = "security_keys"
        private const val KEYSTORE_FILE = "keystore.p12"
        private const val KEYSTORE_TYPE = "PKCS12"
        private const val KEY_ALIAS = "security_key_backup_master"

        // Intentional: mirrors LockdownPassphraseStoreImpl's documented desktop threat model.
        private val KEYSTORE_PASSWORD = "meshtastic-security-keys".toCharArray()
        private const val AES_ALGORITHM = "AES"
        private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val BYTE_MASK = 0xFF
        private const val SERIALIZED_LINE_COUNT = 3
    }
}
