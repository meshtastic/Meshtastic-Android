package com.geeksville.mesh.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ClientOnlyProtos.DeviceProfile
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.config.EditDeviceProfileDialog
import com.geeksville.mesh.ui.components.config.PacketResponseStateDialog

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun RadioConfigScreen(
    node: NodeEntity?,
    viewModel: RadioConfigViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit = {}
) {
    val isLocal = node?.num == viewModel.myNodeNum
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
                val route = state.route
                if (ConfigRoute.entries.any { it.name == route } ||
                    ModuleRoute.entries.any { it.name == route }) {
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
            title = if (deviceProfile != null) "Import configuration" else "Export configuration",
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
                        putExtra(Intent.EXTRA_TITLE, "${node!!.num.toUInt()}.cfg")
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
        enabled = state.connected && !isWaiting,
        isLocal = isLocal,
        modifier = modifier,
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
    val color = if (enabled) {
        MaterialTheme.colors.onSurface
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = enabled) { onClick() },
        elevation = 4.dp
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
                    tint = color,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                color = color,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.TwoTone.KeyboardArrowRight, "trailingIcon",
                modifier = Modifier.wrapContentSize(),
                tint = color,
            )
        }
    }
}

@Composable
private fun NavButton(@StringRes title: Int, enabled: Boolean, onClick: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.background,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Warning,
                    contentDescription = "warning",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "${stringResource(title)}?\n")
                Icon(
                    imageVector = Icons.TwoTone.Warning,
                    contentDescription = "warning",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        buttons = {
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
    enabled: Boolean = true,
    isLocal: Boolean = true,
    modifier: Modifier = Modifier,
    onRouteClick: (Enum<*>) -> Unit = {},
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item { PreferenceCategory(stringResource(R.string.device_settings)) }
        items(ConfigRoute.entries) {
            NavCard(title = it.title, icon = it.icon, enabled = enabled) { onRouteClick(it) }
        }

        item { PreferenceCategory(stringResource(R.string.module_settings)) }
        items(ModuleRoute.entries) {
            NavCard(title = it.title, icon = it.icon, enabled = enabled) { onRouteClick(it) }
        }

        if (isLocal) {
            item {
                PreferenceCategory("Backup & Restore")
                NavCard(
                    title = "Import configuration",
                    icon = Icons.Default.Download,
                    enabled = enabled,
                    onClick = onImport,
                )
                NavCard(
                    title = "Export configuration",
                    icon = Icons.Default.Upload,
                    enabled = enabled,
                    onClick = onExport,
                )
            }
        }

        items(AdminRoute.entries) { NavButton(it.title, enabled) { onRouteClick(it) } }
    }
}

@Preview(showBackground = true)
@Composable
private fun RadioSettingsScreenPreview() {
    RadioConfigItemList()
}
