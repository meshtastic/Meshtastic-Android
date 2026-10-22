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
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.TakCertificateMaterial
import org.meshtastic.core.repository.TakCertificateStore
import kotlin.io.encoding.Base64

/**
 * Encrypted per-install storage for the local TAK server's TLS material, in [openEncryptedPreferences]. Blobs are
 * Base64-encoded because EncryptedSharedPreferences only stores strings.
 */
@Single(binds = [TakCertificateStore::class])
class TakCertificateStoreImpl(app: Application) : TakCertificateStore {

    private val prefs: SharedPreferences? by lazy { openEncryptedPreferences(app, PREFS_FILE_NAME) }

    private fun requirePrefs(): SharedPreferences = prefs ?: error("Encrypted TAK certificate store unavailable")

    @Suppress("ReturnCount")
    override fun load(): TakCertificateMaterial? {
        val p = prefs ?: return null
        val password = p.getString(KEY_PASSWORD, null) ?: return null
        val server = p.getString(KEY_SERVER_P12, null) ?: return null
        val client = p.getString(KEY_CLIENT_P12, null) ?: return null
        val truststore = p.getString(KEY_TRUSTSTORE_P12, null) ?: return null
        return TakCertificateMaterial(
            serverP12 = Base64.decode(server),
            clientP12 = Base64.decode(client),
            truststoreP12 = Base64.decode(truststore),
            password = password,
        )
    }

    override fun save(material: TakCertificateMaterial) {
        requirePrefs()
            .edit()
            .putString(KEY_PASSWORD, material.password)
            .putString(KEY_SERVER_P12, Base64.encode(material.serverP12))
            .putString(KEY_CLIENT_P12, Base64.encode(material.clientP12))
            .putString(KEY_TRUSTSTORE_P12, Base64.encode(material.truststoreP12))
            .apply()
    }

    override fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    private companion object {
        private const val PREFS_FILE_NAME = "tak_certificate_store"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SERVER_P12 = "server_p12"
        private const val KEY_CLIENT_P12 = "client_p12"
        private const val KEY_TRUSTSTORE_P12 = "truststore_p12"
    }
}
