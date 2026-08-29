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
package org.meshtastic.app.map

import java.io.InputStream

// All that is left of the Android side of map layers once the store moved to `feature/map`: sniffing a stream, which
// only this app's KML/KMZ readers do.

/** Zip magic bytes; a KML-typed source starting with these is a KMZ archive rather than bare KML. */
private val KMZ_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())

/**
 * True if [this] starts with the zip magic bytes, meaning a nominally-[LayerType.KML] source (`.kml` and `.kmz` both
 * resolve to that one type — see [KML_EXTENSIONS]) is actually a KMZ archive. Shared by both flavors' parsers so
 * neither has to duplicate the sniff or trust the file extension, which the content resolver can get wrong.
 *
 * Requires a mark-capable stream (wrap with [java.io.BufferedInputStream] first if unsure); leaves the stream position
 * unchanged either way.
 */
fun InputStream.isKmzArchive(): Boolean {
    mark(KMZ_MAGIC.size)
    val magic = ByteArray(KMZ_MAGIC.size)
    // read(ByteArray) is only guaranteed to return at least 1 byte before EOF, not to fill the buffer — loop rather
    // than trust a single call, or a short read could misclassify a real KMZ as bare KML.
    var totalRead = 0
    while (totalRead < magic.size) {
        val read = read(magic, totalRead, magic.size - totalRead)
        if (read == -1) break
        totalRead += read
    }
    reset()
    return totalRead == KMZ_MAGIC.size && magic.contentEquals(KMZ_MAGIC)
}
