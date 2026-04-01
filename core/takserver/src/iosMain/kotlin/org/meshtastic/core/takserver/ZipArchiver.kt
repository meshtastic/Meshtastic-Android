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
package org.meshtastic.core.takserver

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorReadingForUploading
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual object ZipArchiver {
    actual fun createZip(entries: Map<String, ByteArray>): ByteArray {
        val fileManager = NSFileManager.defaultManager
        val tempDir = NSTemporaryDirectory() + "tak_data_package/"

        // Clean up and create temp directory
        fileManager.removeItemAtPath(tempDir, null)
        fileManager.createDirectoryAtPath(tempDir, withIntermediateDirectories = true, attributes = null, error = null)

        try {
            // Write each entry as a file in the temp directory
            for ((name, data) in entries) {
                val fileUrl = NSURL.fileURLWithPath(tempDir + name)
                val nsData =
                    data.usePinned { pinned ->
                        NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
                    }
                nsData.writeToURL(fileUrl, atomically = true)
            }

            // Use NSFileCoordinator to create a zip from the directory
            val dirUrl = NSURL.fileURLWithPath(tempDir)
            var zipData: ByteArray? = null

            val coordinator = NSFileCoordinator()
            coordinator.coordinateReadingItemAtURL(
                dirUrl,
                options = NSFileCoordinatorReadingForUploading,
                error = null,
            ) { zipUrl ->
                if (zipUrl != null) {
                    val data = NSData.dataWithContentsOfURL(zipUrl)
                    if (data != null) {
                        zipData =
                            ByteArray(data.length.toInt()).also { bytes ->
                                bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
                            }
                    }
                }
            }

            return zipData ?: error("Failed to create zip archive")
        } finally {
            fileManager.removeItemAtPath(tempDir, null)
        }
    }
}
