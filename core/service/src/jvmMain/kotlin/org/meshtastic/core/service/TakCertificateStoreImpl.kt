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
import org.meshtastic.core.repository.TakCertificateMaterial
import org.meshtastic.core.repository.TakCertificateStore
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.crypto.SecretKey

/**
 * File-backed encrypted store for the local TAK server's TLS material on JVM/Desktop.
 *
 * The material is one AES-256-GCM `.enc` file under `$MESHTASTIC_DATA_DIR/tak_certs/` (default:
 * `~/.meshtastic/tak_certs/`); see [DesktopKeystoreCipher] for the key handling.
 */
@Single(binds = [TakCertificateStore::class])
@Suppress("TooGenericExceptionCaught")
class TakCertificateStoreImpl(dataDir: File = File(desktopDataDir())) : TakCertificateStore {

    private val storeDir: File by lazy { File(dataDir, STORE_DIR).also { it.mkdirs() } }

    private val cipher by lazy { DesktopKeystoreCipher(storeDir, KEY_ALIAS, KEYSTORE_PASSWORD) }

    private val masterKey: SecretKey? by lazy {
        try {
            cipher.loadOrCreateMasterKey()
        } catch (e: Exception) {
            Logger.e(e) { "TAK: Failed to initialize desktop keystore for certificate store" }
            null
        }
    }

    private val materialFile: File
        get() = File(storeDir, MATERIAL_FILE)

    @Suppress("ReturnCount")
    override fun load(): TakCertificateMaterial? {
        val key = masterKey ?: return null
        val file = materialFile
        if (!file.exists()) return null
        return try {
            deserialize(cipher.decrypt(key, file.readBytes()))
        } catch (e: Exception) {
            Logger.e(e) { "TAK: Failed to read stored certificate material" }
            null
        }
    }

    /**
     * Writes through a temp file and an atomic rename, so an interrupted save leaves the previous identity intact
     * rather than a truncated file that would be read as "no material" and silently replaced on next start.
     */
    override fun save(material: TakCertificateMaterial) {
        val key = masterKey ?: error("TAK: Cannot save certificate material - keystore unavailable")
        val encrypted = cipher.encrypt(key, serialize(material))
        val temp = Files.createTempFile(storeDir.toPath(), "$MATERIAL_FILE.", ".tmp")
        try {
            Files.write(temp, encrypted)
            Files.move(temp, materialFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override fun clear() {
        val file = materialFile
        if (file.exists() && !file.delete()) {
            Logger.w { "TAK: Certificate material file was not deleted" }
        }
    }

    // Format v1: [int version][utf password][int len][server.p12][int len][client.p12][int len][truststore.p12]
    private fun serialize(material: TakCertificateMaterial): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeInt(FORMAT_VERSION)
            data.writeUTF(material.password)
            listOf(material.serverP12, material.clientP12, material.truststoreP12).forEach { blob ->
                data.writeInt(blob.size)
                data.write(blob)
            }
        }
        return out.toByteArray()
    }

    private fun deserialize(plaintext: ByteArray): TakCertificateMaterial =
        DataInputStream(plaintext.inputStream()).use { data ->
            val version = data.readInt()
            check(version == FORMAT_VERSION) { "Unsupported TAK certificate store format $version" }
            val password = data.readUTF()
            val blobs = List(BLOB_COUNT) { ByteArray(data.readInt()).also(data::readFully) }
            TakCertificateMaterial(
                serverP12 = blobs[0],
                clientP12 = blobs[1],
                truststoreP12 = blobs[2],
                password = password,
            )
        }

    private companion object {
        private const val STORE_DIR = "tak_certs"
        private const val MATERIAL_FILE = "material.enc"
        private const val KEY_ALIAS = "tak_certs_master"

        // Intentional: this mirrors the documented desktop threat model for at-rest protection only.
        private val KEYSTORE_PASSWORD = "meshtastic-tak-certs".toCharArray()
        private const val FORMAT_VERSION = 1
        private const val BLOB_COUNT = 3
    }
}
