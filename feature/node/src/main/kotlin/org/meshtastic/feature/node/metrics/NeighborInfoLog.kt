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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtastic.core.strings.getString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.getNeighborInfoResponse
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.neighbor_info
import org.meshtastic.core.strings.routing_error_no_response
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.Groups
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PersonOff
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
import org.meshtastic.core.ui.util.annotateNeighborInfo
import org.meshtastic.feature.node.component.CooldownIconButton
import org.meshtastic.feature.node.detail.NodeRequestEffect

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NeighborInfoLogScreen(
    modifier: Modifier = Modifier,
    viewModel: MetricsViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
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
            val lastRequestNeighborsTime by viewModel.lastRequestNeighborsTime.collectAsState()
            MainAppBar(
                title = state.node?.user?.long_name ?: "",
                subtitle = stringResource(Res.string.neighbor_info),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (!state.isLocal) {
                        CooldownIconButton(
                            onClick = { viewModel.requestNeighborInfo() },
                            cooldownTimestamp = lastRequestNeighborsTime,
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
            items(state.neighborInfoRequests, key = { it.uuid }) { log ->
                val result =
                    remember(state.neighborInfoResults, log.fromRadio.packet?.id) {
                        state.neighborInfoResults.find {
                            it.fromRadio.packet?.decoded?.request_id == log.fromRadio.packet?.id
                        }
                    }

                val time =
                    DateUtils.formatDateTime(
                        context,
                        log.received_date,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
                    )
                val text = if (result != null) "Success" else stringResource(Res.string.routing_error_no_response)
                val icon = if (result != null) MeshtasticIcons.Groups else MeshtasticIcons.PersonOff
                var expanded by remember { mutableStateOf(false) }

                Box {
                    MetricLogItem(
                        icon = icon,
                        text = "$time - $text",
                        contentDescription = stringResource(Res.string.neighbor_info),
                        modifier =
                        Modifier.combinedClickable(onLongClick = { expanded = true }) {
                            result
                                ?.fromRadio
                                ?.packet
                                ?.getNeighborInfoResponse(
                                    ::getUsername,
                                    header = getString(Res.string.neighbor_info),
                                )
                                ?.let {
                                    val message =
                                        annotateNeighborInfo(
                                            it,
                                            statusGreen = statusGreen,
                                            statusYellow = statusYellow,
                                            statusOrange = statusOrange,
                                        )
                                    viewModel.showLogDetail(Res.string.neighbor_info, message)
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
