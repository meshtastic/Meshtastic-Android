/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.service

import android.app.Application
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

data class StoredPassphrase(
    val passphrase: String,
    val boots: Int,
    val hours: Int,
)

@Singleton
class TakPassphraseStore @Inject constructor(app: Application) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            app,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getPassphrase(deviceAddress: String): StoredPassphrase? {
        val key = sanitizeKey(deviceAddress)
        val passphrase = prefs.getString("${key}_passphrase", null) ?: return null
        val boots = prefs.getInt("${key}_boots", DEFAULT_BOOTS)
        val hours = prefs.getInt("${key}_hours", 0)
        return StoredPassphrase(passphrase, boots, hours)
    }

    fun savePassphrase(deviceAddress: String, passphrase: String, boots: Int, hours: Int) {
        val key = sanitizeKey(deviceAddress)
        prefs.edit()
            .putString("${key}_passphrase", passphrase)
            .putInt("${key}_boots", boots)
            .putInt("${key}_hours", hours)
            .apply()
    }

    fun clearPassphrase(deviceAddress: String) {
        val key = sanitizeKey(deviceAddress)
        prefs.edit()
            .remove("${key}_passphrase")
            .remove("${key}_boots")
            .remove("${key}_hours")
            .apply()
    }

    private fun sanitizeKey(address: String): String = address.replace(":", "_")

    companion object {
        private const val PREFS_FILE_NAME = "tak_passphrase_store"
        const val DEFAULT_BOOTS = 50
    }
}
