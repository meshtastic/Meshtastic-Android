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
package org.meshtastic.feature.map.geojson

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.meshtastic.feature.map.kml.ICON_URL_PROPERTY

/**
 * More distinct icons than one imported layer will be given style images for.
 *
 * Every icon becomes an image uploaded into the map style and held for as long as the layer is shown, so an import with
 * a per-feature icon would otherwise be an unbounded upload. Well past what a hand-made overlay carries.
 */
private const val MAX_ICONS = 64

/**
 * The distinct icon images a GeoJSON document asks for.
 *
 * The renderer has to know these before it composes: MapLibre picks an icon per feature from a `case` over the values
 * it was given, and there is no way to register an image under a name chosen later — `ImageManager`'s acquire methods
 * are internal to the library. So the document is read once, here, rather than left to the map to discover.
 *
 * Returns nothing for a document that will not parse. An import that cannot be read is already drawing nothing; the
 * renderer falling back to plain points is the better failure than no layer at all.
 */
fun geoJsonIconUrls(geoJson: String): Set<String> = try {
    Json.parseToJsonElement(geoJson)
        .jsonObject["features"]
        ?.jsonArray
        .orEmpty()
        .asSequence()
        .mapNotNull { feature -> (feature as? JsonObject)?.get("properties") as? JsonObject }
        // `as?` rather than the `jsonPrimitive` accessor, which throws: an import is free to put an object or a
        // number where a URL belongs, and one such feature must not cost the layer its other icons.
        .mapNotNull { properties ->
            (properties[ICON_URL_PROPERTY] as? JsonPrimitive)?.takeIf { it.isString }?.content
        }
        .filter { it.isNotBlank() }
        .distinct()
        .take(MAX_ICONS)
        .toSet()
} catch (e: IllegalArgumentException) {
    Logger.withTag(TAG).w(e) { "Could not read icons from an imported layer" }
    emptySet()
}

private const val TAG = "GeoJsonIcons"
