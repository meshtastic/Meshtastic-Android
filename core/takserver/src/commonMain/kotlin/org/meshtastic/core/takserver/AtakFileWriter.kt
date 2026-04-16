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

/**
 * Writes data package files to ATAK's auto-import directory.
 *
 * On Android, the actual implementation writes to
 * `/sdcard/atak/tools/datapackage/` which ATAK monitors for new zip files.
 * On other platforms this is a no-op.
 */
internal expect object AtakFileWriter {
    /**
     * Write a data package zip to ATAK's monitored import directory.
     * @return true if the file was written successfully, false otherwise.
     */
    fun writeToImportDir(fileName: String, zipBytes: ByteArray): Boolean
}
