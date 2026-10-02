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

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption for the desktop secure stores, keyed by a master key held in a PKCS12 keystore in [dir].
 *
 * The keystore password is fixed because the threat model mirrors Android's `EncryptedSharedPreferences`: file-system
 * permission is the primary access control; the encryption layer protects data at rest against casual file browsing or
 * backup leakage, not against a compromised user account.
 */
internal class DesktopKeystoreCipher(
    private val dir: File,
    private val keyAlias: String,
    private val keystorePassword: CharArray,
) {
    /**
     * Loads the master key, creating the keystore and key on first use.
     *
     * Throws when the keystore exists but holds no key under [keyAlias]: generating a replacement would overwrite the
     * keystore and orphan every file encrypted under the old key.
     */
    fun loadOrCreateMasterKey(): SecretKey {
        val ksFile = File(dir, KEYSTORE_FILE)
        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        val protection = KeyStore.PasswordProtection(keystorePassword)
        if (ksFile.exists()) {
            FileInputStream(ksFile).use { ks.load(it, keystorePassword) }
            val entry = ks.getEntry(keyAlias, protection)
            check(entry is KeyStore.SecretKeyEntry) { "Keystore exists but master key $keyAlias is missing/invalid" }
            return entry.secretKey
        }
        val keyGen = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGen.init(AES_KEY_BITS)
        val secretKey = keyGen.generateKey()
        ks.load(null, keystorePassword)
        ks.setEntry(keyAlias, KeyStore.SecretKeyEntry(secretKey), protection)
        FileOutputStream(ksFile).use { ks.store(it, keystorePassword) }
        return secretKey
    }

    /** Encrypts [plaintext] as `[1 byte IV length][IV][ciphertext]`. */
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    fun decrypt(key: SecretKey, data: ByteArray): ByteArray {
        val ivLength = data[0].toInt() and BYTE_MASK
        val iv = data.copyOfRange(1, 1 + ivLength)
        val ciphertext = data.copyOfRange(1 + ivLength, data.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        private const val KEYSTORE_FILE = "keystore.p12"
        private const val KEYSTORE_TYPE = "PKCS12"
        private const val AES_ALGORITHM = "AES"
        private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val BYTE_MASK = 0xFF
    }
}
