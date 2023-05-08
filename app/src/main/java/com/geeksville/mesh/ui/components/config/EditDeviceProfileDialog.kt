package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ClientOnlyProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.deviceProfile
import com.geeksville.mesh.ui.components.SwitchPreference
import com.google.accompanist.themeadapter.appcompat.AppCompatTheme

@Composable
fun EditDeviceProfileDialog(
    title: String,
    deviceProfile: ClientOnlyProtos.DeviceProfile,
    onAddClick: (ClientOnlyProtos.DeviceProfile) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var longNameInput by remember(deviceProfile) { mutableStateOf(deviceProfile.hasLongName()) }
    var shortNameInput by remember(deviceProfile) { mutableStateOf(deviceProfile.hasShortName()) }
    var channelUrlInput by remember(deviceProfile) { mutableStateOf(deviceProfile.hasChannelUrl()) }
    var configInput by remember(deviceProfile) { mutableStateOf(deviceProfile.hasConfig()) }
    var moduleConfigInput by remember(deviceProfile) { mutableStateOf(deviceProfile.hasModuleConfig()) }

    AlertDialog(
        title = { Text(title) },
        onDismissRequest = onDismissRequest,
        text = {
            AppCompatTheme {
                Column(modifier.fillMaxWidth()) {
                    SwitchPreference(title = "longName",
                        checked = longNameInput,
                        enabled = deviceProfile.hasLongName(),
                        onCheckedChange = { longNameInput = it }
                    )
                    SwitchPreference(title = "shortName",
                        checked = shortNameInput,
                        enabled = deviceProfile.hasShortName(),
                        onCheckedChange = { shortNameInput = it }
                    )
                    SwitchPreference(title = "channelUrl",
                        checked = channelUrlInput,
                        enabled = deviceProfile.hasChannelUrl(),
                        onCheckedChange = { channelUrlInput = it }
                    )
                    SwitchPreference(title = "config",
                        checked = configInput,
                        enabled = deviceProfile.hasConfig(),
                        onCheckedChange = { configInput = it }
                    )
                    SwitchPreference(title = "moduleConfig",
                        checked = moduleConfigInput,
                        enabled = deviceProfile.hasModuleConfig(),
                        onCheckedChange = { moduleConfigInput = it }
                    )
                }
            }
        },
        buttons = {
            Row(
                modifier = modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp)
                        .weight(1f),
                    onClick = onDismissRequest
                ) { Text(stringResource(R.string.cancel)) }
                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(end = 24.dp)
                        .weight(1f),
                    onClick = {
                        onAddClick(deviceProfile {
                            if (longNameInput) longName = deviceProfile.longName
                            if (shortNameInput) shortName = deviceProfile.shortName
                            if (channelUrlInput) channelUrl = deviceProfile.channelUrl
                            if (configInput) config = deviceProfile.config
                            if (moduleConfigInput) moduleConfig = deviceProfile.moduleConfig
                        })
                    },
                ) { Text(stringResource(R.string.save)) }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun EditDeviceProfileDialogPreview() {
    EditDeviceProfileDialog(
        title = "Export configuration",
        deviceProfile = deviceProfile { },
        onAddClick = { },
        onDismissRequest = { },
    )
}
