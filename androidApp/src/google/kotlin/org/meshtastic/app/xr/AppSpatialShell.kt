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
package org.meshtastic.app.xr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.meshtastic.app.map.offline.pmtiles.OfflineBuildings
import org.meshtastic.app.map.offline.pmtiles.OfflineRegionExtractor
import org.meshtastic.app.map.offline.pmtiles.OfflineRegionStore
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.ui.component.Quality
import org.meshtastic.core.ui.component.determineSignalQuality
import org.meshtastic.feature.map.terrain.spatial.LatLon
import org.meshtastic.feature.map.terrain.spatial.SpatialNodeSnapshot
import org.meshtastic.feature.map.terrain.spatial.SpatialSignal
import org.meshtastic.feature.map.tiles.MapTileCatalogue
import org.meshtastic.feature.xr.SpatialShell
import org.meshtastic.feature.xr.TerrainBuildingSource
import org.meshtastic.feature.xr.TerrainImagery
import org.meshtastic.feature.xr.TerrainRoute
import org.meshtastic.proto.Config
import java.io.File

/** The 2D map's raster basemaps, satellite first: it reads best on a relief model. */
private val terrainImagery: List<TerrainImagery> =
    (listOf(MapTileCatalogue.EsriImagery) + MapTileCatalogue.basemaps)
        .distinctBy { it.id }
        .map {
            TerrainImagery(
                id = it.id,
                label = it.label,
                tileUrl = it.spec.tiles.first(),
                maxZoom = it.spec.maxZoom,
                attribution = it.spec.attributionHtml.orEmpty().replace(Regex("<[^>]+>"), "").replace("&copy;", "©"),
            )
        }

private val INFRASTRUCTURE_ROLES =
    setOf(
        Config.DeviceConfig.Role.ROUTER,
        Config.DeviceConfig.Role.ROUTER_LATE,
        Config.DeviceConfig.Role.REPEATER,
        Config.DeviceConfig.Role.CLIENT_BASE,
    )

private fun signalOf(snr: Float): SpatialSignal = when (determineSignalQuality(snr, modemPreset = null)) {
    Quality.GOOD -> SpatialSignal.GOOD
    Quality.FAIR -> SpatialSignal.FAIR
    Quality.BAD -> SpatialSignal.BAD
    Quality.NONE -> SpatialSignal.NONE
}

@Composable
fun AppSpatialShell(content: @Composable () -> Unit) {
    val repository = koinInject<NodeRepository>()
    val nodes by repository.nodeDBbyNum.collectAsState()
    val ours by repository.ourNodeInfo.collectAsState()
    val snapshots =
        remember(nodes, ours) {
            nodes.values
                .filter { it.validPosition != null }
                .map {
                    SpatialNodeSnapshot(
                        id = it.num.toLong(),
                        coordinate = LatLon(it.latitude, it.longitude),
                        isConnectedRadio = it.num == ours?.num,
                        shortName = it.user.short_name,
                        color = it.colors.second,
                        isOnline = it.isOnline,
                        isFavorite = it.isFavorite,
                        viaMqtt = it.viaMqtt,
                        hopsAway = it.hopsAway,
                        signal = it.snr.takeIf { snr -> snr != Float.MAX_VALUE }?.let(::signalOf),
                        isInfrastructure = it.user.role in INFRASTRUCTURE_ROLES,
                        lastHeard = it.lastHeard,
                    )
                }
        }
    val dispatchers = koinInject<CoroutineDispatchers>()
    val context = LocalContext.current
    val buildings =
        remember(context, dispatchers) {
            // Made on first use: the store touches the disk, and this composes on phones too.
            val store by lazy { OfflineRegionStore(File(context.filesDir, OfflineRegionStore.DIRECTORY)) }
            TerrainBuildingSource(
                attribution = OfflineRegionExtractor.ATTRIBUTION,
                available = { withContext(dispatchers.io) { OfflineBuildings.available(store) } },
                footprints = { bounds -> withContext(dispatchers.io) { OfflineBuildings.footprints(bounds, store) } },
            )
        }
    // The screen that shows a trace route clears it, so the table keeps the last one that had a route.
    val traced by koinInject<ServiceRepository>().tracerouteResponse.collectAsState()
    val route by
        produceState<TerrainRoute?>(null, traced) {
            traced
                ?.takeIf { it.hasOverlay }
                ?.let { value = TerrainRoute(it.forwardRoute.map(Int::toLong), it.returnRoute.map(Int::toLong)) }
        }
    SpatialShell(
        nodes = snapshots,
        imagery = terrainImagery,
        buildings = buildings,
        route = route,
        buildDispatcher = dispatchers.default,
        content = content,
    )
}
