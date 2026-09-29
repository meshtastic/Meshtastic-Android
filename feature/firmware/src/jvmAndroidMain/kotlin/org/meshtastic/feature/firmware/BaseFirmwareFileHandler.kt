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
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.model.DeviceHardware
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * The [FirmwareFileHandler] work the Android and desktop handlers share: HTTP fetches into [tempDir], and firmware
 * extraction through the bounded [extractFirmwareEntry]. Subclasses say how a platform URI is opened.
 */
abstract class BaseFirmwareFileHandler(private val client: HttpClient, protected val tempDir: File) :
    FirmwareFileHandler {

    /** Opens [uri] for reading, or returns null when the platform cannot resolve it. */
    protected abstract fun openUri(uri: CommonUri): InputStream?

    override fun cleanupAllTemporaryFiles() {
        runCatching {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
        }
            .onFailure { e -> Logger.w(e) { "Failed to cleanup temp directory" } }
    }

    override suspend fun deleteFile(file: FirmwareArtifact) = withContext(ioDispatcher) {
        if (!file.isTemporary) return@withContext
        val localFile = file.toLocalFileOrNull() ?: return@withContext
        if (localFile.exists()) localFile.delete()
    }

    override suspend fun checkUrlExists(url: String): Boolean = withContext(ioDispatcher) {
        try {
            client.head(url).status.isSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Failed to check URL existence: $url" }
            false
        }
    }

    override suspend fun fetchText(url: String): String? = withContext(ioDispatcher) {
        try {
            val response = client.get(url)
            if (response.status.isSuccess()) response.bodyAsText() else null
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Failed to fetch text from: $url" }
            null
        }
    }

    override suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): FirmwareArtifact? =
        withContext(ioDispatcher) {
            val response =
                try {
                    client.get(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.w(e) { "Download failed for $url" }
                    return@withContext null
                }

            if (!response.status.isSuccess()) {
                Logger.w { "Download failed: ${response.status.value} for $url" }
                return@withContext null
            }

            if (!tempDir.exists()) tempDir.mkdirs()
            val targetFile = File(tempDir, fileName)
            downloadResponseToFile(response, targetFile, onProgress)
            targetFile.toFirmwareArtifact()
        }

    override suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = withContext(ioDispatcher) {
        if (hardware.effectiveTarget.isEmpty() && preferredFilename == null) return@withContext null
        extractFirmwareEntryOrNull(tempDir, hardware.effectiveTarget, fileExtension, preferredFilename) {
            openUri(uri)
        }
            ?.toFirmwareArtifact()
    }

    override suspend fun extractFirmwareFromZip(
        zipFile: FirmwareArtifact,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = withContext(ioDispatcher) {
        val localZipFile = zipFile.toLocalFileOrNull() ?: return@withContext null
        if (hardware.effectiveTarget.isEmpty() && preferredFilename == null) return@withContext null
        extractFirmwareEntryOrNull(tempDir, hardware.effectiveTarget, fileExtension, preferredFilename) {
            localZipFile.inputStream()
        }
            ?.toFirmwareArtifact()
    }

    protected fun File.toFirmwareArtifact(): FirmwareArtifact =
        FirmwareArtifact(uri = CommonUri.parse(toURI().toString()), fileName = name, isTemporary = true)

    protected fun FirmwareArtifact.toLocalFileOrNull(): File? = uri.toLocalFileOrNull()

    protected fun CommonUri.toLocalFileOrNull(): File? = runCatching {
        val parsedUri = URI(toString())
        if (parsedUri.scheme == "file") File(parsedUri) else null
    }
        .getOrNull()
}

/** A corrupt archive or one past [extractFirmwareEntry]'s limits yields null, so retrieval moves to its fallbacks. */
private fun extractFirmwareEntryOrNull(
    outputDir: File,
    target: String,
    fileExtension: String,
    preferredFilename: String?,
    open: () -> InputStream?,
): File? = try {
    open()?.let { extractFirmwareEntry(it, outputDir, target, fileExtension, preferredFilename) }
} catch (e: IOException) {
    Logger.w(e) { "Failed to extract firmware" }
    null
} catch (e: IllegalArgumentException) {
    Logger.w(e) { "Firmware archive refused" }
    null
}
