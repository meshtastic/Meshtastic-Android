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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.evaluateTracerouteMapAvailability
import org.meshtastic.core.model.fullRouteDiscovery
import org.meshtastic.core.model.getTracerouteResponse
import org.meshtastic.core.model.toMessageRes
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.close
import org.meshtastic.core.strings.delete
import org.meshtastic.core.strings.routing_error_no_response
import org.meshtastic.core.strings.traceroute
import org.meshtastic.core.strings.traceroute_diff
import org.meshtastic.core.strings.traceroute_direct
import org.meshtastic.core.strings.traceroute_duration
import org.meshtastic.core.strings.traceroute_hops
import org.meshtastic.core.strings.traceroute_route_back_to_us
import org.meshtastic.core.strings.traceroute_route_towards_dest
import org.meshtastic.core.strings.traceroute_time_and_text
import org.meshtastic.core.strings.view_on_map
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SNR_FAIR_THRESHOLD
import org.meshtastic.core.ui.component.SNR_GOOD_THRESHOLD
import org.meshtastic.core.ui.component.SimpleAlertDialog
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.feature.map.model.TracerouteOverlay
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.feature.node.metrics.CommonCharts.MS_PER_SEC
import org.meshtastic.proto.MeshProtos

private data class TracerouteDialog(
    val message: AnnotatedString,
    val requestId: Int,
    val responseLogUuid: String,
    val overlay: TracerouteOverlay?,
)

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
                    snackbarHostState.showSnackbar(
                        com.meshtastic.core.strings.getString(effect.resource, *effect.args.toTypedArray()),
                    )
                }
            }
        }
    }

    fun getUsername(nodeNum: Int): String = with(viewModel.getUser(nodeNum)) { "$longName ($shortName)" }

    var showDialog by remember { mutableStateOf<TracerouteDialog?>(null) }
    var errorMessageRes by remember { mutableStateOf<StringResource?>(null) }
    val context = LocalContext.current

    TracerouteLogDialogs(
        dialog = showDialog,
        errorMessageRes = errorMessageRes,
        viewModel = viewModel,
        onViewOnMap = onViewOnMap,
        onShowErrorMessageRes = { errorMessageRes = it },
        onDismissDialog = { showDialog = null },
        onDismissError = { errorMessageRes = null },
    )

    Scaffold(
        topBar = {
            MainAppBar(
                title = state.node?.user?.longName ?: "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (!state.isLocal) {
                        IconButton(onClick = { viewModel.requestTraceroute() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
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
                    remember(state.tracerouteRequests, log.fromRadio.packet.id) {
                        state.tracerouteResults.find {
                            it.fromRadio.packet.decoded.requestId == log.fromRadio.packet.id
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
                        if (route != null && route.routeList.isNotEmpty() && route.routeBackList.isNotEmpty()) {
                            val seconds =
                                (res.received_date - log.received_date).coerceAtLeast(0).toDouble() / MS_PER_SEC
                            val annotatedBase =
                                annotateTraceroute(
                                    res.fromRadio.packet.getTracerouteResponse(
                                        ::getUsername,
                                        headerTowards = stringResource(Res.string.traceroute_route_towards_dest),
                                        headerBack = stringResource(Res.string.traceroute_route_back_to_us),
                                    ),
                                )
                            val durationText = stringResource(Res.string.traceroute_duration, "%.1f".format(seconds))
                            buildAnnotatedString {
                                append(annotatedBase)
                                append("\n\n$durationText")
                            }
                        } else {
                            // For cases where there's a result but no full route, display plain text
                            res.fromRadio.packet
                                .getTracerouteResponse(
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
                            requestId = log.fromRadio.packet.id,
                            forwardRoute = it.routeList,
                            returnRoute = it.routeBackList,
                        )
                    }

                Box {
                    TracerouteItem(
                        icon = icon,
                        text = stringResource(Res.string.traceroute_time_and_text, time, text),
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
                                        ?.let { AnnotatedString(it) }
                            dialogMessage?.let {
                                val responseLogUuid = result?.uuid ?: return@combinedClickable
                                showDialog =
                                    TracerouteDialog(
                                        message = it,
                                        requestId = log.fromRadio.packet.id,
                                        responseLogUuid = responseLogUuid,
                                        overlay = overlay,
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

@Composable
private fun TracerouteLogDialogs(
    dialog: TracerouteDialog?,
    errorMessageRes: StringResource?,
    viewModel: MetricsViewModel,
    onViewOnMap: (requestId: Int, responseLogUuid: String) -> Unit,
    onShowErrorMessageRes: (StringResource) -> Unit,
    onDismissDialog: () -> Unit,
    onDismissError: () -> Unit,
) {
    dialog?.let { dialogState ->
        val snapshotPositionsFlow =
            remember(dialogState.responseLogUuid) { viewModel.tracerouteSnapshotPositions(dialogState.responseLogUuid) }
        val snapshotPositions by snapshotPositionsFlow.collectAsStateWithLifecycle(emptyMap<Int, MeshProtos.Position>())
        SimpleAlertDialog(
            title = Res.string.traceroute,
            text = { SelectionContainer { Text(text = dialogState.message) } },
            confirmText = stringResource(Res.string.view_on_map),
            onConfirm = {
                val positionedNodeNums =
                    if (snapshotPositions.isNotEmpty()) {
                        snapshotPositions.keys
                    } else {
                        viewModel.positionedNodeNums()
                    }
                val availability =
                    evaluateTracerouteMapAvailability(
                        forwardRoute = dialogState.overlay?.forwardRoute.orEmpty(),
                        returnRoute = dialogState.overlay?.returnRoute.orEmpty(),
                        positionedNodeNums = positionedNodeNums,
                    )
                availability.toMessageRes()?.let(onShowErrorMessageRes)
                    ?: onViewOnMap(dialogState.requestId, dialogState.responseLogUuid)
                onDismissDialog()
            },
            onDismiss = onDismissDialog,
        )
    }

    errorMessageRes?.let { res ->
        SimpleAlertDialog(
            title = Res.string.traceroute,
            text = { Text(text = stringResource(res)) },
            dismissText = stringResource(Res.string.close),
            onDismiss = onDismissError,
        )
    }
}

@Composable
private fun DeleteItem(onClick: () -> Unit) {
    DropdownMenuItem(
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun TracerouteItem(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = stringResource(Res.string.traceroute))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Generates a display string and icon based on the route discovery information. */
@Composable
private fun MeshProtos.RouteDiscovery?.getTextAndIcon(): Pair<String, ImageVector> = when {
    this == null -> {
        stringResource(Res.string.routing_error_no_response) to Icons.Default.PersonOff
    }
    // A direct route means the sender and receiver are the only two nodes in the route.
    routeCount <= 2 && routeBackCount <= 2 -> { // also check routeBackCount for direct to be more robust
        stringResource(Res.string.traceroute_direct) to Icons.Default.Group
    }

    routeCount == routeBackCount -> {
        val hops = routeCount - 2
        pluralStringResource(Res.plurals.traceroute_hops, hops, hops) to Icons.Default.Groups
    }

    else -> {
        // Asymmetric route
        val towards = maxOf(0, routeCount - 2)
        val back = maxOf(0, routeBackCount - 2)
        stringResource(Res.string.traceroute_diff, towards, back) to Icons.Default.Groups
    }
}

/**
 * Converts a raw traceroute string into an [AnnotatedString] with SNR values highlighted according to their quality.
 *
 * @param inString The raw string output from a traceroute response.
 * @return An [AnnotatedString] with SNR values styled, or an empty [AnnotatedString] if input is null.
 */
@Composable
fun annotateTraceroute(inString: String?): AnnotatedString {
    if (inString == null) return buildAnnotatedString { append("") }
    return buildAnnotatedString {
        inString.lines().forEachIndexed { i, line ->
            if (i > 0) append("\n")
            // Example line: "⇊ -8.75 dB SNR"
            if (line.trimStart().startsWith("⇊")) {
                val snrRegex = Regex("""⇊ ([\d\.\?-]+) dB""")
                val snrMatch = snrRegex.find(line)
                val snrValue = snrMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()

                if (snrValue != null) {
                    val snrColor =
                        when {
                            snrValue >= SNR_GOOD_THRESHOLD -> MaterialTheme.colorScheme.StatusGreen
                            snrValue >= SNR_FAIR_THRESHOLD -> MaterialTheme.colorScheme.StatusYellow
                            else -> MaterialTheme.colorScheme.StatusOrange
                        }
                    withStyle(style = SpanStyle(color = snrColor, fontWeight = FontWeight.Bold)) { append(line) }
                } else {
                    // Append line as is if SNR value cannot be parsed
                    append(line)
                }
            } else {
                // Append non-SNR lines as is
                append(line)
            }
        }
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
    AppTheme { TracerouteItem(icon = Icons.Default.Group, text = "$time - Direct") }
}
