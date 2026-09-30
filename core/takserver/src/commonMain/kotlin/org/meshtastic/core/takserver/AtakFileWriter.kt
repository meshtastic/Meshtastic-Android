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
 * Saves data package files where the user can import them into ATAK.
 *
 * On Android the package goes to the shared Downloads folder (the app's own external Downloads folder below API 29),
 * without any storage permission. On other platforms this is a no-op.
 */
internal expect object AtakFileWriter {
    /**
     * Save a data package zip, replacing the one this install saved earlier under the same name.
     *
     * @return true if the file was written successfully, false otherwise.
     */
    fun writeToImportDir(fileName: String, zipBytes: ByteArray): Boolean
}
