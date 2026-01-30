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
 * along with this program.  See the <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.node.metrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
