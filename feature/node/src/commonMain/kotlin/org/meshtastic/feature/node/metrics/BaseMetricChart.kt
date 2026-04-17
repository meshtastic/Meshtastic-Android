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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.FadingEdges
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberFadingEdges
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.collapse_chart
import org.meshtastic.core.resources.expand_chart
import org.meshtastic.core.resources.info
import org.meshtastic.core.resources.logs
import org.meshtastic.core.resources.save
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.BarChart
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.List
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Save

/** Minimum x-step (in seconds) to prevent the default GCD from producing a value of 1 with irregular timestamps. */
private const val MIN_X_STEP_SECONDS = 60.0

/**
 * A generic chart host for Meshtastic metric charts. Handles common boilerplate for markers, scrolling, and point
 * selection synchronization.
 *
 * Uses [FadingEdges] to indicate scrollable content beyond the visible area, and accepts optional [Decoration]s for
 * reference threshold lines/bands.
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
    decorations: List<Decoration> = emptyList(),
    selectedX: Double? = null,
    onPointSelected: ((Double) -> Unit)? = null,
    vicoScrollState: VicoScrollState = rememberVicoScrollState(),
) {
    // Key on layer count so Compose rebuilds the entire subtree when legend chip toggles
    // add/remove layers. rememberCartesianChart uses vararg internally, so changing the
    // argument count without a key corrupts the slot table.
    key(layers.size) {
        val zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = Zoom.Content)

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
                fadingEdges = rememberFadingEdges(),
                decorations = decorations,
                // Telemetry timestamps arrive at irregular intervals. Without an explicit
                // x-step, Vico computes the GCD of consecutive x-value differences which can
                // be as small as 1 second, making the chart logically enormous. A 60-second
                // floor keeps the internal slot count reasonable for any practical interval.
                getXStep = { model -> maxOf(model.getXDeltaGcd(), MIN_X_STEP_SECONDS) },
            ),
            modelProducer = modelProducer,
            modifier = modifier,
            scrollState = vicoScrollState,
            zoomState = zoomState,
        )
    }
}

/**
 * Common scaffold for all metric chart composables. Provides:
 * - A [Column] container with the supplied [modifier]
 * - An empty-data guard (returns early when [isEmpty] is true)
 * - A remembered [CartesianChartModelProducer] passed to [content]
 * - A trailing [Legend] strip
 *
 * @param isEmpty Whether the chart data is empty — when true, nothing is rendered.
 * @param legendData Legend items shown below the chart.
 * @param hiddenSet Indices of hidden legend items (toggleable legend).
 * @param onToggle Callback when a legend item is toggled; when null, a read-only legend is rendered.
 * @param content Builder lambda receiving the [CartesianChartModelProducer] and a standard `Modifier.weight(1f)`
 *   suitable for the chart area.
 *
 * A single [CartesianChartModelProducer] is created per scaffold instance. Vico forbids swapping the producer attached
 * to a live [CartesianChartHost] (it throws "A new `CartesianChartModelProducer` was provided…"), so callers must push
 * new data through [CartesianChartModelProducer.runTransaction] instead of recreating the producer. Keying the scaffold
 * on external state (e.g. a selected channel) caused exactly that crash, so the previous `key` parameter was removed.
 */
@Composable
fun MetricChartScaffold(
    isEmpty: Boolean,
    legendData: List<LegendData>,
    modifier: Modifier = Modifier,
    hiddenSet: Set<Int> = emptySet(),
    onToggle: ((Int) -> Unit)? = null,
    content: @Composable ColumnScope.(CartesianChartModelProducer, Modifier) -> Unit,
) {
    Column(modifier = modifier) {
        if (isEmpty) return@Column
        val modelProducer = remember { CartesianChartModelProducer() }
        val chartModifier = Modifier.weight(1f).padding(horizontal = 8.dp).padding(bottom = 0.dp)
        content(modelProducer, chartModifier)
        Legend(
            legendData = legendData,
            modifier = Modifier.padding(top = 0.dp),
            hiddenSet = hiddenSet,
            onToggle = onToggle,
        )
    }
}

/**
 * An adaptive layout for metric screens. Uses a split Row for wide screens (tablets/landscape) and a stacked Column for
 * narrow screens (phones). When [isChartExpanded] is true, the card list is hidden and the chart fills the available
 * space.
 */
@Composable
fun AdaptiveMetricLayout(
    chartPart: @Composable (Modifier) -> Unit,
    listPart: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    isChartExpanded: Boolean = false,
) {
    BoxWithConstraints(modifier = modifier) {
        val isExpanded = maxWidth >= 600.dp
        if (isExpanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                chartPart(Modifier.weight(1f).fillMaxHeight())
                AnimatedVisibility(visible = !isChartExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                    listPart(Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                chartPart(
                    if (isChartExpanded) {
                        Modifier.fillMaxWidth().weight(1f)
                    } else {
                        Modifier.fillMaxWidth().fillMaxHeight(fraction = 0.45f)
                    },
                )
                AnimatedVisibility(visible = !isChartExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                    listPart(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

/**
 * A high-level template for metric screens that handles the Scaffold, AppBar, adaptive layout, and chart-to-list
 * synchronisation.
 *
 * @param extraActions Additional composable actions rendered in the app bar before the standard buttons (e.g. a
 *   cooldown traceroute button).
 * @param onExportCsv When non-null, a Save [IconButton] is rendered in the app bar that invokes this callback. This
 *   centralises the CSV export affordance so individual screens only need to provide the export logic.
 */
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
    onRequestTelemetry: (() -> Unit)? = null,
    onExportCsv: (() -> Unit)? = null,
    extraActions: @Composable () -> Unit = {},
    chartPart: @Composable (Modifier, Double?, VicoScrollState, (Double) -> Unit) -> Unit,
    listPart: @Composable (Modifier, Double?, LazyListState, (Double) -> Unit) -> Unit,
    controlPart: @Composable () -> Unit = {},
) {
    var displayInfoDialog by rememberSaveable { mutableStateOf(false) }
    var isChartExpanded by rememberSaveable { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val vicoScrollState =
        rememberVicoScrollState(
            autoScroll = Scroll.Absolute.End,
            autoScrollCondition = AutoScrollCondition.OnModelGrowth,
        )
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
                    extraActions()
                    if (onExportCsv != null && data.isNotEmpty()) {
                        IconButton(onClick = onExportCsv) {
                            Icon(
                                imageVector = MeshtasticIcons.Save,
                                contentDescription = stringResource(Res.string.save),
                            )
                        }
                    }
                    IconToggleButton(checked = isChartExpanded, onCheckedChange = { isChartExpanded = it }) {
                        Icon(
                            imageVector =
                            if (isChartExpanded) {
                                MeshtasticIcons.List
                            } else {
                                MeshtasticIcons.BarChart
                            },
                            contentDescription =
                            stringResource(
                                if (isChartExpanded) Res.string.collapse_chart else Res.string.expand_chart,
                            ),
                        )
                    }
                    if (infoData.isNotEmpty()) {
                        IconButton(onClick = { displayInfoDialog = true }) {
                            Icon(
                                imageVector = MeshtasticIcons.Info,
                                contentDescription = stringResource(Res.string.info),
                            )
                        }
                    }
                    if (telemetryType != null) {
                        IconButton(
                            onClick = { onRequestTelemetry?.invoke() },
                            modifier = Modifier.testTag("refresh_button"),
                        ) {
                            Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                        }
                    }
                },
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (displayInfoDialog) {
                LegendInfoDialog(infoData = infoData, onDismiss = { displayInfoDialog = false })
            }

            controlPart()

            AdaptiveMetricLayout(
                isChartExpanded = isChartExpanded,
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
                    listPart(modifier, selectedX, lazyListState) { x ->
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
