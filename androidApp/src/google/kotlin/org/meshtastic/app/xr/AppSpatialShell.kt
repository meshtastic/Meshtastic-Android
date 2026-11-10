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
import androidx.compose.runtime.remember
import org.koin.compose.koinInject
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.feature.map.terrain.spatial.LatLon
import org.meshtastic.feature.map.terrain.spatial.SpatialNodeSnapshot
import org.meshtastic.feature.map.tiles.MapTileCatalogue
import org.meshtastic.feature.xr.SpatialShell
import org.meshtastic.feature.xr.TerrainImagery

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
                    )
                }
        }
    SpatialShell(
        nodes = snapshots,
        imagery = terrainImagery,
        buildDispatcher = koinInject<CoroutineDispatchers>().default,
        content = content,
    )
}
