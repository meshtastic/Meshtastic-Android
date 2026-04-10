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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.clear
import org.meshtastic.core.resources.collapse_chart
import org.meshtastic.core.resources.expand_chart
import org.meshtastic.core.resources.logs
import org.meshtastic.core.resources.position_log
import org.meshtastic.core.resources.save
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.BarChart
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.List
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Save
import org.meshtastic.core.ui.util.LocalNodeTrackMapProvider

@Composable
private fun ActionButtons(
    clearButtonEnabled: Boolean,
    onClear: () -> Unit,
    saveButtonEnabled: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onClear,
            enabled = clearButtonEnabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(imageVector = MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.clear))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(Res.string.clear))
        }

        OutlinedButton(modifier = Modifier.weight(1f), onClick = onSave, enabled = saveButtonEnabled) {
            Icon(imageVector = MeshtasticIcons.Save, contentDescription = stringResource(Res.string.save))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(Res.string.save))
        }
    }
}

@Suppress("LongMethod")
@Composable
fun PositionLogScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val exportPositionLauncher =
        org.meshtastic.core.ui.util.rememberSaveFileLauncher { uri -> viewModel.savePositionCSV(uri) }

    var clearButtonEnabled by rememberSaveable(state.positionLogs) { mutableStateOf(state.positionLogs.isNotEmpty()) }
    var isMapExpanded by remember { mutableStateOf(false) }

    val trackMap = LocalNodeTrackMapProvider.current
    val destNum = state.node?.num ?: 0

    Scaffold(
        topBar = {
            MainAppBar(
                title = state.node?.user?.long_name ?: "",
                subtitle =
                stringResource(Res.string.position_log) +
                    " (${state.positionLogs.size} ${stringResource(Res.string.logs)})",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(onClick = { isMapExpanded = !isMapExpanded }) {
                        Icon(
                            imageVector = if (isMapExpanded) MeshtasticIcons.List else MeshtasticIcons.BarChart,
                            contentDescription =
                            stringResource(
                                if (isMapExpanded) Res.string.collapse_chart else Res.string.expand_chart,
                            ),
                        )
                    }
                    if (!state.isLocal) {
                        IconButton(onClick = { viewModel.requestPosition() }) {
                            Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                        }
                    }
                },
                onClickChip = {},
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            AdaptiveMetricLayout(
                isChartExpanded = isMapExpanded,
                chartPart = { modifier -> trackMap(destNum, state.positionLogs, modifier) },
                listPart = { modifier ->
                    BoxWithConstraints(modifier = modifier) {
                        val compactWidth = maxWidth < 600.dp
                        Column {
                            val textStyle =
                                if (compactWidth) {
                                    MaterialTheme.typography.bodySmall
                                } else {
                                    LocalTextStyle.current
                                }
                            CompositionLocalProvider(LocalTextStyle provides textStyle) {
                                PositionLogHeader(compactWidth)
                                PositionList(compactWidth, state.positionLogs, state.displayUnits)
                            }

                            ActionButtons(
                                clearButtonEnabled = clearButtonEnabled,
                                onClear = {
                                    clearButtonEnabled = false
                                    viewModel.clearPosition()
                                },
                                saveButtonEnabled = state.hasPositionLogs(),
                                onSave = { exportPositionLauncher("position.csv", "text/csv") },
                            )
                        }
                    }
                },
            )
        }
    }
}
