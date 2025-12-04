/*
 * Copyright (c) 2025 Meshtastic LLC
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
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.meshtastic.core.model.DeviceHardware
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject

private const val DOWNLOAD_BUFFER_SIZE = 8192

/**
 * Helper class to handle file operations related to firmware updates, such as downloading, copying from URI, and
 * extracting specific files from Zip archives.
 */
class FirmwareFileHandler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    private val tempDir = File(context.cacheDir, "firmware_update")

    fun cleanupAllTemporaryFiles() {
        runCatching {
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
        }
            .onFailure { e -> Timber.w(e, "Failed to cleanup temp directory") }
    }

    suspend fun checkUrlExists(url: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).head().build()
        try {
            client.newCall(request).execute().use { response -> response.isSuccessful }
        } catch (e: IOException) {
            Timber.w(e, "Failed to check URL existence: $url")
            false
        }
    }

    suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response =
                try {
                    client.newCall(request).execute()
                } catch (e: IOException) {
                    Timber.w(e, "Download failed for $url")
                    return@withContext null
                }

            if (!response.isSuccessful) {
                Timber.w("Download failed: ${response.code} for $url")
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()

            if (!tempDir.exists()) tempDir.mkdirs()

            val targetFile = File(tempDir, fileName)

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
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
            targetFile
        }

    suspend fun extractFirmware(zipFile: File, hardware: DeviceHardware, fileExtension: String): File? =
        withContext(Dispatchers.IO) {
            val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
            if (target.isEmpty()) return@withContext null

            val targetLowerCase = target.lowercase()
            val matchingEntries = mutableListOf<Pair<ZipEntry, File>>()

            if (!tempDir.exists()) tempDir.mkdirs()

            ZipInputStream(zipFile.inputStream()).use { zipInput ->
                var entry = zipInput.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (!entry.isDirectory && isValidFirmwareFile(name, targetLowerCase, fileExtension)) {
                        val outFile = File(tempDir, File(name).name)
                        FileOutputStream(outFile).use { output -> zipInput.copyTo(output) }
                        matchingEntries.add(entry to outFile)
                    }
                    entry = zipInput.nextEntry
                }
            }
            matchingEntries.minByOrNull { it.first.name.length }?.second
        }

    suspend fun extractFirmware(uri: Uri, hardware: DeviceHardware, fileExtension: String): File? =
        withContext(Dispatchers.IO) {
            val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
            if (target.isEmpty()) return@withContext null

            val targetLowerCase = target.lowercase()
            val matchingEntries = mutableListOf<Pair<ZipEntry, File>>()

            if (!tempDir.exists()) tempDir.mkdirs()

            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                ZipInputStream(inputStream).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        if (!entry.isDirectory && isValidFirmwareFile(name, targetLowerCase, fileExtension)) {
                            val outFile = File(tempDir, File(name).name)
                            FileOutputStream(outFile).use { output -> zipInput.copyTo(output) }
                            matchingEntries.add(entry to outFile)
                        }
                        entry = zipInput.nextEntry
                    }
                }
            } catch (e: IOException) {
                Timber.w(e, "Failed to extract firmware from URI")
                return@withContext null
            }
            matchingEntries.minByOrNull { it.first.name.length }?.second
        }

    private fun isValidFirmwareFile(filename: String, target: String, fileExtension: String): Boolean {
        val regex = Regex(".*[\\-_]${Regex.escape(target)}[\\-_\\.].*")
        return filename.endsWith(fileExtension) &&
            filename.contains(target) &&
            (regex.matches(filename) || filename.startsWith("$target-") || filename.startsWith("$target."))
    }

    suspend fun copyFileToUri(sourceFile: File, destinationUri: Uri) = withContext(Dispatchers.IO) {
        val inputStream = FileInputStream(sourceFile)
        val outputStream =
            context.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("Cannot open content URI for writing")

        inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
    }

    suspend fun copyUriToUri(sourceUri: Uri, destinationUri: Uri) = withContext(Dispatchers.IO) {
        val inputStream =
            context.contentResolver.openInputStream(sourceUri) ?: throw IOException("Cannot open source URI")
        val outputStream =
            context.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("Cannot open destination URI")

        inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
    }
}
