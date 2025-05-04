package com.geeksville.mesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.android.getBluetoothPermissions
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.RegionInfo
import com.geeksville.mesh.model.ScanEffect.RequestBluetoothPermission
import com.geeksville.mesh.model.ScanEffect.RequestForCheckLocationPermission
import com.geeksville.mesh.model.ScanEffect.ShowBluetoothIsDisabled
import com.geeksville.mesh.ui.components.SelectBtRadioDialog
import com.geeksville.mesh.ui.theme.AppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsScreenViewModel = hiltViewModel(),
    btScanModel: BTScanModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val btScanUiState by btScanModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(btScanUiState.devices, uiState.isConnected) {
        viewModel.updateNodeInfo()
    }
    val multiplePermissionsState =
        rememberMultiplePermissionsState(context.getBluetoothPermissions().toList())
    LaunchedEffect(Unit) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is Effect.CheckForBluetoothPermission -> {
                    if (multiplePermissionsState.allPermissionsGranted) {
                        btScanModel.scanForDevices()
                    } else {
                        // Show a dialog explaining why we need the permissions
                        multiplePermissionsState.launchMultiplePermissionRequest()
                        btScanModel.scanForDevices()
                    }
                }
            }
        }

        btScanModel.effect.collect { scanEffect ->
            when (scanEffect) {
                RequestForCheckLocationPermission -> TODO()
                RequestBluetoothPermission -> TODO()
                ShowBluetoothIsDisabled -> TODO()
                else -> {}
            }
        }
    }

    Surface {
        Column(modifier = modifier.padding(16.dp)) {
            if (uiState.isConnected) {
                NameAndRegionRow(
                    textValue = uiState.userName,
                    onValueChange = viewModel::onUserNameChange,
                    dropDownExpanded = uiState.regionDropDownExpanded,
                    onToggleDropDown = viewModel::onToggleRegionDropDown,
                    selectedRegion = uiState.selectedRegion,
                    onRegionSelected = viewModel::onRegionSelected,
                )
            }
            RadioConnectionStatusMessage(
                errorMessage = btScanUiState.errorText,
                statusMessage = btScanUiState.statusMessage,
                selectedAddress = btScanModel.selectedAddress,
                connectedRadioFirmwareVersion = uiState.nodeFirmwareVersion,
                isConnected = uiState.isConnected,
            )
            RadioSelectorRadioButtons(
                devices = btScanUiState.devices.values.toList(),
                onDeviceSelected = { btScanModel.onSelected(it) },
                selectedAddress = btScanModel.selectedNotNull,
            )
            AddDeviceByIPAddress(
                ipAddress = uiState.ipAddress,
                onIpAddressChange = viewModel::onIpAddressChange
            )
            if (uiState.isConnected) {
                ProvideLocationCheckBox(enabled = uiState.enableProvideLocation)
            }

            if (btScanModel.selectedAddress == null || btScanModel.selectedAddress == "m") {
                Text(
                    text = stringResource(R.string.warning_not_paired),
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .alpha(0.7f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            AddRadioFloatingActionButton(
                onClick = viewModel::onAddRadioButtonClicked,
                showScanningProgress = btScanUiState.scanning
            )
        }

        if (btScanUiState.showScanResults) {
            SelectBtRadioDialog(
                devices = btScanUiState.scanResult,
                onRadioSelected = { btScanModel.onSelected(it) },
                onDismissRequest = { btScanModel.onDismissScanResults() },
            )
        }
    }
}

@Composable
private fun NameAndRegionRow(
    modifier: Modifier = Modifier,
    textValue: String,
    onValueChange: (String) -> Unit,
    dropDownExpanded: Boolean,
    onToggleDropDown: () -> Unit,
    selectedRegion: RegionInfo,
    onRegionSelected: (RegionInfo) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = textValue,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.your_name)) },
            singleLine = true,
            modifier = Modifier,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.textFieldColors(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
            )
        )
        RegionSelector(
            dropDownExpanded = dropDownExpanded,
            onToggleDropDown = onToggleDropDown,
            selectedRegion = selectedRegion,
            onRegionSelected = onRegionSelected,
        )
    }
}

@Composable
private fun RegionSelector(
    modifier: Modifier = Modifier,
    dropDownExpanded: Boolean = false,
    onToggleDropDown: () -> Unit,
    selectedRegion: RegionInfo,
    onRegionSelected: (RegionInfo) -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleDropDown),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = selectedRegion.regionCode.name,
                style = MaterialTheme.typography.body1
            )

            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface,
            )
        }
        DropdownMenu(
            expanded = dropDownExpanded,
            onDismissRequest = { onToggleDropDown() },
            modifier = Modifier
                .background(MaterialTheme.colors.surface, shape = RoundedCornerShape(16.dp))
        ) {
            for (region in RegionInfo.entries) {
                DropdownMenuItem(onClick = { onRegionSelected(region) }) {
                    Text(region.name)
                }
            }
        }
    }
}

@Composable
private fun RadioSelectorRadioButtons(
    modifier: Modifier = Modifier,
    devices: List<BTScanModel.DeviceListEntry>,
    onDeviceSelected: (BTScanModel.DeviceListEntry) -> Unit,
    selectedAddress: String,
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        repeat(devices.size) {
            val device = devices[it]
            Row(modifier = Modifier, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedAddress == device.fullAddress,
                    onClick = { onDeviceSelected(device) })
                Text(device.name)
            }
        }
    }
}

@Composable
fun AddDeviceByIPAddress(
    modifier: Modifier = Modifier,
    ipAddress: String = "",
    onIpAddressChange: (String) -> Unit
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                enabled = false,
                selected = false,
                onClick = { /*TODO*/ })
            Text(stringResource(R.string.ip_address), modifier = Modifier.alpha(0.5f))
        }
        TextField(
            value = ipAddress,
            onValueChange = onIpAddressChange,
            label = { Text(stringResource(R.string.ip_address)) },
            placeholder = { Text(stringResource(R.string.ip_address)) },
            singleLine = true,
            modifier = modifier.padding(vertical = 8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.textFieldColors(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
            )
        )
    }
}

@Composable
private fun ProvideLocationCheckBox(modifier: Modifier = Modifier, enabled: Boolean) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            enabled = enabled,
            checked = true,
            onCheckedChange = { /*TODO*/ },
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(stringResource(R.string.provide_location_to_mesh))
    }
}

@Composable
private fun RadioConnectionStatusMessage(
    modifier: Modifier = Modifier,
    errorMessage: String?,
    statusMessage: String?,
    selectedAddress: String?,
    connectedRadioFirmwareVersion: String?,
    isConnected: Boolean,
) {
    when {
        selectedAddress.isNullOrBlank() -> {
            val message = stringResource(R.string.not_paired_yet)
            Text(message, modifier = modifier)
        }

        connectedRadioFirmwareVersion != null && isConnected -> {
            val message = stringResource(R.string.connected_to, connectedRadioFirmwareVersion)
            Text(message, modifier = modifier)
        }

        errorMessage != null -> {
            Text(errorMessage, modifier = modifier)
        }

        statusMessage != null && isConnected -> {
            Text(statusMessage, modifier = modifier)
        }

        !isConnected -> {
            val message = stringResource(R.string.not_connected)
            Text(message, modifier = modifier)
        }
    }
}

@Composable
fun AddRadioFloatingActionButton(
    modifier: Modifier = Modifier,
    showScanningProgress: Boolean = false,
    onClick: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        if (showScanningProgress) {
            CircularProgressIndicator(modifier = Modifier)
        } else {
            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_radio),
                    tint = Color.White
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    AppTheme { Surface { SettingsScreen() } }
}

@Preview
@Composable
private fun SettingsScreenPreviewDark() {
    AppTheme(darkTheme = true) { Surface { SettingsScreen() } }
}
