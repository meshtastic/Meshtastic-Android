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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
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

/**
 * A generic chart host for Meshtastic metric charts. Handles common boilerplate for markers, scrolling, and point
 * selection synchronization.
 *
 * @param modelProducer The [CartesianChartModelProducer] for the chart.
 * @param layers The chart layers (e.g., LineCartesianLayer).
 * @param modifier The modifier for the chart host.
 * @param startAxis The start vertical axis.
 * @param endAxis The end vertical axis.
 * @param bottomAxis The bottom horizontal axis.
 * @param marker The marker to show on interaction.
 * @param selectedX The currently selected X value (used for persistent markers).
 * @param onPointSelected Callback when a point is selected via interaction.
 * @param vicoScrollState The scroll state for the chart.
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
 *
 * @param chartPart The Composable function to render the chart part of the screen.
 * @param listPart The Composable function to render the list part of the screen.
 * @param modifier The modifier for the adaptive layout container.
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
