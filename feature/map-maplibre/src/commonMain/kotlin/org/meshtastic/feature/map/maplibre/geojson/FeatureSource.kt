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
package org.meshtastic.feature.map.maplibre.geojson

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry

/**
 * A GeoJSON source built from a feature collection, which is only rebuilt when [keys] change.
 *
 * Saves every layer the `GeoJsonData.Features(...)` wrapping, since none of them ever needs another kind of
 * `GeoJsonData`. `rememberGeoJsonSource` republishes on its own whenever `data` changes — `GeoJsonData.Features` is a
 * data class, so a collection that is equal to the last one costs nothing and a changed one is pushed to the map.
 *
 * [features] is a lambda, and the keys are mandatory, because that republishing guarantee covers only the *publish*: a
 * collection passed by value is still **built** on every recomposition before it can be compared. The main map
 * recomposes on every frame of a pan or zoom — it reads the camera's viewport — so a by-value collection rebuilt the
 * whole mesh, and every precision circle as a 64-vertex polygon, on the main thread once per frame. Keying the build is
 * what keeps that off the frame path, so pass the values the collection is derived from.
 */
@Composable
internal fun <G : Geometry> rememberFeatureSource(
    vararg keys: Any?,
    options: GeoJsonOptions = GeoJsonOptions(),
    features: () -> FeatureCollection<G, JsonObject?>,
): GeoJsonSource {
    val data = remember(*keys, options) { GeoJsonData.Features(features()) }
    return rememberGeoJsonSource(data = data, options = options)
}
