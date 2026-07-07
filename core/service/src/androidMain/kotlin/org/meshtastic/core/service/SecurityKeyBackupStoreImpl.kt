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

import android.app.Application
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.SecurityKeyBackupStore
import org.meshtastic.core.repository.StoredSecurityKeys

/**
 * Encrypted per-node storage for security key backups.
 *
 * Uses EncryptedSharedPreferences backed by an AES-256-GCM MasterKey (hardware keystore when available), mirroring
 * [LockdownPassphraseStoreImpl]. Keyed by node number, matching iOS's per-node Keychain entries.
 */
@Single(binds = [SecurityKeyBackupStore::class])
class SecurityKeyBackupStoreImpl(app: Application) : SecurityKeyBackupStore {

    // androidx.security.crypto (MasterKey / EncryptedSharedPreferences) is deprecated by Google with no
    // drop-in AndroidX replacement yet. Migrating encrypted storage is a separate, security-sensitive
    // effort; suppress until a stable replacement (e.g. Tink) is adopted.
    @Suppress("TooGenericExceptionCaught", "DEPRECATION")
    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                app,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to initialize encrypted security key backup store" }
            null
        }
    }

    @Suppress("ReturnCount")
    override fun get(nodeNum: Int): StoredSecurityKeys? {
        val p = prefs ?: return null
        val publicKey = p.getString("${nodeNum}_public", null) ?: return null
        val privateKey = p.getString("${nodeNum}_private", null) ?: return null
        val timestamp = p.getLong("${nodeNum}_timestamp", 0L)
        return StoredSecurityKeys(publicKey, privateKey, timestamp)
    }

    override fun save(nodeNum: Int, publicKeyBase64: String, privateKeyBase64: String, timestamp: Long) {
        val p = prefs ?: error("Encrypted security key backup store unavailable")
        p.edit()
            .putString("${nodeNum}_public", publicKeyBase64)
            .putString("${nodeNum}_private", privateKeyBase64)
            .putLong("${nodeNum}_timestamp", timestamp)
            .apply()
    }

    override fun delete(nodeNum: Int) {
        val p = prefs ?: return
        p.edit().remove("${nodeNum}_public").remove("${nodeNum}_private").remove("${nodeNum}_timestamp").apply()
    }

    private companion object {
        private const val PREFS_FILE_NAME = "security_key_backup_store"
    }
}
