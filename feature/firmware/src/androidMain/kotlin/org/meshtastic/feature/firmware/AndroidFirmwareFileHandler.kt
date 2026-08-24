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
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import co.touchlab.kermit.Logger
import com.eygraber.uri.toAndroidUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.DeviceHardware
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private const val DOWNLOAD_BUFFER_SIZE = 8192

/** SAF provider for physical volumes; the only one a UF2 bootloader drive can appear under. */
private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

/** SAF volume id for internal shared storage — never a removable drive. */
private const val PRIMARY_VOLUME_ID = "primary"

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

            if (!tempDir.exists()) tempDir.mkdirs()
            val targetFile = java.io.File(tempDir, fileName)
            downloadResponseToFile(response, targetFile, onProgress)
            targetFile.toFirmwareArtifact()
        }

    override suspend fun extractFirmwareFromZip(
        zipFile: FirmwareArtifact,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = withContext(ioDispatcher) {
        val localZipFile = zipFile.toLocalFileOrNull() ?: return@withContext null
        val target = hardware.effectiveTarget
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
        // Prefer the shortest matching entry name — official release bundles contain one
        // matching firmware per target; the heuristic picks the canonical name if multiple match.
        matchingEntries.minByOrNull { it.first.name.length }?.second?.toFirmwareArtifact()
    }

    override suspend fun extractFirmware(
        uri: CommonUri,
        hardware: DeviceHardware,
        fileExtension: String,
        preferredFilename: String?,
    ): FirmwareArtifact? = withContext(ioDispatcher) {
        val target = hardware.effectiveTarget
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

    override suspend fun getDisplayName(uri: CommonUri): String? = withContext(ioDispatcher) {
        val platformUri = uri.toAndroidUri()
        if (platformUri.scheme == "content") {
            // query() can throw SecurityException (revoked permission) or IllegalArgumentException
            // (malformed URI) even after the scheme guard; fall through to the file-scheme branch on failure.
            runCatching {
                context.contentResolver.query(
                    platformUri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )
            }
                .onFailure { e -> Logger.w(e) { "Failed to query display name from content provider" } }
                .getOrNull()
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        cursor
                            .getString(nameIndex)
                            ?.takeIf { it.isNotBlank() }
                            ?.let {
                                return@withContext it
                            }
                    }
                }
        }
        if (platformUri.scheme == "file") uri.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() } else null
    }

    /**
     * Fully expands [artifact] into memory, keyed by entry name.
     *
     * Streams from the artifact rather than buffering it whole, and delegates the bounds to [extractZipEntriesBounded]
     * so this and the desktop handler cannot drift apart. The [getFileSize] check is only a cheap early rejection — it
     * returns 0 for a provider that declines to report a length, so the inflation bound inside the extractor is what
     * actually protects the heap.
     */
    override suspend fun extractZipEntries(artifact: FirmwareArtifact): Map<String, ByteArray> =
        withContext(ioDispatcher) {
            val declaredSize = getFileSize(artifact)
            require(declaredSize <= MAX_FIRMWARE_ZIP_BYTES) {
                "Firmware archive is $declaredSize bytes, over the $MAX_FIRMWARE_ZIP_BYTES limit"
            }
            openArtifactStream(artifact).use { extractZipEntriesBounded(it) }
        }

    /** Opens [artifact] for streaming, preferring a local file and falling back to the content resolver. */
    private fun openArtifactStream(artifact: FirmwareArtifact): InputStream {
        val localFile = artifact.toLocalFileOrNull()
        if (localFile != null && localFile.exists()) return localFile.inputStream()
        return context.contentResolver.openInputStream(artifact.uri.toAndroidUri())
            ?: throw IOException("Cannot open artifact: ${artifact.uri}")
    }

    private fun isValidFirmwareFile(filename: String, target: String, fileExtension: String): Boolean =
        org.meshtastic.feature.firmware.isValidFirmwareFile(filename, target, fileExtension)

    /**
     * Accepts only a Storage Access Framework document on a non-primary external volume.
     *
     * `com.android.externalstorage.documents` document ids are `<volumeId>:<path>`, where internal shared storage is
     * always `primary`. A mounted USB mass-storage volume — which is what a UF2 bootloader drive is — gets its own
     * volume id. Every other provider (Downloads, Drive, MediaStore) is therefore rejected, which is the point: those
     * are exactly where a mis-tap sends the image.
     */
    override suspend fun isRemovableDestination(destinationUri: CommonUri): Boolean = withContext(ioDispatcher) {
        safeCatching {
            val androidUri = destinationUri.toAndroidUri()
            if (androidUri.authority != EXTERNAL_STORAGE_AUTHORITY) return@safeCatching false
            // Accepts either a tree URI (the maintenance flow picks the volume) or a single document URI.
            val documentId =
                runCatching { DocumentsContract.getTreeDocumentId(androidUri) }.getOrNull()
                    ?: DocumentsContract.getDocumentId(androidUri)
            val volumeId = documentId.substringBefore(':', missingDelimiterValue = "")
            volumeId.isNotBlank() && !volumeId.equals(PRIMARY_VOLUME_ID, ignoreCase = true)
        }
            .onFailure { Logger.w { "Could not classify the selected destination volume" } }
            .getOrDefault(false)
    }

    override suspend fun isDestinationReadable(destinationUri: CommonUri): Boolean = withContext(ioDispatcher) {
        safeCatching {
            context.contentResolver.openInputStream(destinationUri.toAndroidUri())?.use { true } == true
        }
            .getOrDefault(false)
    }

    override suspend fun readSiblingText(treeUri: CommonUri, fileName: String): String? = withContext(ioDispatcher) {
        safeCatching {
            val tree = treeUri.toAndroidUri()
            val treeDocumentId = DocumentsContract.getTreeDocumentId(tree)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocumentId)
            val documentId =
                context.contentResolver
                    .query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        ),
                        null,
                        null,
                        null,
                    )
                    ?.use { cursor ->
                        var found: String? = null
                        while (cursor.moveToNext()) {
                            if (cursor.getString(1).equals(fileName, ignoreCase = true)) {
                                found = cursor.getString(0)
                                break
                            }
                        }
                        found
                    } ?: return@safeCatching null

            val documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
            context.contentResolver.openInputStream(documentUri)?.use { it.readBytes().decodeToString() }
        }
            .onFailure { Logger.w { "Could not read $fileName from the selected volume" } }
            .getOrNull()
    }

    override suspend fun createDocumentInTree(treeUri: CommonUri, fileName: String, mimeType: String): CommonUri? =
        withContext(ioDispatcher) {
            safeCatching {
                val tree = treeUri.toAndroidUri()
                val parent =
                    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                DocumentsContract.createDocument(context.contentResolver, parent, mimeType, fileName)?.let {
                    CommonUri.parse(it.toString())
                }
            }
                .onFailure { Logger.w { "Could not create $fileName on the selected volume" } }
                .getOrNull()
        }

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
