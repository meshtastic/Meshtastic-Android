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

/**
 * Opens [fileName] as EncryptedSharedPreferences backed by an AES-256-GCM MasterKey (hardware keystore when available),
 * or returns null when the keystore cannot be initialized. The key is not gated behind user authentication, so
 * background work can read it.
 */
// androidx.security.crypto (MasterKey / EncryptedSharedPreferences) is deprecated by Google with no drop-in AndroidX
// replacement yet. Migrating encrypted storage is a separate, security-sensitive effort; suppress until a stable
// replacement (e.g. Tink) is adopted.
@Suppress("TooGenericExceptionCaught", "DEPRECATION")
internal fun openEncryptedPreferences(app: Application, fileName: String): SharedPreferences? = try {
    val masterKey = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    EncryptedSharedPreferences.create(
        app,
        fileName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
} catch (e: Exception) {
    Logger.e(e) { "Failed to initialize encrypted preferences $fileName" }
    null
}
