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

package com.geeksville.mesh.ui.radioconfig

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ClientOnlyProtos.DeviceProfile
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.AdminRoute
import com.geeksville.mesh.navigation.ConfigRoute
import com.geeksville.mesh.navigation.ModuleRoute
import com.geeksville.mesh.navigation.Route
import com.geeksville.mesh.navigation.getNavRouteFrom
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.radioconfig.components.EditDeviceProfileDialog
import com.geeksville.mesh.ui.radioconfig.components.PacketResponseStateDialog
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun RadioConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: RadioConfigViewModel = hiltViewModel(),
    uiViewModel: UIViewModel = hiltViewModel(),
    onNavigate: (Route) -> Unit = {}
) {
    val node by viewModel.destNode.collectAsStateWithLifecycle()
    val ourNode by uiViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val isLocal = node?.num == ourNode?.num
    val nodeName: String? = node?.user?.longName?.let {
        if (!isLocal) {
            "$it (" + stringResource(R.string.remote) + ")"
        } else {
            it
        }
    }

    nodeName?.let {
        uiViewModel.setTitle(it)
    }

    val excludedModulesUnlocked by uiViewModel.excludedModulesUnlocked.collectAsStateWithLifecycle()

    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    var isWaiting by remember { mutableStateOf(false) }
    if (isWaiting) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = {
                isWaiting = false
                viewModel.clearPacketResponse()
            },
            onComplete = {
                getNavRouteFrom(state.route)?.let { route ->
                    isWaiting = false
                    viewModel.clearPacketResponse()
                    onNavigate(route)
                }
            },
        )
    }

    var deviceProfile by remember { mutableStateOf<DeviceProfile?>(null) }
    var showEditDeviceProfileDialog by remember { mutableStateOf(false) }

    val importConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            showEditDeviceProfileDialog = true
            it.data?.data?.let { uri ->
                viewModel.importProfile(uri) { profile -> deviceProfile = profile }
            }
        }
    }

    val exportConfigLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri -> viewModel.exportProfile(uri, deviceProfile!!) }
        }
    }

    if (showEditDeviceProfileDialog) {
        EditDeviceProfileDialog(
            title = if (deviceProfile != null) {
                stringResource(R.string.import_configuration)
            } else {
                stringResource(R.string.export_configuration)
            },
            deviceProfile = deviceProfile ?: viewModel.currentDeviceProfile,
            onConfirm = {
                showEditDeviceProfileDialog = false
                if (deviceProfile != null) {
                    viewModel.installProfile(it)
                } else {
                    deviceProfile = it
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/*"
                        putExtra(Intent.EXTRA_TITLE, "device_profile.cfg")
                    }
                    exportConfigLauncher.launch(intent)
                }
            },
            onDismiss = {
                showEditDeviceProfileDialog = false
                deviceProfile = null
            }
        )
    }

    RadioConfigItemList(
        modifier = modifier,
        state = state,
        excludedModulesUnlocked = excludedModulesUnlocked,
        onRouteClick = { route ->
            isWaiting = true
            viewModel.setResponseStateLoading(route)
        },
        onImport = {
            viewModel.clearPacketResponse()
            deviceProfile = null
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/*"
            }
            importConfigLauncher.launch(intent)
        },
        onExport = {
            viewModel.clearPacketResponse()
            deviceProfile = null
            showEditDeviceProfileDialog = true
        },
    )
}

@Composable
fun NavCard(
    title: String,
    enabled: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.TwoTone.KeyboardArrowRight, "trailingIcon",
                modifier = Modifier.wrapContentSize(),
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun NavButton(@StringRes title: Int, enabled: Boolean, onClick: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = {},
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.Warning,
                        contentDescription = stringResource(id = R.string.warning),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "${stringResource(title)}?\n"
                    )
                    Icon(
                        imageVector = Icons.TwoTone.Warning,
                        contentDescription = stringResource(id = R.string.warning),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showDialog = false },
                    ) { Text(stringResource(R.string.cancel)) }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showDialog = false
                            onClick()
                        },
                    ) { Text(stringResource(R.string.send)) }
                }
            }
        )
    }

    Column {
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled,
            onClick = { showDialog = true },
        ) { Text(text = stringResource(title)) }
    }
}

@Composable
private fun RadioConfigItemList(
    state: RadioConfigState,
    excludedModulesUnlocked: Boolean = false,
    modifier: Modifier = Modifier,
    onRouteClick: (Enum<*>) -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    val enabled = state.connected && !state.responseState.isWaiting()
    var modules by remember { mutableStateOf(ModuleRoute.filterExcludedFrom(state.metadata)) }
    LaunchedEffect(excludedModulesUnlocked) {
        if (excludedModulesUnlocked) {
            modules = ModuleRoute.entries
        } else {
            modules = ModuleRoute.filterExcludedFrom(state.metadata)
        }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item { PreferenceCategory(stringResource(R.string.radio_configuration)) }
        items(ConfigRoute.filterExcludedFrom(state.metadata)) {
            NavCard(
                title = stringResource(it.title),
                icon = it.icon,
                enabled = enabled
            ) { onRouteClick(it) }
        }

        item { PreferenceCategory(stringResource(R.string.module_settings)) }
        items(modules) {
            NavCard(
                title = stringResource(it.title),
                icon = it.icon,
                enabled = enabled
            ) { onRouteClick(it) }
        }

        if (state.isLocal) {
            item {
                PreferenceCategory(stringResource(R.string.backup_restore))
                NavCard(
                    title = stringResource(R.string.import_configuration),
                    icon = Icons.Default.Download,
                    enabled = enabled,
                    onClick = onImport,
                )
                NavCard(
                    title = stringResource(R.string.export_configuration),
                    icon = Icons.Default.Upload,
                    enabled = enabled,
                    onClick = onExport,
                )
            }
        }

        items(AdminRoute.entries) { NavButton(it.title, enabled) { onRouteClick(it) } }
    }
}

private const val UNLOCK_CLICK_COUNT = 5 // Number of clicks required to unlock excluded modules.
private const val UNLOCK_TIMEOUT_SECONDS = 3 // Timeout in seconds to reset the click counter.

@Composable
fun RadioConfigMenuActions(
    modifier: Modifier = Modifier,
    viewModel: UIViewModel = hiltViewModel(),
) {
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
                Toast.makeText(
                    context,
                    context.getString(R.string.modules_unlocked),
                    Toast.LENGTH_LONG
                ).show()
            }
        },
        modifier = modifier,
    ) {
    }
}

@Preview(showBackground = true)
@Composable
private fun RadioSettingsScreenPreview() = AppTheme {
    RadioConfigItemList(
        RadioConfigState(isLocal = true, connected = true)
    )
}
