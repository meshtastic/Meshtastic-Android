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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry

/**
 * A GeoJSON source that keeps up with its features.
 *
 * `rememberGeoJsonSource` reads its `data` when the source is created and not again — the API's own way to change the
 * contents afterwards is `setData`. So a source built from a list that arrives later stays empty for the lifetime of
 * the map, which is what happened to the discovery map: its scanner drew, because the session is resolved before the
 * map composes, while every discovered node was invisible, because those load asynchronously afterwards.
 *
 * The bug hides wherever the data happens to be ready in time. The main map's nodes and waypoints have been collected
 * since app start, so they are already there on first composition and look fine; a screen with its own view model,
 * created as the screen opens, always composes empty first.
 */
@Composable
internal fun <G : Geometry> rememberFeatureSource(
    features: FeatureCollection<G, JsonObject?>,
    options: GeoJsonOptions = GeoJsonOptions(),
): GeoJsonSource {
    val source = rememberGeoJsonSource(data = GeoJsonData.Features(features), options = options)
    // Skipped on the first pass, where the source was just built from these very features.
    val initial = remember { features }
    LaunchedEffect(features) { if (features !== initial) source.setData(GeoJsonData.Features(features)) }
    return source
}
