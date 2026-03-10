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
package org.meshtastic.feature.firmware

import android.content.Context
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.toPlatformUri
import org.meshtastic.core.model.DeviceHardware
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val DOWNLOAD_BUFFER_SIZE = 8192

/**
 * Helper class to handle file operations related to firmware updates, such as downloading, copying from URI, and
 * extracting specific files from Zip archives.
 */
@Single
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

    override suspend fun checkUrlExists(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            client.head(url).status.isSuccess()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Failed to check URL existence: $url" }
            false
        }
    }

    override suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): String? =
        withContext(Dispatchers.IO) {
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
            targetFile.absolutePath
        }

    override suspend fun extractFirmwareFromZip(
        zipFilePath: String,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): String? = withContext(Dispatchers.IO) {
        val zipFile = java.io.File(zipFilePath)
        val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
        if (target.isEmpty() && preferredFilename == null) return@withContext null

        val targetLowerCase = target.lowercase()
        val preferredFilenameLower = preferredFilename?.lowercase()
        val matchingEntries = mutableListOf<Pair<ZipEntry, File>>()

        if (!tempDir.exists()) tempDir.mkdirs()

        ZipInputStream(zipFile.inputStream()).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
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
                        return@withContext outFile.absolutePath
                    }
                }
                entry = zipInput.nextEntry
            }
        }
        matchingEntries.minByOrNull { it.first.name.length }?.second?.absolutePath
    }

    override suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): String? = withContext(Dispatchers.IO) {
        val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
        if (target.isEmpty() && preferredFilename == null) return@withContext null

        val targetLowerCase = target.lowercase()
        val preferredFilenameLower = preferredFilename?.lowercase()
        val matchingEntries = mutableListOf<Pair<ZipEntry, File>>()

        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            val platformUri = uri.toPlatformUri() as android.net.Uri
            val inputStream = context.contentResolver.openInputStream(platformUri) ?: return@withContext null
            ZipInputStream(inputStream).use { zipInput ->
                var entry = zipInput.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
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
                            return@withContext outFile.absolutePath
                        }
                    }
                    entry = zipInput.nextEntry
                }
            }
        } catch (e: IOException) {
            Logger.w(e) { "Failed to extract firmware from URI" }
            return@withContext null
        }
        matchingEntries.minByOrNull { it.first.name.length }?.second?.absolutePath
    }

    override suspend fun getFileSize(path: String): Long = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) file.length() else 0L
    }

    override suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) file.delete()
    }

    private fun isValidFirmwareFile(filename: String, target: String, fileExtension: String): Boolean {
        val regex = Regex(".*[\\-_]${Regex.escape(target)}[\\-_\\.].*")
        return filename.endsWith(fileExtension) &&
            filename.contains(target) &&
            (regex.matches(filename) || filename.startsWith("$target-") || filename.startsWith("$target."))
    }

    override suspend fun copyFileToUri(sourcePath: String, destinationUri: CommonUri): Long =
        withContext(Dispatchers.IO) {
            val inputStream = java.io.FileInputStream(java.io.File(sourcePath))
            val outputStream =
                context.contentResolver.openOutputStream(destinationUri.toPlatformUri() as android.net.Uri)
                    ?: throw IOException("Cannot open content URI for writing")

            inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
        }

    override suspend fun copyUriToUri(sourceUri: CommonUri, destinationUri: CommonUri): Long =
        withContext(Dispatchers.IO) {
            val inputStream =
                context.contentResolver.openInputStream(sourceUri.toPlatformUri() as android.net.Uri)
                    ?: throw IOException("Cannot open source URI")
            val outputStream =
                context.contentResolver.openOutputStream(destinationUri.toPlatformUri() as android.net.Uri)
                    ?: throw IOException("Cannot open destination URI")

            inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
        }
}
