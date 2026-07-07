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
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.StoredPassphrase

/**
 * Encrypted per-device storage for lockdown passphrases.
 *
 * Uses EncryptedSharedPreferences backed by an AES-256-GCM MasterKey (hardware keystore when available). The key is
 * intentionally NOT gated behind biometric authentication so that auto-unlock can run in the background without user
 * interaction.
 */
@Single(binds = [LockdownPassphraseStore::class])
class LockdownPassphraseStoreImpl(app: Application) : LockdownPassphraseStore {

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
            Logger.e(e) { "Failed to initialize encrypted passphrase store" }
            null
        }
    }

    private fun requirePrefs(): SharedPreferences = prefs ?: error("Encrypted passphrase store unavailable")

    @Suppress("ReturnCount")
    override fun getPassphrase(deviceAddress: String): StoredPassphrase? {
        val p = requirePrefs()
        val key = sanitizeKey(deviceAddress)
        val passphrase = p.getString("${key}_passphrase", null) ?: return null
        val boots = p.getInt("${key}_boots", LockdownPassphraseStore.DEFAULT_BOOTS)
        val hours = p.getInt("${key}_hours", 0)
        val maxSessionSeconds = p.getInt("${key}_maxSessionSeconds", 0)
        return StoredPassphrase(passphrase, boots, hours, maxSessionSeconds)
    }

    override fun savePassphrase(
        deviceAddress: String,
        passphrase: String,
        boots: Int,
        hours: Int,
        maxSessionSeconds: Int,
    ) {
        val p = requirePrefs()
        val key = sanitizeKey(deviceAddress)
        p.edit()
            .putString("${key}_passphrase", passphrase)
            .putInt("${key}_boots", boots)
            .putInt("${key}_hours", hours)
            .putInt("${key}_maxSessionSeconds", maxSessionSeconds)
            .apply()
    }

    override fun clearPassphrase(deviceAddress: String) {
        val p = requirePrefs()
        val key = sanitizeKey(deviceAddress)
        p.edit()
            .remove("${key}_passphrase")
            .remove("${key}_boots")
            .remove("${key}_hours")
            .remove("${key}_maxSessionSeconds")
            .apply()
    }

    private fun sanitizeKey(address: String): String = address.replace(":", "_")

    private companion object {
        private const val PREFS_FILE_NAME = "lockdown_passphrase_store"
    }
}
