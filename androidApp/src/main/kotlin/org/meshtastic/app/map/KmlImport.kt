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

import org.meshtastic.feature.map.kml.KmlToGeoJson
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads an imported KML or KMZ and hands back the GeoJSON MapLibre can draw.
 *
 * The conversion itself is [KmlToGeoJson] in `feature/map`, which is common code and takes the document as text. What
 * stays here is the part that is genuinely a file: sniffing whether the import is a zip, and pulling the KML out of it.
 * Shared by both flavours — the MapLibre map renders the GeoJSON directly, the Google map hands it to maps-utils'
 * GeoJSON pipeline.
 *
 * [source] must be mark-capable — wrap it in a [java.io.BufferedInputStream] — because a KMZ is recognised by sniffing
 * its first bytes rather than by trusting a file extension the content resolver often gets wrong.
 */
fun convertKmlSource(source: InputStream): String? {
    val kml = if (source.isKmzArchive()) source.firstKmlEntry() else source
    return kml?.let { KmlToGeoJson.convert(it.readBytes().decodeToString()) }
}

/**
 * The first `.kml` entry in a KMZ.
 *
 * By convention that is `doc.kml` at the archive root, but exporters disagree and some nest it, so the first entry with
 * the extension wins rather than a fixed name.
 */
private fun InputStream.firstKmlEntry(): InputStream? {
    val zip = ZipInputStream(this)
    var entry = zip.nextEntry
    var found = false
    while (entry != null && !found) {
        found = !entry.isDirectory && entry.name.endsWith(".kml", ignoreCase = true)
        if (!found) entry = zip.nextEntry
    }
    return if (found) zip else null
}
