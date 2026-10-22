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
package org.meshtastic.core.repository

/**
 * The per-install TLS material for the local TAK server.
 *
 * Every blob is a PKCS#12 container protected by [password]:
 * - [serverP12]: the server identity (certificate + private key) presented to ATAK / iTAK.
 * - [clientP12]: the single client identity ATAK must present; handed to the user inside the exported data package.
 * - [truststoreP12]: the issuing CA certificate only (no private key); ATAK uses it to trust the server.
 *
 * The CA private key is discarded after issuing, so nothing (including this app) can mint further client certificates
 * for this install; a fresh set is generated instead.
 */
class TakCertificateMaterial(
    val serverP12: ByteArray,
    val clientP12: ByteArray,
    val truststoreP12: ByteArray,
    val password: String,
) {
    init {
        require(password.isNotEmpty()) { "password must not be empty" }
    }
}

/**
 * Encrypted per-install storage for [TakCertificateMaterial].
 *
 * Platform implementations should use secure storage (EncryptedSharedPreferences on Android, the desktop keystore
 * cipher on JVM). The material is read without user interaction so the server can start in the background.
 */
interface TakCertificateStore {
    /** Returns the stored material, or null when none has been generated yet (or it cannot be decrypted). */
    fun load(): TakCertificateMaterial?

    /** Persists [material], replacing any previous set. */
    fun save(material: TakCertificateMaterial)

    /** Deletes the stored material. */
    fun clear()
}
