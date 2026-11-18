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
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.effectiveLongName
import org.meshtastic.core.model.getNeighborInfoResponse
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.neighbor_info
import org.meshtastic.core.resources.neighbor_info_received
import org.meshtastic.core.resources.routing_error_no_response
import org.meshtastic.core.resources.success
import org.meshtastic.core.ui.icon.Groups
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PersonOff
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.ui.util.LocalModemPreset
import org.meshtastic.core.ui.util.annotateNeighborInfo
import org.meshtastic.feature.node.component.CooldownIconButton
import org.meshtastic.feature.node.model.NeighborInfoEntry
import org.meshtastic.feature.node.model.neighborInfoEntries

/**
 * Neighbor-info log with chart and list, built on [BaseMetricScreen]: our requests with their replies, and neighbor
 * info the node sent on its own, newest first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod")
@Composable
fun NeighborInfoLogScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFrame by viewModel.timeFrame.collectAsStateWithLifecycle()
    val availableTimeFrames by viewModel.availableTimeFrames.collectAsStateWithLifecycle()
    val lastRequestNeighborsTime by viewModel.lastRequestNeighborsTime.collectAsStateWithLifecycle()

    fun getUsername(nodeNum: Int): String = with(viewModel.getUser(nodeNum)) { "$long_name ($short_name)" }

    val statusGreen = MaterialTheme.colorScheme.StatusGreen
    val statusYellow = MaterialTheme.colorScheme.StatusYellow
    val statusOrange = MaterialTheme.colorScheme.StatusOrange
    val statusRed = MaterialTheme.colorScheme.StatusRed
    val modemPreset = LocalModemPreset.current
    val header = stringResource(Res.string.neighbor_info)

    val threshold = timeFrame.timeThreshold()
    val entries =
        remember(state.neighborInfoRequests, state.neighborInfoResults, threshold) {
            state.neighborInfoEntries().filter { it.timeSeconds >= threshold }
        }

    BaseMetricScreen(
        modifier = modifier,
        onNavigateUp = onNavigateUp,
        telemetryType = null,
        titleRes = Res.string.neighbor_info,
        nodeName = state.node?.effectiveLongName.orEmpty(),
        data = entries,
        timeProvider = { it.timeSeconds },
        infoData = NEIGHBOR_INFO_INFO_DATA,
        extraActions = {
            if (!state.isLocal && state.canRequestNeighborInfo) {
                CooldownIconButton(
                    onClick = { viewModel.requestNeighborInfo() },
                    cooldownTimestamp = lastRequestNeighborsTime,
                ) {
                    Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                }
            }
        },
        controlPart = {
            TimeFrameSelector(
                selectedTimeFrame = timeFrame,
                availableTimeFrames = availableTimeFrames,
                onTimeFrameSelected = viewModel::setTimeFrame,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
        chartPart = { chartModifier, selectedX, vicoScrollState, onPointSelected ->
            NeighborInfoChart(
                entries = entries,
                vicoScrollState = vicoScrollState,
                selectedX = selectedX,
                onSelectPoint = onPointSelected,
                modifier = chartModifier,
            )
        },
        listPart = { listModifier, _, lazyListState, onCardClick ->
            LazyColumn(modifier = listModifier.fillMaxSize(), state = lazyListState) {
                items(entries, key = { it.log.uuid }) { entry ->
                    val report = entry.report
                    val time = DateFormatter.formatDateTime((report ?: entry.log).received_date)
                    val text =
                        when {
                            entry is NeighborInfoEntry.Received -> stringResource(Res.string.neighbor_info_received)
                            report != null -> stringResource(Res.string.success)
                            else -> stringResource(Res.string.routing_error_no_response)
                        }
                    var expanded by remember { mutableStateOf(false) }

                    Box {
                        MetricLogItem(
                            icon = if (report != null) MeshtasticIcons.Groups else MeshtasticIcons.PersonOff,
                            text = "$time - $text",
                            contentDescription = header,
                            modifier =
                            Modifier.combinedClickable(onLongClick = { expanded = true }) {
                                onCardClick(entry.timeSeconds)
                                report
                                    ?.fromRadio
                                    ?.packet
                                    ?.getNeighborInfoResponse(::getUsername, header = header)
                                    ?.let {
                                        val message =
                                            annotateNeighborInfo(
                                                it,
                                                statusGreen = statusGreen,
                                                statusYellow = statusYellow,
                                                statusOrange = statusOrange,
                                                statusRed = statusRed,
                                                modemPreset = modemPreset,
                                            )
                                        viewModel.showLogDetail(Res.string.neighbor_info, message)
                                    }
                            },
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DeleteItem {
                                // A request's reply goes too, or it would reappear as received.
                                viewModel.deleteLog(entry.log.uuid)
                                (entry as? NeighborInfoEntry.Requested)?.reply?.uuid?.let(viewModel::deleteLog)
                                expanded = false
                            }
                        }
                    }
                }
            }
        },
    )
}
