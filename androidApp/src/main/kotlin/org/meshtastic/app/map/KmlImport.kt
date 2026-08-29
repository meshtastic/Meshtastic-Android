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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.meshtastic.feature.map.kml.KmlToGeoJson
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads an imported KML or KMZ and hands back the GeoJSON MapLibre and Google Maps can both draw.
 *
 * The conversion itself is [KmlToGeoJson] in `feature/map`, which is common code and takes the document as text. What
 * stays here is the part that is genuinely a file: sniffing whether the import is a zip, pulling the KML out of it, and
 * decoding the images packed beside it.
 *
 * [source] must be mark-capable — wrap it in a [java.io.BufferedInputStream] — because a KMZ is recognised by sniffing
 * its first bytes rather than by trusting a file extension the content resolver often gets wrong.
 */
fun convertKmlSource(source: InputStream): ImportedKml? = if (source.isKmzArchive()) {
    source.readArchive()
} else {
    source.readBytes().decodeToString().toImportedKml(emptyMap())
}

/**
 * Null only when the document holds nothing drawable at all. An overlay-only KML gets an empty `FeatureCollection`
 * rather than being dropped — the overlays are the content, and every parser downstream needs valid JSON.
 */
private fun String.toImportedKml(images: Map<String, Bitmap>): ImportedKml? {
    val conversion = KmlToGeoJson.convertDocument(this)
    if (conversion.geoJson == null && conversion.groundOverlays.isEmpty()) return null
    return ImportedKml(
        geoJson = conversion.geoJson ?: EMPTY_FEATURE_COLLECTION,
        images = images,
        groundOverlays = conversion.groundOverlays,
    )
}

private const val EMPTY_FEATURE_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

/**
 * The KML in an archive, and every image beside it.
 *
 * By convention the document is `doc.kml` at the root, but exporters disagree and some nest it, so the first entry with
 * the extension wins rather than a fixed name. The zip is walked once: a second pass would mean holding the whole
 * archive in memory or reopening a stream the caller does not own.
 */
private fun InputStream.readArchive(): ImportedKml? {
    var kml: String? = null
    val images = mutableMapOf<String, Bitmap>()

    ZipInputStream(this).use { zip ->
        generateSequence { zip.nextEntry }
            .filterNot { it.isDirectory }
            .forEach { entry ->
                val bytes = zip.readBytes()
                when {
                    kml == null && entry.name.endsWith(".kml", ignoreCase = true) -> kml = bytes.decodeToString()

                    // Anything else that decodes as an image is one a placemark may name; anything that does not is
                    // some other file the exporter packed, and is skipped without complaint.
                    else -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { images[entry.name] = it }
                }
            }
    }

    return kml?.toImportedKml(images)
}
