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
package org.meshtastic.feature.map.maplibre.layers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.expressions.dsl.asNumber
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.dp
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.layers.HillshadeLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.RasterDemEncoding
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberRasterDemSource
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.feature.map.maplibre.geojson.ContourFeatureKeys
import org.meshtastic.feature.map.maplibre.geojson.loadContourFeatureCollection
import org.meshtastic.feature.map.maplibre.terrain.OfflineTerrainRegion
import org.meshtastic.feature.map.maplibre.terrain.OfflineTerrainRepository
import org.meshtastic.feature.map.maplibre.terrain.intersects
import org.meshtastic.feature.map.maplibre.terrain.toGeoBounds
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainSource
import org.meshtastic.feature.map.terrain.TerrainTileMath

/**
 * Offline terrain — hillshade and elevation contours, rendered for the viewport when a downloaded region covers it.
 *
 * Gated on [OfflineTerrainRepository.default]'s [OfflineTerrainRegion] rather than being a togglable
 * [org.meshtastic.feature.map.maplibre.style.MapOverlay] like the online hillshade overlay: there is nothing to toggle
 * until something has been downloaded, and once it has, showing it automatically — the same "auto-activate once the
 * precondition is met" shape the base offline layer's own fallback uses — beats a second switch the user has to
 * remember to flip. If the online [org.meshtastic.feature.map.maplibre.style.MapOverlay.Hillshade] overlay is also on,
 * the two composite on top of each other; harmless (both are legitimate shading of the same real terrain) and
 * deliberately not deduplicated for this first version.
 *
 * **Not visually verified on a real device or desktop build in this session** — no display was available. The
 * `file://` + raster-dem combination is architecturally sound (see this feature's PR description for the
 * maplibre-native source evidence) but needs an on-device smoke test before merge, and so does the behavior of a
 * `file://` tile source for a tile the store doesn't have on disk (a viewport that only partially overlaps the
 * downloaded region, or a zoom level between the two tiers) — MapLibre's handling of a missing local file is unverified
 * here.
 */
@Composable
internal fun TerrainLayers(viewportBounds: BoundingBox?, zoom: Double, displayUnits: MeasurementSystem) {
    val repository = remember { OfflineTerrainRepository.default }
    val region by repository.region.collectAsState()

    LaunchedEffect(Unit) { repository.refresh() }

    // Two early returns (detekt's ReturnCount limit), kept inline rather than in a helper function: a helper
    // returning a resolved nullable can't hand the compiler back a smart-cast on *this* function's own
    // `viewportBounds` parameter, and ContourLayer below needs it non-null. A manifest can record `maxZoom = -1` —
    // TerrainRegionExtractor's own "zero tiles requested" success case (see OfflineTerrainRepositoryTest) — meaning
    // nothing was ever fetched; that and "no region at all" are treated the same way here.
    val downloaded = region
    if (downloaded == null || downloaded.maxZoom < 0 || viewportBounds == null) return
    if (!downloaded.intersects(viewportBounds)) return

    HillshadeTiers(repository, downloaded)
    ContourLayer(repository, downloaded, viewportBounds, zoom, displayUnits == MeasurementSystem.METRIC)
}

/**
 * The two hillshade tiers, as two [HillshadeLayer]s over two `file://` raster-dem sources (see
 * [org.maplibre.compose.sources.rememberRasterDemSource]) — see this feature's PR description for why MapLibre's own
 * internal Horn's-method shading means no decoded elevation grid or [org.meshtastic.feature.map.terrain.Hillshade] call
 * is needed here at all, unlike the contours below.
 *
 * A style layer's own `minZoom`/`maxZoom` (exclusive on the max, per the style spec) is how the two tiers hand off: the
 * global layer stops at [MapterhornEndpoints.REGIONAL_MIN_ZOOM] once regional detail exists, and the regional layer
 * starts there — never both drawing the same pixel. Each source's own [TileSetOptions] is clamped to
 * [OfflineTerrainRegion.maxZoom] as well as the tier's own endpoint constant, so MapLibre is never asked for a tile
 * file this region's own download never fetched.
 */
@Composable
private fun HillshadeTiers(repository: OfflineTerrainRepository, region: OfflineTerrainRegion) {
    val globalMaxZoom = minOf(region.maxZoom, MapterhornEndpoints.GLOBAL_MAX_ZOOM)
    val globalSource =
        rememberRasterDemSource(
            tiles = listOf(repository.tileUrlTemplate(TerrainSource.GLOBAL)),
            options = TileSetOptions(minZoom = 0, maxZoom = globalMaxZoom),
            encoding = RasterDemEncoding.Terrarium,
        )

    if (region.hasRegionalDetail) {
        HillshadeLayer(
            id = "offline-terrain-hillshade-global",
            source = globalSource,
            maxZoom = MapterhornEndpoints.REGIONAL_MIN_ZOOM.toFloat(),
            shadowColor = const(Color.Black),
            highlightColor = const(Color.White),
            accentColor = const(Color.Black),
        )

        val regionalMaxZoom = minOf(region.maxZoom, MapterhornEndpoints.REGIONAL_MAX_ZOOM)
        val regionalSource =
            rememberRasterDemSource(
                tiles = listOf(repository.tileUrlTemplate(TerrainSource.REGIONAL)),
                options = TileSetOptions(minZoom = MapterhornEndpoints.REGIONAL_MIN_ZOOM, maxZoom = regionalMaxZoom),
                encoding = RasterDemEncoding.Terrarium,
            )
        HillshadeLayer(
            id = "offline-terrain-hillshade-regional",
            source = regionalSource,
            minZoom = MapterhornEndpoints.REGIONAL_MIN_ZOOM.toFloat(),
            shadowColor = const(Color.Black),
            highlightColor = const(Color.White),
            accentColor = const(Color.Black),
        )
    } else {
        HillshadeLayer(
            id = "offline-terrain-hillshade-global",
            source = globalSource,
            shadowColor = const(Color.Black),
            highlightColor = const(Color.White),
            accentColor = const(Color.Black),
        )
    }
}

/**
 * Elevation contours for the tiles [viewportBounds] touches, decoded off the composition thread — see
 * [loadContourFeatureCollection]'s own doc comment for why, and `GroundOverlayLayer` in `CustomLayers.kt` for the
 * precedent this follows.
 *
 * Styled per-feature via the simplestyle properties [loadContourFeatureCollection] already attached
 * ([ContourFeatureKeys]), the same `stroke`/`stroke-width`/`stroke-opacity` expression pattern `CustomLayers.kt`'s
 * `ImportedLayer` uses for imported overlays.
 */
@Composable
private fun ContourLayer(
    repository: OfflineTerrainRepository,
    region: OfflineTerrainRegion,
    viewportBounds: BoundingBox,
    zoom: Double,
    metric: Boolean,
) {
    // region.maxZoom.coerceAtLeast(0): a manifest can in principle round-trip a maxZoom of -1 (a real,
    // successful "zero tiles requested" download — see OfflineTerrainRepositoryTest's own case for it), and
    // coerceIn(0, -1) throws. Never produced by this section's own UI, but the manifest is disk state, not a
    // value this code controls end to end.
    val tileZoom = zoom.toInt().coerceIn(0, region.maxZoom.coerceAtLeast(0))
    val tiles =
        remember(viewportBounds, tileZoom) {
            TerrainTileMath.tilesAt(tileZoom, viewportBounds.toGeoBounds()).take(MAX_CONTOUR_TILES)
        }

    val collection by
        produceState<FeatureCollection<LineString, JsonObject?>?>(null, tiles, tileZoom, metric) {
            value =
                withContext(ioDispatcher) {
                    loadContourFeatureCollection(repository.tileStore, tiles, tileZoom, metric)
                }
        }
    val loaded = collection ?: return
    if (loaded.features.isEmpty()) return

    // Not rememberFeatureSource: that wrapper's own `remember` is keyed on inputs that change *before* the
    // asynchronous produceState above finishes decoding, so it would republish the previous collection under
    // the new keys and only catch up a recomposition later — contours would lag one tile-change behind forever.
    // `loaded` here is already the freshly produced collection, so publishing it directly on every recomposition
    // is the correct (and cheap — GeoJsonData.Features is a data class) case FeatureSource.kt's own doc comment
    // describes for rememberGeoJsonSource itself.
    val source = rememberGeoJsonSource(data = GeoJsonData.Features(loaded))

    LineLayer(
        id = "offline-terrain-contours",
        source = source,
        color = feature[ContourFeatureKeys.STROKE].asString().convertToColor(const(ContourFallbackColor)),
        width = feature[ContourFeatureKeys.STROKE_WIDTH].asNumber().dp,
        opacity = feature[ContourFeatureKeys.STROKE_OPACITY].asNumber(),
    )
}

/**
 * Tiles processed per contour recomposition — bounds the work `produceState`'s decode+marching-squares does on a
 * viewport spanning many tiles at a shallow zoom. A viewport this wide already has coarser downloaded terrain (see
 * [org.meshtastic.feature.map.maplibre.component.OfflineTerrainSection]'s own zoom-based download cap), so the tiles
 * beyond this cap are the ones contributing the least visible contour detail anyway.
 */
private const val MAX_CONTOUR_TILES = 16

/**
 * Fallback only — every contour feature this module builds always carries its own `stroke` property (see
 * [loadContourFeatureCollection]), so this is never actually drawn. Matches
 * [org.meshtastic.feature.map.terrain.ContourStyling]'s own topo-map-conventional brown.
 */
private val ContourFallbackColor = Color(0xFF8B5A2B)
