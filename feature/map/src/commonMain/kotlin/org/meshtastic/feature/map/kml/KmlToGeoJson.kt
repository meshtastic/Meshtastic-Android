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

import co.touchlab.kermit.Logger
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming
import org.meshtastic.core.common.util.safeCatching

/**
 * Converts KML into the GeoJSON MapLibre can read.
 *
 * MapLibre has no KML source, and the F-Droid map used to drop `LayerType.KML` imports on the floor: the file appeared
 * in the layers sheet, could be toggled, and drew nothing.
 *
 * Hand-written rather than a new dependency. The KML that reaches this app is what other mapping tools export —
 * Placemarks with a Point, LineString or Polygon and a colour — and the subset the renderers draw is no wider than what
 * this reads. It parses through xmlutil, which the app already resolves for CoT XML, so this is common code and runs
 * wherever the app does rather than only on Android.
 *
 * Styles come out as [simplestyle-spec](https://github.com/mapbox/simplestyle-spec) properties, which is what both
 * flavors' renderers already read, so a converted KML is styled by the same code path as an imported GeoJSON.
 *
 * Unsupported by design, because the renderers on both sides ignore them anyway: NetworkLink, ScreenOverlay, Model, and
 * time spans. GroundOverlays are parsed — they come back from [convertDocument] beside the GeoJSON, because an image
 * draped over a box has no GeoJSON representation at all.
 */
object KmlToGeoJson {

    /**
     * Reads [kml] and returns a GeoJSON `FeatureCollection` document, or null if nothing mappable was found.
     *
     * Takes the document as text rather than a stream: a KMZ is a zip, and unpacking one is the host's job — this
     * converter only ever sees the KML inside.
     */
    fun convert(kml: String): String? = convertDocument(kml).geoJson

    /**
     * Everything drawable in [kml]: the vector features as a GeoJSON `FeatureCollection` (null if there are none), plus
     * any `<GroundOverlay>` images. A document can hold either without the other — a pure overlay export has no
     * placemarks at all, and it must not read as "nothing mappable".
     */
    fun convertDocument(kml: String): KmlConversion {
        val (features, overlays) =
            safeCatching { parseDocument(kml) }
                .onFailure { failure -> Logger.withTag(TAG).w(failure) { "Could not read an imported KML" } }
                .getOrDefault(emptyList<String>() to emptyList())

        // An empty collection would draw nothing while claiming the import had succeeded.
        val geoJson =
            if (features.isEmpty()) {
                null
            } else {
                features.joinToString(
                    separator = ",",
                    prefix = "{\"type\":\"FeatureCollection\",\"features\":[",
                    postfix = "]}",
                )
            }
        return KmlConversion(geoJson, overlays)
    }

    @Suppress("CyclomaticComplexMethod") // one branch per top-level KML element; splitting hides the shape
    private fun parseDocument(kml: String): Pair<List<String>, List<KmlGroundOverlay>> {
        val reader = xmlStreaming.newReader(kml)

        val styles = mutableMapOf<String, KmlStyle>()
        val styleMaps = mutableMapOf<String, String>()
        val placemarks = mutableListOf<Placemark>()
        val overlays = mutableListOf<KmlGroundOverlay>()

        var done = false
        while (!done) {
            when (reader.next()) {
                EventType.START_ELEMENT ->
                    when (reader.localName) {
                        "Style" -> reader.readStyle()?.let { (id, style) -> styles["#$id"] = style }
                        "StyleMap" -> reader.readStyleMap()?.let { (id, target) -> styleMaps["#$id"] = target }
                        "Placemark" -> placemarks += reader.readPlacemark()
                        "GroundOverlay" -> reader.readGroundOverlay()?.let { overlays += it }
                        else -> Unit
                    }

                EventType.END_DOCUMENT -> done = true

                else -> Unit
            }
        }

        val features =
            placemarks.flatMap { placemark ->
                // An inline Style outranks a styleUrl, per the KML reference. A StyleMap points at the styles for
                // normal and highlighted; only the normal one is ever drawn here.
                val resolved = placemark.inlineStyle ?: placemark.styleUrl?.let { styles[styleMaps[it] ?: it] }
                placemark.geometries.map { geometry -> geometry.toFeature(placemark, resolved) }
            }
        return features to overlays
    }
}

private const val TAG = "KmlToGeoJson"
