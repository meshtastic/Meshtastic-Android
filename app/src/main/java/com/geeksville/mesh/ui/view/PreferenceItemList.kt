package com.geeksville.mesh.ui.view

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.SwitchPreference

private fun Int.uintToString(): String = this.toUInt().toString()
private fun String.stringToIntOrNull(): Int? = this.toUIntOrNull()?.toInt()

@Composable
fun PreferenceItemList(viewModel: UIViewModel) {
    val focusManager = LocalFocusManager.current

    val connectionState = viewModel.connectionState.observeAsState()
    val connected = connectionState.value == MeshService.ConnectionState.CONNECTED

    val localConfig by viewModel.localConfig.collectAsState()

    // Temporary [ConfigProtos.Config] state holders
    var deviceInput by remember { mutableStateOf(localConfig.device) }
    var positionInput by remember { mutableStateOf(localConfig.position) }
    var powerInput by remember { mutableStateOf(localConfig.power) }
    var networkInput by remember { mutableStateOf(localConfig.network) }
    var displayInput by remember { mutableStateOf(localConfig.display) }
    var loraInput by remember { mutableStateOf(localConfig.lora) }
    var bluetoothInput by remember { mutableStateOf(localConfig.bluetooth) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Device Config") }

        item {
            DropDownPreference(title = "Role",
                enabled = connected,
                items = ConfigProtos.Config.DeviceConfig.Role.values()
                    .filter { it != ConfigProtos.Config.DeviceConfig.Role.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = viewModel.config.device.role, // deviceInput.role,
                onItemSelected = { n -> viewModel.updateDeviceConfig { it.copy { role = n } } }) // TODO
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Serial Output Enabled",
                checked = viewModel.config.device.serialEnabled, // deviceInput.serialEnabled,
                enabled = connected,
                onCheckedChange = {
                    viewModel.updateDeviceConfig {
                        it.copy { serialEnabled = !deviceInput.serialEnabled } // TODO
                    }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Enabled Debug Log",
                checked = viewModel.config.device.debugLogEnabled, // deviceInput.debugLogEnabled,
                enabled = connected,
                onCheckedChange = {
                    viewModel.updateDeviceConfig {
                        it.copy { debugLogEnabled = !deviceInput.debugLogEnabled } // TODO
                    }
                })
        }
        item { Divider() }

        item { PreferenceCategory(text = "Position Config") }

        item {
            EditTextPreference(title = "Position Broadcast Interval",
                value = positionInput.positionBroadcastSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.updatePositionConfig { positionInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { positionInput = positionInput.copy { positionBroadcastSecs = it } }
                })
        }

        item {
            SwitchPreference(title = "Enable Smart Position",
                checked = viewModel.config.position.positionBroadcastSmartEnabled, // positionInput.positionBroadcastSmartEnabled,
                enabled = connected,
                onCheckedChange = {
                    viewModel.updatePositionConfig {
                        it.copy {
                            positionBroadcastSmartEnabled = !positionInput.positionBroadcastSmartEnabled // TODO
                        }
                    }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Use Fixed Position",
                checked = viewModel.config.position.fixedPosition, // positionInput.fixedPosition,
                enabled = connected,
                onCheckedChange = {
                    viewModel.updatePositionConfig {
                        it.copy { fixedPosition = !positionInput.fixedPosition } // TODO
                    }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "GPS Enabled",
                checked = viewModel.config.position.gpsEnabled, // positionInput.gpsEnabled,
                enabled = connected,
                onCheckedChange = {
                    viewModel.updatePositionConfig { it.copy { gpsEnabled = !positionInput.gpsEnabled } } // TODO
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "GPS Update Interval",
                value = positionInput.gpsUpdateInterval.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.updatePositionConfig { positionInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { positionInput = positionInput.copy { gpsUpdateInterval = it } }
                })
        }

        item {
            EditTextPreference(title = "Fix Attempt Duration",
                value = positionInput.gpsAttemptTime.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.updatePositionConfig { positionInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { positionInput = positionInput.copy { gpsAttemptTime = it } }
                })
        }

        item { PreferenceCategory(text = "Power Config") }

        item {
            EditTextPreference(
                title = "Shutdown on battery delay",
                value = powerInput.onBatteryShutdownAfterSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.updatePowerConfig { powerInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { onBatteryShutdownAfterSecs = it } }
                })
        }

        item {
            SwitchPreference(title = "Enable power saving mode",
                checked = viewModel.config.power.isPowerSaving, // powerInput.isPowerSaving,
                enabled = connected && viewModel.isESP32(),
                onCheckedChange = {
                    viewModel.updatePowerConfig { it.copy { isPowerSaving = !powerInput.isPowerSaving } } // TODO
                })
        }
        item { Divider() }

        item {
            EditTextPreference(
                title = "ADC Multiplier Override ratio",
                value = powerInput.adcMultiplierOverride.toString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.updatePowerConfig { powerInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.toFloatOrNull()
                        ?.let { powerInput = powerInput.copy { adcMultiplierOverride = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Minimum Wake Time",
                value = powerInput.minWakeSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.updatePowerConfig { powerInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { minWakeSecs = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Mesh SDS Timeout",
                value = powerInput.meshSdsTimeoutSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.updatePowerConfig { powerInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { meshSdsTimeoutSecs = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Super Deep Sleep Duration",
                value = powerInput.sdsSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.updatePowerConfig { powerInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { sdsSecs = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Light Sleep Duration",
                value = powerInput.lsSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.updatePowerConfig { powerInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { lsSecs = it } }
                })
        }

        item {
            EditTextPreference(
                title = "No Connection Bluetooth Disabled",
                value = powerInput.waitBluetoothSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    viewModel.updatePowerConfig { powerInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { waitBluetoothSecs = it } }
                })
        }

        item {
            PreferenceCategory(text = "Network Config")
        }

        item {
            SwitchPreference(
                title = "WiFi Enabled",
                checked = viewModel.config.network.wifiEnabled,
                enabled = connected,
                onCheckedChange = {
                    viewModel.updateNetworkConfig { it.copy { wifiEnabled = !networkInput.wifiEnabled } } // TODO
                })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "WiFi Mode",
                enabled = connected,
                items = ConfigProtos.Config.NetworkConfig.WiFiMode.values()
                    .filter { it != ConfigProtos.Config.NetworkConfig.WiFiMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = viewModel.config.network.wifiMode, // networkInput.wifiMode,
                onItemSelected = { n -> viewModel.updateNetworkConfig { it.copy { wifiMode = n } } }) // TODO
        }
        item { Divider() }

        item {
            EditTextPreference(
                title = "SSID",
                value = networkInput.wifiSsid,
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = { // TODO use KeyboardType.Text
                    viewModel.updateNetworkConfig { networkInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { networkInput = networkInput.copy { wifiSsid = it }
                })
        }

        item {
            EditTextPreference(
                title = "PSK",
                value = networkInput.wifiPsk,
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = { // TODO use KeyboardType.Password
                    viewModel.updateNetworkConfig { networkInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { networkInput = networkInput.copy { wifiPsk = it }
                })
        }

        item {
            EditTextPreference(
                title = "NTP Server",
                value = networkInput.ntpServer,
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = { // TODO use KeyboardType.url
                    viewModel.updateNetworkConfig { networkInput }
                    focusManager.clearFocus()
                }),
                onValueChanged = { networkInput = networkInput.copy { ntpServer = it }
                })
        }

        item {
            SwitchPreference(
                title = "Ethernet Enabled",
                checked = false, // viewModel.config.network.ethEnabled,
                enabled = false,
                onCheckedChange = {
                    // viewModel.updateNetworkConfig { it.copy { ethEnabled = !networkInput.ethEnabled } } // TODO
                })
        }
        item { Divider() }

        item {

        }
    }
}
