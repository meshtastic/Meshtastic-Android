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

import android.util.Xml
import co.touchlab.kermit.Logger
import org.meshtastic.core.common.util.safeCatching
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Converts KML and KMZ into the GeoJSON MapLibre can read.
 *
 * MapLibre has no KML source, and the F-Droid map used to drop `LayerType.KML` imports on the floor: the file appeared
 * in the layers sheet, could be toggled, and drew nothing. The Google flavor reads KML through maps-android-utils'
 * parser, which is Google Maps-specific — hence a conversion here rather than a shared reader.
 *
 * Hand-written rather than a new dependency. The KML that reaches this app is what other mapping tools export —
 * Placemarks with a Point, LineString or Polygon and a colour — and the subset the Google flavor renders is no wider
 * than what this reads. A parser dependency in an F-Droid build is also a licence conversation this avoids.
 *
 * Styles come out as [simplestyle-spec](https://github.com/mapbox/simplestyle-spec) properties, which is what both
 * flavors' renderers already read, so a converted KML is styled by the same code path as an imported GeoJSON.
 *
 * Unsupported by design, because the renderers on both sides ignore them anyway: NetworkLink, GroundOverlay,
 * ScreenOverlay, Model, and time spans.
 */
internal object KmlToGeoJson {

    /**
     * Reads [source] and returns a GeoJSON `FeatureCollection` document, or null if nothing mappable was found.
     *
     * [source] must be mark-capable — wrap it in a [java.io.BufferedInputStream] — because a KMZ is recognised by
     * sniffing its first bytes rather than by trusting a file extension the content resolver often gets wrong.
     */
    fun convert(source: InputStream): String? {
        val kml = if (source.isKmzArchive()) source.firstKmlEntry() else source
        val features =
            kml?.let { stream ->
                safeCatching { parsePlacemarks(stream) }
                    .onFailure { failure -> Logger.withTag(TAG).w(failure) { "Could not read an imported KML" } }
                    .getOrDefault(emptyList())
            }
                .orEmpty()

        // An empty collection would draw nothing while claiming the import had succeeded.
        return if (features.isEmpty()) {
            null
        } else {
            features.joinToString(
                separator = ",",
                prefix = "{\"type\":\"FeatureCollection\",\"features\":[",
                postfix = "]}",
            )
        }
    }

    /**
     * The first `.kml` entry in a KMZ.
     *
     * By convention that is `doc.kml` at the archive root, but exporters disagree and some nest it, so the first entry
     * with the extension wins rather than a fixed name.
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

    private fun parsePlacemarks(kml: InputStream): List<String> {
        val parser =
            Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(kml, null)
            }

        val styles = mutableMapOf<String, KmlStyle>()
        val styleMaps = mutableMapOf<String, String>()
        val placemarks = mutableListOf<Placemark>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Style" -> parser.readStyle()?.let { (id, style) -> styles["#$id"] = style }
                    "StyleMap" -> parser.readStyleMap()?.let { (id, target) -> styleMaps["#$id"] = target }
                    "Placemark" -> placemarks += parser.readPlacemark()
                }
            }
            event = parser.next()
        }

        return placemarks.flatMap { placemark ->
            // An inline Style outranks a styleUrl, per the KML reference. A StyleMap points at the styles for
            // normal and highlighted; only the normal one is ever drawn here.
            val resolved = placemark.inlineStyle ?: placemark.styleUrl?.let { styles[styleMaps[it] ?: it] }
            placemark.geometries.map { geometry -> geometry.toFeature(placemark, resolved) }
        }
    }
}

private const val TAG = "KmlToGeoJson"
