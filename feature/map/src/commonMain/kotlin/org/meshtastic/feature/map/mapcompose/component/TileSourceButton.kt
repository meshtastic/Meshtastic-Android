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
package org.meshtastic.feature.map.mapcompose.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_tile_source
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.mapcompose.TILE_SIZE
import org.meshtastic.feature.map.mapcompose.geo.WebMercator
import org.meshtastic.feature.map.mapcompose.tile.TileSource
import org.meshtastic.feature.map.mapcompose.tile.TileSourceCatalog
import ovh.plrapps.mapcompose.api.centroidSnapshotFlow
import ovh.plrapps.mapcompose.api.centroidY
import ovh.plrapps.mapcompose.api.scale
import ovh.plrapps.mapcompose.ui.state.MapState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/** Tile-source picker rendered in [MapControlsOverlay]'s map-type slot; the shared twin of the fdroid dropdown. */
@Composable
internal fun TileSourceButton(selected: TileSource, onSelect: (TileSource) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MapButton(
            icon = MeshtasticIcons.Map,
            contentDescription = stringResource(Res.string.map_tile_source),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TileSourceCatalog.ALL.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.name) },
                    onClick = {
                        onSelect(source)
                        expanded = false
                    },
                    leadingIcon = { RadioButton(selected = source.id == selected.id, onClick = null) },
                )
            }
        }
    }
}

/** Tile-provider attribution, required by the providers' usage policies (e.g. OSM). */
@Composable
internal fun MapAttributionBar(tileSource: TileSource, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = SCRIM_ALPHA)) {
        Text(
            text = tileSource.attribution,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * A simple scale indicator: the ground distance covered by a fixed 100px on-screen ruler, corrected for the
 * Web-Mercator latitude stretch at the current map center.
 */
@Composable
internal fun MapScaleBar(mapState: MapState, tileSource: TileSource, modifier: Modifier = Modifier) {
    val camera by
        remember(mapState) { mapState.centroidSnapshotFlow().map { mapState.scale to mapState.centroidY } }
            .collectAsState(initial = mapState.scale to mapState.centroidY)

    val (scale, centroidY) = camera
    if (scale <= 0.0) return
    val latitude = WebMercator.yToLatitude(centroidY)
    // The rendered world is (fullSize * scale) pixels wide and spans the equatorial circumference shrunk by cos(lat).
    val mapSizePx = TILE_SIZE * (1 shl tileSource.maxZoom) * scale
    val worldMeters = 2 * PI * WebMercator.EARTH_RADIUS_M * cos(latitude * PI / HALF_TURN_DEGREES)
    val rulerMeters = worldMeters / mapSizePx * RULER_WIDTH_PX

    val label =
        if (rulerMeters >= METERS_PER_KM) {
            "${NumberFormatter.format(rulerMeters / METERS_PER_KM, 1)} km"
        } else {
            "${rulerMeters.roundToInt()} m"
        }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = SCRIM_ALPHA)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private const val SCRIM_ALPHA = 0.7f
private const val RULER_WIDTH_PX = 100.0
private const val METERS_PER_KM = 1000.0
private const val HALF_TURN_DEGREES = 180.0
