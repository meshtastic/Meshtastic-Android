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

import android.content.Context
import co.touchlab.kermit.Logger
import com.eygraber.uri.toAndroidUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.model.DeviceHardware
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val DOWNLOAD_BUFFER_SIZE = 8192

/**
 * Helper class to handle file operations related to firmware updates, such as downloading, copying from URI, and
 * extracting specific files from Zip archives.
 */
@Single
@Suppress("TooManyFunctions")
class AndroidFirmwareFileHandler(private val context: Context, private val client: HttpClient) : FirmwareFileHandler {
    private val tempDir = File(context.cacheDir, "firmware_update")

    override fun cleanupAllTemporaryFiles() {
        runCatching {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
        }
            .onFailure { e -> Logger.w(e) { "Failed to cleanup temp directory" } }
    }

    override suspend fun checkUrlExists(url: String): Boolean = withContext(ioDispatcher) {
        try {
            client.head(url).status.isSuccess()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Failed to check URL existence: $url" }
            false
        }
    }

    override suspend fun fetchText(url: String): String? = withContext(ioDispatcher) {
        try {
            val response = client.get(url)
            if (response.status.isSuccess()) response.bodyAsText() else null
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
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.w(e) { "Download failed for $url" }
                    return@withContext null
                }

            if (!response.status.isSuccess()) {
                Logger.w { "Download failed: ${response.status.value} for $url" }
                return@withContext null
            }

            val body = response.bodyAsChannel()
            val contentLength = response.contentLength() ?: -1L

            if (!tempDir.exists()) tempDir.mkdirs()

            val targetFile = java.io.File(tempDir, fileName)

            body.toInputStream().use { input ->
                java.io.FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!isActive) throw CancellationException("Download cancelled")

                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (contentLength > 0) {
                            onProgress(totalBytesRead.toFloat() / contentLength)
                        }
                    }
                    if (contentLength != -1L && totalBytesRead != contentLength) {
                        throw IOException("Incomplete download: expected $contentLength bytes, got $totalBytesRead")
                    }
                }
            }
            targetFile.toFirmwareArtifact()
        }

    override suspend fun extractFirmwareFromZip(
        zipFile: FirmwareArtifact,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = withContext(ioDispatcher) {
        val localZipFile = zipFile.toLocalFileOrNull() ?: return@withContext null
        val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
        if (target.isEmpty() && preferredFilename == null) return@withContext null

        val targetLowerCase = target.lowercase()
        val preferredFilenameLower = preferredFilename?.lowercase()
        val matchingEntries = mutableListOf<Pair<ZipEntry, File>>()

        if (!tempDir.exists()) tempDir.mkdirs()

        ZipInputStream(localZipFile.inputStream()).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                // File(name).name strips directory components, mitigating ZipSlip attacks
                val entryFileName = File(name).name

                val isMatch =
                    if (preferredFilenameLower != null) {
                        entryFileName == preferredFilenameLower
                    } else {
                        !entry.isDirectory && isValidFirmwareFile(name, targetLowerCase, fileExtension)
                    }

                if (isMatch) {
                    val outFile = File(tempDir, entryFileName)
                    FileOutputStream(outFile).use { output -> zipInput.copyTo(output) }
                    matchingEntries.add(entry to outFile)

                    if (preferredFilenameLower != null) {
                        return@withContext outFile.toFirmwareArtifact()
                    }
                }
                entry = zipInput.nextEntry
            }
        }
        matchingEntries.minByOrNull { it.first.name.length }?.second?.toFirmwareArtifact()
    }

    override suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = withContext(ioDispatcher) {
        val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
        if (target.isEmpty() && preferredFilename == null) return@withContext null

        val targetLowerCase = target.lowercase()
        val preferredFilenameLower = preferredFilename?.lowercase()
        val matchingEntries = mutableListOf<Pair<ZipEntry, File>>()

        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            val platformUri = uri.toAndroidUri()
            val inputStream = context.contentResolver.openInputStream(platformUri) ?: return@withContext null
            ZipInputStream(inputStream).use { zipInput ->
                var entry = zipInput.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    // File(name).name strips directory components, mitigating ZipSlip attacks
                    val entryFileName = File(name).name

                    val isMatch =
                        if (preferredFilenameLower != null) {
                            entryFileName == preferredFilenameLower
                        } else {
                            !entry.isDirectory && isValidFirmwareFile(name, targetLowerCase, fileExtension)
                        }

                    if (isMatch) {
                        val outFile = File(tempDir, entryFileName)
                        FileOutputStream(outFile).use { output -> zipInput.copyTo(output) }
                        matchingEntries.add(entry to outFile)

                        if (preferredFilenameLower != null) {
                            return@withContext outFile.toFirmwareArtifact()
                        }
                    }
                    entry = zipInput.nextEntry
                }
            }
        } catch (e: IOException) {
            Logger.w(e) { "Failed to extract firmware from URI" }
            return@withContext null
        }
        matchingEntries.minByOrNull { it.first.name.length }?.second?.toFirmwareArtifact()
    }

    override suspend fun getFileSize(file: FirmwareArtifact): Long = withContext(ioDispatcher) {
        file.toLocalFileOrNull()?.takeIf { it.exists() }?.length()
            ?: context.contentResolver.openAssetFileDescriptor(file.uri.toAndroidUri(), "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L }
            }
            ?: 0L
    }

    override suspend fun deleteFile(file: FirmwareArtifact) = withContext(ioDispatcher) {
        if (!file.isTemporary) return@withContext
        val localFile = file.toLocalFileOrNull() ?: return@withContext
        if (localFile.exists()) localFile.delete()
    }

    override suspend fun readBytes(artifact: FirmwareArtifact): ByteArray = withContext(ioDispatcher) {
        val localFile = artifact.toLocalFileOrNull()
        if (localFile != null && localFile.exists()) {
            localFile.readBytes()
        } else {
            context.contentResolver.openInputStream(artifact.uri.toAndroidUri())?.use { it.readBytes() }
                ?: throw IOException("Cannot open artifact: ${artifact.uri}")
        }
    }

    override suspend fun importFromUri(uri: CommonUri): FirmwareArtifact? = withContext(ioDispatcher) {
        val inputStream = context.contentResolver.openInputStream(uri.toAndroidUri()) ?: return@withContext null
        val tempFile = File(context.cacheDir, "firmware_update/ota_firmware.bin")
        tempFile.parentFile?.mkdirs()
        inputStream.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
        tempFile.toFirmwareArtifact()
    }

    override suspend fun extractZipEntries(artifact: FirmwareArtifact): Map<String, ByteArray> =
        withContext(ioDispatcher) {
            val entries = mutableMapOf<String, ByteArray>()
            val bytes = readBytes(artifact)
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            entries
        }

    private fun isValidFirmwareFile(filename: String, target: String, fileExtension: String): Boolean =
        org.meshtastic.feature.firmware.isValidFirmwareFile(filename, target, fileExtension)

    override suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long =
        withContext(ioDispatcher) {
            val inputStream =
                source.toLocalFileOrNull()?.inputStream()
                    ?: context.contentResolver.openInputStream(source.uri.toAndroidUri())
                    ?: throw IOException("Cannot open source URI")
            val outputStream =
                context.contentResolver.openOutputStream(destinationUri.toAndroidUri())
                    ?: throw IOException("Cannot open content URI for writing")

            inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
        }

    private fun File.toFirmwareArtifact(): FirmwareArtifact =
        FirmwareArtifact(uri = CommonUri.parse(toURI().toString()), fileName = name, isTemporary = true)

    private fun FirmwareArtifact.toLocalFileOrNull(): File? {
        val uriString = uri.toString()
        return if (uriString.startsWith("file:")) {
            runCatching { File(URI(uriString)) }.getOrNull()
        } else {
            null
        }
    }
}
