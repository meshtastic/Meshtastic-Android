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
package org.meshtastic.feature.map.kml

// Duplicated verbatim in androidMain and jvmMain rather than shared from a custom `jvmAndroid` source-set group:
// the hierarchy-template group would not attach to the AGP-owned android target, and a hand-written dependsOn edge
// disables the default template and silently drops iosMain. Twelve lines twice is cheaper than either failure mode.

import org.meshtastic.feature.map.layers.isKmzArchive
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

actual fun readKmlDocument(bytes: ByteArray): String? = if (bytes.isKmzArchive()) {
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }
            .firstOrNull { !it.isDirectory && it.name.endsWith(".kml", ignoreCase = true) }
            ?.let { zip.readBytes().decodeToString() }
    }
} else {
    bytes.decodeToString()
}

actual fun readKmlArchiveImages(bytes: ByteArray, hrefs: Set<String>): Map<String, ByteArray> {
    if (hrefs.isEmpty() || !bytes.isKmzArchive()) return emptyMap()
    val images = mutableMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }
            .filterNot { it.isDirectory }
            .forEach { entry -> if (entry.name in hrefs) images[entry.name] = zip.readBytes() }
    }
    return images
}
