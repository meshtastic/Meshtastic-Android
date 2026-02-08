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

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.getTracerouteResponse
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.routing_error_no_response
import org.meshtastic.core.strings.traceroute
import org.meshtastic.core.strings.traceroute_diff
import org.meshtastic.core.strings.traceroute_direct
import org.meshtastic.core.strings.traceroute_duration
import org.meshtastic.core.strings.traceroute_hops
import org.meshtastic.core.strings.traceroute_log
import org.meshtastic.core.strings.traceroute_route_back_to_us
import org.meshtastic.core.strings.traceroute_route_towards_dest
import org.meshtastic.core.strings.traceroute_time_and_text
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.Group
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PersonOff
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Route
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.ui.util.annotateTraceroute
import org.meshtastic.feature.map.model.TracerouteOverlay
import org.meshtastic.feature.node.component.CooldownIconButton
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.proto.RouteDiscovery

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun TracerouteLogScreen(
    modifier: Modifier = Modifier,
    viewModel: MetricsViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onViewOnMap: (requestId: Int, responseLogUuid: String) -> Unit = { _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NodeRequestEffect.ShowFeedback -> {
                    @Suppress("SpreadOperator")
                    snackbarHostState.showSnackbar(getString(effect.resource, *effect.args.toTypedArray()))
                }
            }
        }
    }

    fun getUsername(nodeNum: Int): String =
        with(viewModel.getUser(nodeNum)) { "${long_name ?: ""} (${short_name ?: ""})" }

    val context = LocalContext.current
    val statusGreen = MaterialTheme.colorScheme.StatusGreen
    val statusYellow = MaterialTheme.colorScheme.StatusYellow
    val statusOrange = MaterialTheme.colorScheme.StatusOrange

    Scaffold(
        topBar = {
            val lastTracerouteTime by viewModel.lastTraceRouteTime.collectAsState()
            MainAppBar(
                title = state.node?.user?.long_name ?: "",
                subtitle = stringResource(Res.string.traceroute_log),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (!state.isLocal) {
                        CooldownIconButton(
                            onClick = { viewModel.requestTraceroute() },
                            cooldownTimestamp = lastTracerouteTime,
                        ) {
                            Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                        }
                    }
                },
                onClickChip = {},
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(state.tracerouteRequests, key = { it.uuid }) { log ->
                val result =
                    remember(state.tracerouteRequests, log.fromRadio.packet?.id) {
                        state.tracerouteResults.find {
                            it.fromRadio.packet?.decoded?.request_id == log.fromRadio.packet?.id
                        }
                    }
                val route = remember(result) { result?.fromRadio?.packet?.fullRouteDiscovery }

                val time =
                    DateUtils.formatDateTime(
                        context,
                        log.received_date,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
                    )
                val (text, icon) = route.getTextAndIcon()
                var expanded by remember { mutableStateOf(false) }

                val tracerouteDetailsAnnotated: AnnotatedString? =
                    result?.let { res ->
                        if (route != null && route.route.isNotEmpty() && route.route_back.isNotEmpty()) {
                            val seconds =
                                (res.received_date - log.received_date).coerceAtLeast(0).toDouble() / MS_PER_SEC
                            val annotatedBase =
                                annotateTraceroute(
                                    res.fromRadio.packet?.getTracerouteResponse(
                                        ::getUsername,
                                        headerTowards = stringResource(Res.string.traceroute_route_towards_dest),
                                        headerBack = stringResource(Res.string.traceroute_route_back_to_us),
                                    ),
                                    statusGreen = statusGreen,
                                    statusYellow = statusYellow,
                                    statusOrange = statusOrange,
                                )
                            val durationText = stringResource(Res.string.traceroute_duration, "%.1f".format(seconds))
                            buildAnnotatedString {
                                append(annotatedBase)
                                append("\n\n$durationText")
                            }
                        } else {
                            // For cases where there's a result but no full route, display plain text
                            res.fromRadio.packet
                                ?.getTracerouteResponse(
                                    ::getUsername,
                                    headerTowards = stringResource(Res.string.traceroute_route_towards_dest),
                                    headerBack = stringResource(Res.string.traceroute_route_back_to_us),
                                )
                                ?.let { AnnotatedString(it) }
                        }
                    }
                val overlay =
                    route?.let {
                        TracerouteOverlay(
                            requestId = log.fromRadio.packet?.id ?: 0,
                            forwardRoute = it.route,
                            returnRoute = it.route_back,
                        )
                    }

                Box {
                    MetricLogItem(
                        icon = icon,
                        text = stringResource(Res.string.traceroute_time_and_text, time, text),
                        contentDescription = stringResource(Res.string.traceroute),
                        modifier =
                        Modifier.combinedClickable(onLongClick = { expanded = true }) {
                            val dialogMessage =
                                tracerouteDetailsAnnotated
                                    ?: result
                                        ?.fromRadio
                                        ?.packet
                                        ?.getTracerouteResponse(
                                            ::getUsername,
                                            headerTowards = getString(Res.string.traceroute_route_towards_dest),
                                            headerBack = getString(Res.string.traceroute_route_back_to_us),
                                        )
                                        ?.let {
                                            annotateTraceroute(
                                                it,
                                                statusGreen = statusGreen,
                                                statusYellow = statusYellow,
                                                statusOrange = statusOrange,
                                            )
                                        }
                            dialogMessage?.let {
                                val responseLogUuid = result?.uuid ?: return@combinedClickable
                                viewModel.showTracerouteDetail(
                                    annotatedMessage = it,
                                    requestId = log.fromRadio.packet?.id ?: 0,
                                    responseLogUuid = responseLogUuid,
                                    overlay = overlay,
                                    onViewOnMap = onViewOnMap,
                                    onShowError = { /* Handle error */ },
                                )
                            }
                        },
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DeleteItem {
                            viewModel.deleteLog(log.uuid)
                            expanded = false
                        }
                    }
                }
            }
        }
    }
}

/** Generates a display string and icon based on the route discovery information. */
@Composable
private fun RouteDiscovery?.getTextAndIcon(): Pair<String, ImageVector> = when {
    this == null -> {
        stringResource(Res.string.routing_error_no_response) to MeshtasticIcons.PersonOff
    }
    // A direct route means the sender and receiver are the only two nodes in the route.
    route.size <= 2 && route_back.size <= 2 -> { // also check route_back size for direct to be more robust
        stringResource(Res.string.traceroute_direct) to MeshtasticIcons.Group
    }

    route.size == route_back.size -> {
        val hops = route.size - 2
        pluralStringResource(Res.plurals.traceroute_hops, hops, hops) to MeshtasticIcons.Route
    }

    else -> {
        // Asymmetric route
        val towards = maxOf(0, route.size - 2)
        val back = maxOf(0, route_back.size - 2)
        stringResource(Res.string.traceroute_diff, towards, back) to MeshtasticIcons.Route
    }
}

@PreviewLightDark
@Composable
private fun TracerouteItemPreview() {
    val time =
        DateUtils.formatDateTime(
            LocalContext.current,
            System.currentTimeMillis(),
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
        )
    AppTheme {
        MetricLogItem(
            icon = MeshtasticIcons.Group,
            text = "$time - Direct",
            contentDescription = stringResource(Res.string.traceroute),
        )
    }
}
