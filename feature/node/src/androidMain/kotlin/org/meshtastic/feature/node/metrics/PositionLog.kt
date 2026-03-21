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

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.common.util.toMeshtasticUri
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.clear
import org.meshtastic.core.resources.save
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.icon.Save
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.detail.NodeRequestEffect
import org.meshtastic.proto.Config
import org.meshtastic.proto.Position

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
actual fun PositionLogScreen(viewModel: MetricsViewModel, onNavigateUp: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NodeRequestEffect.ShowFeedback -> {
                    @Suppress("SpreadOperator")
                    snackbarHostState.showSnackbar(effect.text.resolve())
                }
            }
        }
    }

    val exportPositionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let { uri -> viewModel.savePositionCSV(uri.toMeshtasticUri()) }
            }
        }

    var clearButtonEnabled by rememberSaveable(state.positionLogs) { mutableStateOf(state.positionLogs.isNotEmpty()) }

    Scaffold(
        topBar = {
            MainAppBar(
                title = state.node?.user?.long_name ?: "",
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                actions = {
                    if (!state.isLocal) {
                        IconButton(onClick = { viewModel.requestPosition() }) {
                            Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                        }
                    }
                },
                onClickChip = {},
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        BoxWithConstraints(modifier = Modifier.padding(innerPadding)) {
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
                    onSave = {
                        val intent =
                            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/*"
                                putExtra(Intent.EXTRA_TITLE, "position.csv")
                            }
                        exportPositionLauncher.launch(intent)
                    },
                )
            }
        }
    }
}

@Suppress("MagicNumber")
private val testPosition =
    Position(
        latitude_i = 297604270,
        longitude_i = -953698040,
        altitude = 1230,
        sats_in_view = 7,
        time = nowSeconds.toInt(),
    )

@Preview(showBackground = true)
@Composable
private fun PositionItemPreview() {
    AppTheme {
        PositionItem(compactWidth = false, position = testPosition, system = Config.DisplayConfig.DisplayUnits.METRIC)
    }
}

@PreviewScreenSizes
@Composable
private fun ActionButtonsPreview() {
    AppTheme {
        Column(Modifier.fillMaxSize(), Arrangement.Bottom) {
            ActionButtons(clearButtonEnabled = true, onClear = {}, saveButtonEnabled = true, onSave = {})
        }
    }
}
