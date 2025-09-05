/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.settings.radio

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.AdminRoute
import com.geeksville.mesh.navigation.ConfigRoute
import com.geeksville.mesh.navigation.ModuleRoute
import com.geeksville.mesh.navigation.Route
import com.geeksville.mesh.navigation.SettingsRoutes
import com.geeksville.mesh.ui.common.components.TitledCard
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.common.theme.StatusColors.StatusRed
import com.geeksville.mesh.ui.settings.components.SettingsItem
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Suppress("LongMethod")
@Composable
private fun NavButton(@StringRes title: Int, enabled: Boolean, onClick: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Icon(
                        imageVector = Icons.TwoTone.Warning,
                        contentDescription = stringResource(id = R.string.warning),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(text = "${stringResource(title)}?\n")
                    Icon(
                        imageVector = Icons.TwoTone.Warning,
                        contentDescription = stringResource(id = R.string.warning),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(modifier = Modifier.weight(1f), onClick = { showDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showDialog = false
                            onClick()
                        },
                    ) {
                        Text(stringResource(R.string.send))
                    }
                }
            },
        )
    }

    Column {
        Spacer(modifier = Modifier.height(4.dp))
        Button(modifier = Modifier.fillMaxWidth().height(48.dp), enabled = enabled, onClick = { showDialog = true }) {
            Text(text = stringResource(title))
        }
    }
}

@Suppress("LongMethod")
@Composable
fun RadioConfigItemList(
    state: RadioConfigState,
    isManaged: Boolean,
    excludedModulesUnlocked: Boolean = false,
    onRouteClick: (Enum<*>) -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
    onNavigate: (Route) -> Unit,
) {
    val enabled = state.connected && !state.responseState.isWaiting() && !isManaged
    var modules by remember { mutableStateOf(ModuleRoute.filterExcludedFrom(state.metadata)) }

    LaunchedEffect(excludedModulesUnlocked) {
        if (excludedModulesUnlocked) {
            modules = ModuleRoute.entries
        } else {
            modules = ModuleRoute.filterExcludedFrom(state.metadata)
        }
    }

    Column {
        TitledCard(title = stringResource(R.string.radio_configuration)) {
            if (isManaged) {
                ManagedMessage()
            }

            ConfigRoute.filterExcludedFrom(state.metadata).forEach {
                SettingsItem(text = stringResource(it.title), leadingIcon = it.icon, enabled = enabled) {
                    onRouteClick(it)
                }
            }
        }

        TitledCard(title = stringResource(R.string.module_settings), modifier = Modifier.padding(top = 16.dp)) {
            if (isManaged) {
                ManagedMessage()
            }

            modules.forEach {
                SettingsItem(text = stringResource(it.title), leadingIcon = it.icon, enabled = enabled) {
                    onRouteClick(it)
                }
            }
        }
    }

    if (state.isLocal) {
        TitledCard(title = stringResource(R.string.backup_restore), modifier = Modifier.padding(top = 16.dp)) {
            if (isManaged) {
                ManagedMessage()
            }

            SettingsItem(
                text = stringResource(R.string.import_configuration),
                leadingIcon = Icons.Default.Download,
                enabled = enabled,
                onClick = onImport,
            )
            SettingsItem(
                text = stringResource(R.string.export_configuration),
                leadingIcon = Icons.Default.Upload,
                enabled = enabled,
                onClick = onExport,
            )
        }
    }

    Column(modifier = Modifier.padding(top = 16.dp)) {
        AdminRoute.entries.forEach { NavButton(it.title, enabled) { onRouteClick(it) } }
    }

    TitledCard(title = stringResource(R.string.advanced_title), modifier = Modifier.padding(top = 16.dp)) {
        if (isManaged) {
            ManagedMessage()
        }

        SettingsItem(
            text = stringResource(R.string.clean_node_database_title),
            enabled = enabled,
            onClick = { onNavigate(SettingsRoutes.CleanNodeDb) },
        )

        SettingsItem(
            text = stringResource(R.string.debug_panel),
            leadingIcon = Icons.Rounded.BugReport,
            enabled = enabled,
            onClick = { onNavigate(SettingsRoutes.DebugPanel) },
        )
    }
}

private const val UNLOCK_CLICK_COUNT = 5 // Number of clicks required to unlock excluded modules.
private const val UNLOCK_TIMEOUT_SECONDS = 3 // Timeout in seconds to reset the click counter.

@Composable
fun RadioConfigMenuActions(modifier: Modifier = Modifier, viewModel: UIViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var counter by remember { mutableIntStateOf(0) }
    LaunchedEffect(counter) {
        if (counter > 0 && counter < UNLOCK_CLICK_COUNT) {
            delay(UNLOCK_TIMEOUT_SECONDS.seconds)
            counter = 0
        }
    }
    IconButton(
        enabled = counter < UNLOCK_CLICK_COUNT,
        onClick = {
            counter++
            if (counter == UNLOCK_CLICK_COUNT) {
                viewModel.unlockExcludedModules()
                Toast.makeText(context, context.getString(R.string.modules_unlocked), Toast.LENGTH_LONG).show()
            }
        },
        modifier = modifier,
    ) {}
}

@Preview(showBackground = true)
@Composable
private fun RadioSettingsScreenPreview() = AppTheme {
    RadioConfigItemList(
        state = RadioConfigState(isLocal = true, connected = true),
        isManaged = false,
        onNavigate = { _ -> },
    )
}

@Composable
private fun ManagedMessage() {
    Text(
        text = stringResource(R.string.message_device_managed),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.StatusRed,
    )
}

@Preview(showBackground = true)
@Composable
private fun RadioSettingsScreenManagedPreview() = AppTheme {
    RadioConfigItemList(
        state = RadioConfigState(isLocal = true, connected = true),
        isManaged = true,
        onNavigate = { _ -> },
    )
}
