package org.meshtastic.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.meshtastic.app.map.getMapViewProvider
import org.meshtastic.app.node.component.InlineMap
import org.meshtastic.app.node.metrics.getTracerouteMapOverlayInsets
import org.meshtastic.core.ui.util.LocalInlineMapProvider
import org.meshtastic.core.ui.util.LocalMapMainScreenProvider
import org.meshtastic.core.ui.util.LocalMapViewProvider
import org.meshtastic.core.ui.util.LocalNodeMapScreenProvider
import org.meshtastic.core.ui.util.LocalNodeTrackMapProvider
import org.meshtastic.core.ui.util.LocalTracerouteMapOverlayInsetsProvider
import org.meshtastic.core.ui.util.LocalTracerouteMapProvider
import org.meshtastic.core.ui.util.LocalTracerouteMapScreenProvider
import org.meshtastic.feature.map.MapScreen
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.node.NodeMapViewModel
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.metrics.TracerouteMapScreen

@Composable
fun MapLocals(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMapViewProvider provides getMapViewProvider(),
        LocalInlineMapProvider provides { node, modifier -> InlineMap(node, modifier) },
        LocalNodeTrackMapProvider provides
            { destNum, positions, modifier, selectedPositionTime, onPositionSelected ->
                org.meshtastic.app.map.node.NodeTrackMap(
                    destNum,
                    positions,
                    modifier,
                    selectedPositionTime,
                    onPositionSelected,
                )
            },
        LocalTracerouteMapOverlayInsetsProvider provides getTracerouteMapOverlayInsets(),
        LocalTracerouteMapProvider provides
            { overlay, nodePositions, onMappableCountChanged, modifier ->
                org.meshtastic.app.map.traceroute.TracerouteMap(
                    tracerouteOverlay = overlay,
                    tracerouteNodePositions = nodePositions,
                    onMappableCountChanged = onMappableCountChanged,
                    modifier = modifier,
                )
            },
        LocalNodeMapScreenProvider provides
            { destNum, onNavigateUp ->
                val vm = koinViewModel<NodeMapViewModel>()
                vm.setDestNum(destNum)
                org.meshtastic.app.map.node.NodeMapScreen(vm, onNavigateUp = onNavigateUp)
            },
        LocalTracerouteMapScreenProvider provides
            { destNum, requestId, logUuid, onNavigateUp ->
                val metricsViewModel = koinViewModel<MetricsViewModel> { parametersOf(destNum) }
                metricsViewModel.setNodeId(destNum)

                TracerouteMapScreen(
                    metricsViewModel = metricsViewModel,
                    requestId = requestId,
                    logUuid = logUuid,
                    onNavigateUp = onNavigateUp,
                )
            },
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
        content = content,
    )
}
