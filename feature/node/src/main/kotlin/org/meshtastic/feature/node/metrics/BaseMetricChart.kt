/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.info
import org.meshtastic.core.strings.logs
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh

/**
 * A generic chart host for Meshtastic metric charts. Handles common boilerplate for markers, scrolling, and point
 * selection synchronization.
 */
@Composable
fun GenericMetricChart(
    modelProducer: CartesianChartModelProducer,
    layers: List<LineCartesianLayer>,
    modifier: Modifier = Modifier,
    startAxis: VerticalAxis<Axis.Position.Vertical.Start>? = null,
    endAxis: VerticalAxis<Axis.Position.Vertical.End>? = null,
    bottomAxis: HorizontalAxis<Axis.Position.Horizontal.Bottom>? = null,
    marker: CartesianMarker? = null,
    selectedX: Double? = null,
    onPointSelected: ((Double) -> Unit)? = null,
    vicoScrollState: VicoScrollState = rememberVicoScrollState(),
) {
    val markerVisibilityListener =
        remember(onPointSelected) {
            object : CartesianMarkerVisibilityListener {
                override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                    targets.firstOrNull()?.let { onPointSelected?.invoke(it.x) }
                }

                override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                    targets.firstOrNull()?.let { onPointSelected?.invoke(it.x) }
                }
            }
        }

    CartesianChartHost(
        chart =
        @Suppress("SpreadOperator")
        rememberCartesianChart(
            *layers.toTypedArray(),
            startAxis = startAxis,
            endAxis = endAxis,
            bottomAxis = bottomAxis,
            marker = marker,
            markerVisibilityListener = markerVisibilityListener,
            persistentMarkers = { _ -> if (selectedX != null && marker != null) marker at selectedX else null },
        ),
        modelProducer = modelProducer,
        modifier = modifier,
        scrollState = vicoScrollState,
        zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = Zoom.Content),
    )
}

/**
 * An adaptive layout for metric screens. Uses a split Row for wide screens (tablets/landscape) and a stacked Column for
 * narrow screens (phones).
 */
@Composable
fun AdaptiveMetricLayout(
    chartPart: @Composable (Modifier) -> Unit,
    listPart: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val isExpanded = maxWidth >= 600.dp
        if (isExpanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                chartPart(Modifier.weight(1f).fillMaxHeight())
                listPart(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                chartPart(Modifier.fillMaxWidth().fillMaxHeight(fraction = 0.33f))
                listPart(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** A high-level template for metric screens that handles the Scaffold, AppBar, adaptive layout, and synchronization. */
@Composable
@Suppress("LongMethod")
fun <T> BaseMetricScreen(
    onNavigateUp: () -> Unit,
    telemetryType: TelemetryType?,
    titleRes: StringResource,
    nodeName: String,
    data: List<T>,
    timeProvider: (T) -> Double,
    infoData: List<InfoDialogData> = emptyList(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onRequestTelemetry: (() -> Unit)? = null,
    chartPart: @Composable (Modifier, Double?, VicoScrollState, (Double) -> Unit) -> Unit,
    listPart: @Composable (Modifier, Double?, (Double) -> Unit) -> Unit,
    controlPart: @Composable () -> Unit = {},
) {
    var displayInfoDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val vicoScrollState = rememberVicoScrollState()
    val coroutineScope = rememberCoroutineScope()
    var selectedX by remember { mutableStateOf<Double?>(null) }

    Scaffold(
        topBar = {
            MainAppBar(
                title = nodeName,
                subtitle = stringResource(titleRes) + " (${data.size} ${stringResource(Res.string.logs)})",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (infoData.isNotEmpty()) {
                        IconButton(onClick = { displayInfoDialog = true }) {
                            Icon(imageVector = Icons.Rounded.Info, contentDescription = stringResource(Res.string.info))
                        }
                    }
                    if (telemetryType != null) {
                        IconButton(onClick = { onRequestTelemetry?.invoke() }) {
                            Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                        }
                    }
                },
                onClickChip = {},
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (displayInfoDialog) {
                LegendInfoDialog(infoData = infoData, onDismiss = { displayInfoDialog = false })
            }

            controlPart()

            AdaptiveMetricLayout(
                chartPart = { modifier ->
                    chartPart(modifier, selectedX, vicoScrollState) { x ->
                        selectedX = x
                        val index = data.indexOfFirst { timeProvider(it) == x }
                        if (index != -1) {
                            coroutineScope.launch { lazyListState.animateScrollToItem(index) }
                        }
                    }
                },
                listPart = { modifier ->
                    listPart(modifier, selectedX) { x ->
                        selectedX = x
                        coroutineScope.launch {
                            vicoScrollState.animateScroll(Scroll.Absolute.x(x, CommonCharts.SCROLL_BIAS))
                        }
                    }
                },
            )
        }
    }
}
