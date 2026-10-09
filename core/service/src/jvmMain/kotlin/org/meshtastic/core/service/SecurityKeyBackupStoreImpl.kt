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
import javax.crypto.SecretKey

/**
 * File-backed encrypted key-backup store for JVM/Desktop, mirroring [LockdownPassphraseStoreImpl]. Each node's key
 * backup is an AES-256-GCM `.enc` file under `$MESHTASTIC_DATA_DIR/security_keys/`; see [DesktopKeystoreCipher] for the
 * key handling.
 */
@Single(binds = [SecurityKeyBackupStore::class])
@Suppress("TooGenericExceptionCaught")
class SecurityKeyBackupStoreImpl(dataDir: File = File(desktopDataDir())) : SecurityKeyBackupStore {

    private val storeDir: File by lazy { File(dataDir, STORE_DIR).also { it.mkdirs() } }

    private val cipher by lazy { DesktopKeystoreCipher(storeDir, KEY_ALIAS, KEYSTORE_PASSWORD) }

    private val masterKey: SecretKey? by lazy {
        try {
            cipher.loadOrCreateMasterKey()
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
            val plaintext = cipher.decrypt(key, file.readBytes())
            deserialize(plaintext)
        } catch (e: Exception) {
            Logger.e(e) { "SecurityKeyBackup: Failed to read key backup for node" }
            null
        }
    }

    override fun all(): Map<Int, StoredSecurityKeys> = storeDir
        .listFiles { file -> file.name.endsWith(ENTRY_SUFFIX) }
        .orEmpty()
        .mapNotNull { it.name.removeSuffix(ENTRY_SUFFIX).toIntOrNull() }
        .mapNotNull { num -> get(num)?.let { num to it } }
        .toMap()

    override fun save(nodeNum: Int, publicKeyBase64: String, privateKeyBase64: String, timestamp: Long) {
        val key = masterKey ?: error("SecurityKeyBackup: Cannot save keys - keystore unavailable")
        val plaintext = "$timestamp\n$publicKeyBase64\n$privateKeyBase64".encodeToByteArray()
        entryFile(nodeNum).writeBytes(cipher.encrypt(key, plaintext))
    }

    override fun delete(nodeNum: Int) {
        val file = entryFile(nodeNum)
        if (file.exists() && !file.delete()) {
            Logger.w { "SecurityKeyBackup: Key backup file was not deleted for node" }
        }
    }

    private fun entryFile(nodeNum: Int): File = File(storeDir, "$nodeNum$ENTRY_SUFFIX")

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

    private companion object {
        private const val STORE_DIR = "security_keys"
        private const val KEY_ALIAS = "security_key_backup_master"

        // Intentional: mirrors LockdownPassphraseStoreImpl's documented desktop threat model.
        private val KEYSTORE_PASSWORD = "meshtastic-security-keys".toCharArray()
        private const val SERIALIZED_LINE_COUNT = 3
        private const val ENTRY_SUFFIX = ".enc"
    }
}
