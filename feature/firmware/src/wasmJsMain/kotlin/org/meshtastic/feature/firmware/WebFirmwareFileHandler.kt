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
package org.meshtastic.feature.firmware

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.ui.util.readWebFile
import org.meshtastic.core.ui.util.writeWebFile

/**
 * [FirmwareFileHandler] for the browser. A page has no file system, so downloads and extracted images live in memory
 * under `web-firmware://` URIs for the session, and user files come and go through core:ui's web file pickers. Release
 * downloads need release.meshtastic.org's CORS grant, which names the origins it serves.
 *
 * No volume can be vetted or listed, so the multi-pass UF2 maintenance calls answer as refusals, as on desktop.
 */
@Suppress("TooManyFunctions")
@Single(binds = [FirmwareFileHandler::class])
class WebFirmwareFileHandler(private val client: HttpClient) : FirmwareFileHandler {
    // Wasm runs on one thread, so the plain map needs no lock.
    private val files = mutableMapOf<String, ByteArray>()
    private var nextId = 0

    private fun store(bytes: ByteArray, fileName: String): FirmwareArtifact {
        val uri = CommonUri.parse("$MEMORY_SCHEME://${nextId++}/${CommonUri.encode(fileName)}")
        files[uri.toString()] = bytes
        return FirmwareArtifact(uri = uri, fileName = fileName, isTemporary = true)
    }

    private suspend fun bytesOf(uri: CommonUri): ByteArray? = files[uri.toString()] ?: readWebFile(uri)

    override fun cleanupAllTemporaryFiles() = files.clear()

    override suspend fun deleteFile(file: FirmwareArtifact) {
        if (file.isTemporary) files.remove(file.uri.toString())
    }

    override suspend fun checkUrlExists(url: String): Boolean = try {
        client.head(url).status.isSuccess()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Logger.w(e) { "Failed to check URL existence: $url" }
        false
    }

    override suspend fun fetchText(url: String): String? = try {
        val response = client.get(url)
        if (response.status.isSuccess()) response.bodyAsText() else null
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Logger.w(e) { "Failed to fetch text from: $url" }
        null
    }

    override suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): FirmwareArtifact? =
        fetchBytes(url, onProgress)?.let { store(it, fileName.substringAfterLast('/')) }

    private suspend fun fetchBytes(url: String, onProgress: (Float) -> Unit): ByteArray? = try {
        val response =
            client.get(url) {
                onDownload { received, total ->
                    if (total != null && total > 0) onProgress(received.toFloat() / total)
                }
            }
        if (response.status.isSuccess()) {
            response.bodyAsBytes()
        } else {
            Logger.w { "Download failed: ${response.status.value} for $url" }
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // A host whose CORS policy does not name this origin fails here, as a network error.
        Logger.w(e) { "Download failed for $url" }
        null
    }

    override suspend fun getFileSize(file: FirmwareArtifact): Long = bytesOf(file.uri)?.size?.toLong() ?: 0L

    override suspend fun readBytes(artifact: FirmwareArtifact): ByteArray =
        bytesOf(artifact.uri) ?: throw IllegalStateException("Cannot read firmware artifact: ${artifact.uri}")

    override suspend fun importFromUri(uri: CommonUri): FirmwareArtifact? =
        readWebFile(uri)?.let { store(it, displayName(uri) ?: "ota_firmware.bin") }

    override suspend fun getDisplayName(uri: CommonUri): String? = displayName(uri)

    private fun displayName(uri: CommonUri): String? = uri.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }

    override suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long {
        val bytes = readBytes(source)
        check(writeWebFile(destinationUri, bytes)) { "The browser refused the write to $destinationUri" }
        return bytes.size.toLong()
    }

    override suspend fun isRemovableDestination(destinationUri: CommonUri): Boolean = false

    override suspend fun readSiblingText(treeUri: CommonUri, fileName: String): String? = null

    override suspend fun createDocumentInTree(treeUri: CommonUri, fileName: String, mimeType: String): CommonUri? = null

    // A save handle cannot be read back, so the inverted UF2 success check has nothing to go on.
    override suspend fun isDestinationReadable(destinationUri: CommonUri): Boolean = false

    override suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = bytesOf(uri)?.let { extractMatching(it, hardware, fileExtension, preferredFilename) }

    override suspend fun extractFirmwareFromZip(
        zipFile: FirmwareArtifact,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = bytesOf(zipFile.uri)?.let { extractMatching(it, hardware, fileExtension, preferredFilename) }

    override suspend fun extractZipEntries(artifact: FirmwareArtifact): Map<String, ByteArray> {
        val archive = WebZipArchive(readBytes(artifact))
        var remaining = MAX_FIRMWARE_UNCOMPRESSED_BYTES
        return archive.entries
            .filterNot { it.name.endsWith('/') }
            .associate { entry ->
                val bytes = archive.read(entry, remaining)
                remaining -= bytes.size
                entry.name to bytes
            }
    }

    /**
     * The entry the JVM handlers would pick: exactly [preferredFilename] when given, otherwise the shortest matching
     * name. Null on no match, a corrupt archive or one past the limits, so retrieval moves to its fallbacks.
     */
    private suspend fun extractMatching(
        zip: ByteArray,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? {
        val target = hardware.effectiveTarget
        if (target.isEmpty() && preferredFilename == null) return null
        return try {
            val archive = WebZipArchive(zip)
            val matches =
                archive.entries.filter {
                    isFirmwareEntryMatch(it.name, it.name.endsWith('/'), target, fileExtension, preferredFilename)
                }
            val chosen =
                (if (preferredFilename != null) matches.firstOrNull() else matches.minByOrNull { it.name.length })
            chosen?.let { store(archive.read(it), it.name.lowercase().substringAfterLast('/')) }
        } catch (e: IllegalArgumentException) {
            Logger.w(e) { "Firmware archive refused" }
            null
        }
    }

    private companion object {
        const val MEMORY_SCHEME = "web-firmware"
    }
}
