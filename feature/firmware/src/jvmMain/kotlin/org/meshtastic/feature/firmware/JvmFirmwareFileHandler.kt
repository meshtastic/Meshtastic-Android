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

import io.ktor.client.HttpClient
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.safeCatching
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Suppress("TooManyFunctions")
@Single(binds = [FirmwareFileHandler::class])
class JvmFirmwareFileHandler(client: HttpClient) :
    BaseFirmwareFileHandler(client, File(System.getProperty("java.io.tmpdir"), "meshtastic/firmware_update")) {

    override fun openUri(uri: CommonUri): InputStream? = uri.toLocalFileOrNull()?.inputStream()

    override suspend fun getFileSize(file: FirmwareArtifact): Long =
        withContext(ioDispatcher) { file.toLocalFileOrNull()?.takeIf { it.exists() }?.length() ?: 0L }

    override suspend fun readBytes(artifact: FirmwareArtifact): ByteArray = withContext(ioDispatcher) {
        val file =
            artifact.toLocalFileOrNull() ?: throw IOException("Cannot resolve artifact to file: ${artifact.uri}")
        file.readBytes()
    }

    override suspend fun importFromUri(uri: CommonUri): FirmwareArtifact? = withContext(ioDispatcher) {
        val sourceFile = uri.toLocalFileOrNull() ?: return@withContext null
        if (!sourceFile.exists()) return@withContext null
        if (!tempDir.exists()) tempDir.mkdirs()
        val dest = File(tempDir, "ota_firmware.bin")
        sourceFile.copyTo(dest, overwrite = true)
        dest.toFirmwareArtifact()
    }

    override suspend fun getDisplayName(uri: CommonUri): String? = withContext(ioDispatcher) {
        val localFile = uri.toLocalFileOrNull()
        localFile?.name?.takeIf { it.isNotBlank() }
            ?: run {
                val scheme = runCatching { URI(uri.toString()).scheme }.getOrNull()
                if (scheme == "file") {
                    uri.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
    }

    /**
     * Fully expands [artifact] into memory, keyed by entry name.
     *
     * Shares [extractZipEntriesBounded] with the Android handler so the two cannot drift — they previously carried
     * independent copies of this loop, and only one of them got bounded.
     */
    override suspend fun extractZipEntries(artifact: FirmwareArtifact): Map<String, ByteArray> =
        withContext(ioDispatcher) {
            val file = artifact.toLocalFileOrNull() ?: throw IOException("Cannot resolve artifact: ${artifact.uri}")
            require(file.length() <= MAX_FIRMWARE_ZIP_BYTES) {
                "Firmware archive is ${file.length()} bytes, over the $MAX_FIRMWARE_ZIP_BYTES limit"
            }
            file.inputStream().use { extractZipEntriesBounded(it) }
        }

    override suspend fun copyToUri(source: FirmwareArtifact, destinationUri: CommonUri): Long =
        withContext(ioDispatcher) {
            val sourceFile = source.toLocalFileOrNull() ?: throw IOException("Cannot open source URI")
            val destinationFile = destinationUri.toLocalFileOrNull() ?: throw IOException("Cannot open destination URI")
            destinationFile.parentFile?.mkdirs()
            Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            destinationFile.length()
        }

    /**
     * Always false on desktop: there is no Storage Access Framework to classify a volume with, and the multi-pass UF2
     * maintenance flow is Android-only. Refusing is the fail-closed answer, consistent with [DesktopFirmwareUsbManager]
     * reporting the CDC unblock unsupported.
     */
    override suspend fun isRemovableDestination(destinationUri: CommonUri): Boolean = false

    override suspend fun isDestinationReadable(destinationUri: CommonUri): Boolean =
        withContext(ioDispatcher) { destinationUri.toLocalFileOrNull()?.canRead() == true }

    /**
     * Directory-based equivalents of the Android tree operations. Reachable only if a desktop maintenance flow is ever
     * built — [isRemovableDestination] refuses first today — but implemented rather than stubbed so the behaviour is
     * obvious to whoever gets there.
     */
    override suspend fun readSiblingText(treeUri: CommonUri, fileName: String): String? = withContext(ioDispatcher) {
        val dir = treeUri.toLocalFileOrNull() ?: return@withContext null
        dir.listFiles()
            ?.firstOrNull { it.name.equals(fileName, ignoreCase = true) }
            ?.let { file -> safeCatching { file.readText() }.getOrNull() }
    }

    override suspend fun createDocumentInTree(treeUri: CommonUri, fileName: String, mimeType: String): CommonUri? =
        withContext(ioDispatcher) {
            val dir = treeUri.toLocalFileOrNull() ?: return@withContext null
            safeCatching {
                dir.mkdirs()
                val target = File(dir, fileName)
                target.createNewFile()
                CommonUri.parse(target.toURI().toString())
            }
                .getOrNull()
        }
}
