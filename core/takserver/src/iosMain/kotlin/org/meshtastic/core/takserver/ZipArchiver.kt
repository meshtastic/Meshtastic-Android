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
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorReadingForUploading
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal actual object ZipArchiver {
    actual fun createZip(entries: Map<String, ByteArray>): ByteArray {
        val fileManager = NSFileManager.defaultManager
        val tempDir = NSTemporaryDirectory() + "tak_data_package/"

        // Clean up and create temp directory, propagating any NSFileManager errors
        fileManager.removeItemAtPath(tempDir, null)
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val created =
                fileManager.createDirectoryAtPath(
                    path = tempDir,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = errorPtr.ptr,
                )
            if (!created) {
                val nsError = errorPtr.value
                error("Failed to create temp directory: ${nsError?.localizedDescription ?: "unknown error"}")
            }
        }

        try {
            // Write each entry as a file in the temp directory
            for ((name, data) in entries) {
                val fileUrl = NSURL.fileURLWithPath(tempDir + name)
                val nsData =
                    data.usePinned { pinned ->
                        NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
                    }
                val written = nsData.writeToURL(fileUrl, atomically = true)
                if (!written) {
                    error("Failed to write entry '$name' to temp directory")
                }
            }

            // Use NSFileCoordinator to create a zip from the directory
            val dirUrl = NSURL.fileURLWithPath(tempDir)
            var zipData: ByteArray? = null
            var coordinatorError: String? = null

            val coordinator = NSFileCoordinator()
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                coordinator.coordinateReadingItemAtURL(
                    url = dirUrl,
                    options = NSFileCoordinatorReadingForUploading,
                    error = errorPtr.ptr,
                ) { zipUrl ->
                    if (zipUrl != null) {
                        val data = NSData.dataWithContentsOfURL(zipUrl)
                        if (data != null) {
                            zipData =
                                ByteArray(data.length.toInt()).also { bytes ->
                                    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
                                }
                        } else {
                            coordinatorError = "NSData.dataWithContentsOfURL returned null for $zipUrl"
                        }
                    } else {
                        coordinatorError = "NSFileCoordinator provided null zip URL"
                    }
                }
                val nsError = errorPtr.value
                if (nsError != null) {
                    error("NSFileCoordinator error: ${nsError.localizedDescription}")
                }
            }
            if (coordinatorError != null) error(coordinatorError)

            return zipData ?: error("Failed to create zip archive")
        } finally {
            fileManager.removeItemAtPath(tempDir, null)
        }
    }
}
