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
// disables the default template and silently drops iosMain. A few dozen lines twice is cheaper than either failure
// mode.

import co.touchlab.kermit.Logger
import org.meshtastic.feature.map.layers.MAX_KMZ_INFLATED_BYTES
import org.meshtastic.feature.map.layers.isKmzArchive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

actual fun readKmlDocument(bytes: ByteArray): String? = if (bytes.isKmzArchive()) {
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry }
            .firstOrNull { !it.isDirectory && it.name.endsWith(".kml", ignoreCase = true) }
            ?.let { zip.readEntryWithin(MAX_KMZ_INFLATED_BYTES)?.decodeToString() }
    }
} else {
    bytes.decodeToString()
}

actual fun readKmlArchiveImages(bytes: ByteArray, hrefs: Set<String>): Map<String, ByteArray> {
    if (hrefs.isEmpty() || !bytes.isKmzArchive()) return emptyMap()
    val images = mutableMapOf<String, ByteArray>()
    var budget = MAX_KMZ_INFLATED_BYTES
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name in hrefs) {
                // Budget blown mid-archive: stop reading but keep the images already extracted — each draws on its
                // own — and let the overlays whose files went unread be skipped by the caller as "not packed".
                val data = zip.readEntryWithin(budget) ?: break
                budget -= data.size
                images[entry.name] = data
            }
            entry = zip.nextEntry
        }
    }
    return images
}

/**
 * This entry inflated, or null once it would take the archive past [remaining] bytes.
 *
 * The inflated size cannot be trusted from the entry header (a hostile zip lies there), so the cap is enforced on the
 * bytes actually produced. `InputStream.readNBytes` would do this but is API 33+ on Android; minSdk is 26.
 */
private fun ZipInputStream.readEntryWithin(remaining: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read == -1) return out.toByteArray()
        if (out.size() + read > remaining) {
            Logger.withTag("KmlDocument").w { "Refusing a KMZ entry past the ${MAX_KMZ_INFLATED_BYTES}B inflate cap" }
            return null
        }
        out.write(buffer, 0, read)
    }
}
