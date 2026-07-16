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
package org.meshtastic.feature.map.mapcompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.koin.compose.koinInject
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.feature.map.mapcompose.geo.NormalizedBox
import org.meshtastic.feature.map.mapcompose.geo.WebMercator
import org.meshtastic.feature.map.mapcompose.tile.MeshTileStreamProvider
import org.meshtastic.feature.map.mapcompose.tile.TileCacheProvider
import org.meshtastic.feature.map.mapcompose.tile.TileSource
import ovh.plrapps.mapcompose.api.BoundingBox
import ovh.plrapps.mapcompose.api.addLayer
import ovh.plrapps.mapcompose.api.centroidSnapshotFlow
import ovh.plrapps.mapcompose.api.centroidX
import ovh.plrapps.mapcompose.api.centroidY
import ovh.plrapps.mapcompose.api.disableGestures
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.api.scrollTo
import ovh.plrapps.mapcompose.api.shutdown
import ovh.plrapps.mapcompose.ui.layout.Fit
import ovh.plrapps.mapcompose.ui.state.MapState
import kotlin.math.ln
import kotlin.math.pow

internal const val TILE_SIZE = 256

/** Recommended worker count for HTTP tile sources (library guidance; the default targets local sources). */
private const val HTTP_TILE_WORKERS = 16

/** A geographic camera position: where the map is centered and how far it is zoomed (slippy-map zoom level). */
data class MapCamera(val latitude: Double, val longitude: Double, val zoom: Double) {
    fun encode(): String = "$latitude,$longitude,$zoom"

    companion object {
        fun decode(encoded: String?): MapCamera? {
            val parts = encoded?.split(',') ?: return null
            if (parts.size != 3) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lon = parts[1].toDoubleOrNull() ?: return null
            val zoom = parts[2].toDoubleOrNull() ?: return null
            return MapCamera(lat, lon, zoom)
        }
    }
}

/** MapCompose scale 1.0 = the deepest zoom level; each level up halves the scale. */
internal fun zoomToScale(zoom: Double, maxZoom: Int): Double = 2.0.pow(zoom - maxZoom)

internal fun scaleToZoom(scale: Double, maxZoom: Int): Double = maxZoom + ln(scale) / ln(2.0)

/**
 * Creates and remembers a [MapState] configured as a Web-Mercator slippy map over [tileSource], recreated whenever the
 * source changes and shut down when the composition leaves (the state owns an internal coroutine scope).
 */
@Composable
internal fun rememberMeshMapState(tileSource: TileSource, interactive: Boolean = true, initialCamera: MapCamera? = null): MapState {
    val client = koinInject<HttpClient>()
    val cacheProvider = koinInject<TileCacheProvider>()
    val buildConfig = koinInject<BuildConfigProvider>()

    val mapState =
        remember(tileSource.id) {
            val maxZoom = tileSource.maxZoom
            val mapSize = TILE_SIZE * (1 shl maxZoom)
            // OSMF tile policy requires a User-Agent that identifies the application.
            val userAgent = "Meshtastic-Android/${buildConfig.versionName} (+https://meshtastic.org)"
            MapState(
                levelCount = maxZoom + 1,
                fullWidth = mapSize,
                fullHeight = mapSize,
                workerCount = HTTP_TILE_WORKERS,
            ) {
                minimumScaleMode(Fit)
                infiniteScrollX(true)
                initialCamera?.let { camera ->
                    val p = WebMercator.toNormalized(camera.latitude, camera.longitude)
                    scroll(p.x, p.y)
                    scale(zoomToScale(camera.zoom, maxZoom))
                }
            }
                .apply {
                    addLayer(MeshTileStreamProvider(tileSource, cacheProvider.cache, client, userAgent))
                    if (!interactive) disableGestures()
                }
        }

    DisposableEffect(mapState) { onDispose { mapState.shutdown() } }
    return mapState
}

/** The map's current camera, derived from its centroid and scale. */
internal fun MapState.currentCamera(maxZoom: Int): MapCamera = MapCamera(
    latitude = WebMercator.yToLatitude(centroidY),
    longitude = WebMercator.xToLongitude(centroidX),
    zoom = scaleToZoom(scale, maxZoom),
)

/** Persists the camera to [MapPrefs] (debounced) so the main map reopens where the user left it. */
@OptIn(FlowPreview::class)
@Composable
internal fun PersistCameraEffect(mapState: MapState, tileSource: TileSource, mapPrefs: MapPrefs) {
    LaunchedEffect(mapState) {
        mapState.centroidSnapshotFlow().debounce(CAMERA_PERSIST_DEBOUNCE_MS).collect {
            mapPrefs.setMapCameraPosition(mapState.currentCamera(tileSource.maxZoom).encode())
        }
    }
}

private const val CAMERA_PERSIST_DEBOUNCE_MS = 1000L

/** Scrolls (animated) to fit [box], both dimensions padded by the library's BoundingBox handling. */
internal suspend fun MapState.scrollToBox(box: NormalizedBox) {
    scrollTo(BoundingBox(xLeft = box.xLeft, yTop = box.yTop, xRight = box.xRight, yBottom = box.yBottom))
}
