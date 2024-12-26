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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExtendedFloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.geeksville.mesh.R
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.RegionInfo
import com.geeksville.mesh.ui.theme.AppTheme


@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsScreenViewModel = hiltViewModel(),
    btScanModel: BTScanModel = hiltViewModel(),
) {
    LaunchedEffect(btScanModel.devices, viewModel.isConnected) {
        viewModel.updateNodeInfo()
    }
    Surface {
        Column(modifier = modifier.padding(16.dp)) {
            if (viewModel.showNodeSettings) {
                NameAndRegionRow(
                    textValue = viewModel.userName,
                    onValueChange = viewModel::onUserNameChange,
                    dropDownExpanded = viewModel.regionDropDownExpanded,
                    onToggleDropDown = viewModel::onToggleRegionDropDown,
                    selectedRegion = viewModel.selectedRegion,
                    onRegionSelected = viewModel::onRegionSelected,
                )
            }
            RadioConnectionStatusMessage()
            RadioSelectorRadioButtons(
                devices = btScanModel.devices.values.toList(),
                onDeviceSelected = { btScanModel.onSelected(it) },
                selectedAddress = btScanModel.selectedNotNull,

                )
            AddDeviceByIPAddress(
                ipAddress = viewModel.ipAddress,
                onIpAddressChange = viewModel::onIpAddressChange
            )
            if (viewModel.showProvideLocation) {
                ProvideLocationCheckBox(enabled = viewModel.enableProvideLocation)
            }

            Text(
                text = stringResource(R.string.warning_not_paired),
                style = MaterialTheme.typography.caption,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .alpha(0.7f)
            )

            Spacer(modifier = Modifier.weight(1f))

            ExtendedFloatingActionButton(
                onClick = { /*TODO*/ },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_radio),
                        tint = Color.White
                    )
                },
                text = {
                    Text(
                        stringResource(R.string.add_radio),
                        color = Color.White
                    )
                },
                modifier = Modifier.fillMaxWidth()
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
private fun RadioConnectionStatusMessage(modifier: Modifier = Modifier) {
    // TODO - add condition for if we are paired or not
    Text(stringResource(R.string.not_paired_yet), modifier = modifier)
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
