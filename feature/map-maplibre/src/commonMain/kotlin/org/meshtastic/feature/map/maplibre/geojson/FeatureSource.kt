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
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry

/**
 * A GeoJSON source built straight from a feature collection.
 *
 * Only here to save every layer the `GeoJsonData.Features(...)` wrapping, since none of them ever needs another kind of
 * `GeoJsonData`. `rememberGeoJsonSource` republishes on its own whenever `data` changes — `GeoJsonData.Features` is a
 * data class, so a collection that is equal to the last one costs nothing and a changed one is pushed to the map.
 */
@Composable
internal fun <G : Geometry> rememberFeatureSource(
    features: FeatureCollection<G, JsonObject?>,
    options: GeoJsonOptions = GeoJsonOptions(),
): GeoJsonSource = rememberGeoJsonSource(data = GeoJsonData.Features(features), options = options)
