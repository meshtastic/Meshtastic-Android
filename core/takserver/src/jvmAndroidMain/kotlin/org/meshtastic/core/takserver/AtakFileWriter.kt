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

import co.touchlab.kermit.Logger
import java.io.File

/**
 * Android implementation — writes route data packages to ATAK's monitored
 * auto-import directory. Tries multiple locations in order of preference:
 * 1. `/sdcard/atak/tools/datapackage/` (ATAK monitors this)
 * 2. `/sdcard/Download/` (user can manually import from here)
 */
@Suppress("TooGenericExceptionCaught")
internal actual object AtakFileWriter {

    actual fun writeToImportDir(fileName: String, zipBytes: ByteArray): Boolean {
        // Use hardcoded paths — on Android /sdcard/ maps to external storage.
        // On JVM desktop these paths don't exist and the fallback returns false.
        val targets = listOf(
            File("/sdcard/atak/tools/datapackage"),
            File("/sdcard/Download"),
        )

        for (dir in targets) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val target = File(dir, fileName)
                target.writeBytes(zipBytes)
                Logger.i { "Route data package written: $fileName (${zipBytes.size} bytes) → ${target.absolutePath}" }
                return true
            } catch (e: Exception) {
                Logger.d { "Cannot write to ${dir.absolutePath}: ${e.message}" }
            }
        }

        Logger.w { "Failed to write route data package to any ATAK import directory" }
        return false
    }
}
