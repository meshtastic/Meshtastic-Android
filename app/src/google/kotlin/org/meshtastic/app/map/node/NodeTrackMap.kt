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
package org.meshtastic.app.map.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.map.GoogleMapMode
import org.meshtastic.app.map.MapView
import org.meshtastic.feature.map.node.NodeMapViewModel
import org.meshtastic.proto.Position

/**
 * Flavor-unified entry point for the embeddable node-track map. Resolves [destNum] to a
 * [org.meshtastic.core.model.Node] via [NodeMapViewModel] and delegates to [MapView] in [GoogleMapMode.NodeTrack] mode,
 * which provides the full shared map infrastructure (location tracking, tile providers, controls overlay with track
 * filter).
 */
@Composable
fun NodeTrackMap(destNum: Int, positions: List<Position>, modifier: Modifier = Modifier) {
    val vm = koinViewModel<NodeMapViewModel>()
    vm.setDestNum(destNum)
    val focusedNode by vm.node.collectAsStateWithLifecycle()
    MapView(modifier = modifier, mode = GoogleMapMode.NodeTrack(focusedNode = focusedNode, positions = positions))
}
