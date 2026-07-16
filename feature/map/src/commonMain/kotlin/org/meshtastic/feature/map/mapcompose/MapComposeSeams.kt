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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.util.LocalDiscoveryMapProvider
import org.meshtastic.core.ui.util.LocalInlineMapProvider
import org.meshtastic.core.ui.util.LocalMapMainScreenProvider
import org.meshtastic.core.ui.util.LocalMapViewProvider
import org.meshtastic.core.ui.util.LocalNodeMapScreenProvider
import org.meshtastic.core.ui.util.LocalNodeTrackMapProvider
import org.meshtastic.core.ui.util.LocalTracerouteMapProvider
import org.meshtastic.core.ui.util.MapViewProvider
import org.meshtastic.feature.map.MapScreen
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.node.NodeMapViewModel

/**
 * Binds every map seam that `feature:map` can satisfy to the shared MapCompose renderer — one call in an app's
 * composition root gives it the full map experience (the pattern MainActivity's `AppCompositionLocals` establishes for
 * the Android flavors, and the single swap point when fdroid later adopts this renderer).
 *
 * Not bound here: `LocalTracerouteMapScreenProvider` (its screen lives in `feature:node`, which this module must not
 * depend on — bind it alongside this call, mirroring MainActivity) and `LocalSitePlannerAvailable` (deliberately left
 * at its `false` default).
 */
@Suppress("ModifierMissing") // Pure CompositionLocalProvider wrapper; it lays out nothing of its own.
@Composable
fun ProvideMapComposeMap(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMapViewProvider provides MapComposeMapViewProvider,
        LocalMapMainScreenProvider provides
            { onClickNodeChip, navigateToNodeDetails, waypointId ->
                val viewModel = koinViewModel<SharedMapViewModel>()
                MapScreen(
                    viewModel = viewModel,
                    onClickNodeChip = onClickNodeChip,
                    navigateToNodeDetails = navigateToNodeDetails,
                    waypointId = waypointId,
                )
            },
        LocalNodeMapScreenProvider provides
            { destNum, onNavigateUp ->
                MapComposeNodeMapScreen(destNum = destNum, onNavigateUp = onNavigateUp)
            },
        LocalNodeTrackMapProvider provides
            { destNum, positions, modifier, selectedPositionTime, onPositionSelect ->
                MapComposeNodeTrackMap(
                    destNum = destNum,
                    positions = positions,
                    modifier = modifier,
                    selectedPositionTime = selectedPositionTime,
                    onPositionSelect = onPositionSelect,
                )
            },
        LocalTracerouteMapProvider provides
            { overlay, nodePositions, onMappableCountChange, modifier ->
                MapComposeMapView(
                    mode =
                    MapComposeMode.Traceroute(
                        overlay = overlay,
                        nodePositions = nodePositions,
                        onMappableCountChange = onMappableCountChange,
                    ),
                    navigateToNodeDetails = {},
                    modifier = modifier,
                )
            },
        LocalDiscoveryMapProvider provides
            { userLatitude, userLongitude, nodes, modifier ->
                MapComposeMapView(
                    mode = MapComposeMode.Discovery(userLatitude, userLongitude, nodes),
                    navigateToNodeDetails = {},
                    modifier = modifier,
                )
            },
        LocalInlineMapProvider provides
            { node, modifier ->
                MapComposeMapView(mode = MapComposeMode.Inline(node), navigateToNodeDetails = {}, modifier = modifier)
            },
        content = content,
    )
}

/** [MapViewProvider] backed by the shared MapCompose renderer. */
object MapComposeMapViewProvider : MapViewProvider {
    @Composable
    override fun MapView(modifier: Modifier, navigateToNodeDetails: (Int) -> Unit, waypointId: Int?) {
        MapComposeMapView(
            mode = MapComposeMode.Main(waypointId),
            navigateToNodeDetails = navigateToNodeDetails,
            modifier = modifier,
        )
    }
}

/** Position-track map for a node, resolved through the shared [NodeMapViewModel] position logs. */
@Composable
private fun MapComposeNodeTrackMap(
    destNum: Int,
    positions: List<org.meshtastic.proto.Position>,
    selectedPositionTime: Int?,
    onPositionSelect: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<SharedMapViewModel>()
    MapComposeMapView(
        mode =
        MapComposeMode.NodeTrack(
            focusedNode = viewModel.getNodeOrFallback(destNum),
            positions = positions,
            selectedPositionTime = selectedPositionTime,
            onPositionSelect = onPositionSelect,
        ),
        navigateToNodeDetails = {},
        modifier = modifier,
    )
}

/** Full-screen node map: app bar + the focused node's position track — the shared twin of google's NodeMapScreen. */
@Composable
private fun MapComposeNodeMapScreen(destNum: Int, onNavigateUp: () -> Unit) {
    val viewModel = koinViewModel<NodeMapViewModel>()
    viewModel.setDestNum(destNum)
    val node by viewModel.node.collectAsStateWithLifecycle()
    val positions by viewModel.positionLogs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MainAppBar(
                title = node?.user?.long_name ?: "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        MapComposeMapView(
            mode =
            MapComposeMode.NodeTrack(
                focusedNode = node,
                positions = positions,
                selectedPositionTime = null,
                onPositionSelect = null,
            ),
            navigateToNodeDetails = {},
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        )
    }
}
