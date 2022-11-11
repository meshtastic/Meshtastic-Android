package com.geeksville.mesh.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.RegularPreference
import com.geeksville.mesh.ui.components.SwitchPreference

private fun Int.uintToString(): String = this.toUInt().toString()
private fun String.stringToIntOrNull(): Int? = this.toUIntOrNull()?.toInt()

@Composable
fun PreferenceItemList(viewModel: UIViewModel) {
    val focusManager = LocalFocusManager.current

    val hasWifi = viewModel.hasWifi()
    val connectionState = viewModel.connectionState.observeAsState()
    val connected = connectionState.value == MeshService.ConnectionState.CONNECTED

    val localConfig by viewModel.localConfig.collectAsState()
    val user = viewModel.nodeDB.ourNodeInfo?.user

    // Temporary [ConfigProtos.Config] state holders
    var userInput by remember { mutableStateOf(user) }
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
        item { PreferenceCategory(text = "User Config") }

        item {
            RegularPreference(
                title = "Node ID",
                subtitle = userInput?.id ?: stringResource(id = R.string.unknown),
                onClick = {})
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Long name",
                value = userInput?.longName ?: stringResource(id = R.string.unknown_username),
                enabled = connected && userInput?.longName != null,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 39) // long_name max_size:40
                        userInput?.let { userInput = it.copy(longName = value) }
                })
        }

        item {
            EditTextPreference(title = "Short name",
                value = userInput?.shortName ?: stringResource(id = R.string.unknown),
                enabled = connected && userInput?.shortName != null,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 4) // short_name max_size:5
                        userInput?.let { userInput = it.copy(shortName = value) }
                })
        }

        item {
            RegularPreference(
                title = "Hardware model",
                subtitle = userInput?.hwModel?.name ?: stringResource(id = R.string.unknown),
                onClick = {})
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Licensed amateur radio",
                checked = userInput?.isLicensed ?: false,
                enabled = connected && userInput?.isLicensed != null,
                onCheckedChange = { value ->
                    userInput?.let { userInput = it.copy(isLicensed = value) }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = userInput != user,
                onCancelClicked = {
                    focusManager.clearFocus()
                    userInput = user
                }, onSaveClicked = {
                    focusManager.clearFocus()
                    userInput?.let { viewModel.setOwner(it.longName, it.shortName, it.isLicensed) }
                })
        }

        item { PreferenceCategory(text = "Device Config") }

        item {
            DropDownPreference(title = "Role",
                enabled = connected,
                items = ConfigProtos.Config.DeviceConfig.Role.values()
                    .filter { it != ConfigProtos.Config.DeviceConfig.Role.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = deviceInput.role,
                onItemSelected = { deviceInput = deviceInput.copy { role = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Serial output enabled",
                checked = deviceInput.serialEnabled,
                enabled = connected,
                onCheckedChange = { deviceInput = deviceInput.copy { serialEnabled = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Debug log enabled",
                checked = deviceInput.debugLogEnabled,
                enabled = connected,
                onCheckedChange = { deviceInput = deviceInput.copy { debugLogEnabled = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = deviceInput != localConfig.device,
                onCancelClicked = {
                    focusManager.clearFocus()
                    deviceInput = localConfig.device
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateDeviceConfig { deviceInput }
                })
        }

        item { PreferenceCategory(text = "Position Config") }

        item {
            EditTextPreference(title = "Position broadcast interval",
                value = positionInput.positionBroadcastSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { positionInput = positionInput.copy { positionBroadcastSecs = it } }
                })
        }

        item {
            SwitchPreference(title = "Smart position enabled",
                checked = positionInput.positionBroadcastSmartEnabled,
                enabled = connected,
                onCheckedChange = {
                    positionInput = positionInput.copy { positionBroadcastSmartEnabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Use fixed position",
                checked = positionInput.fixedPosition,
                enabled = connected,
                onCheckedChange = { positionInput = positionInput.copy { fixedPosition = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "GPS enabled",
                checked = positionInput.gpsEnabled,
                enabled = connected,
                onCheckedChange = { positionInput = positionInput.copy { gpsEnabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "GPS update interval",
                value = positionInput.gpsUpdateInterval.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { positionInput = positionInput.copy { gpsUpdateInterval = it } }
                })
        }

        item {
            EditTextPreference(title = "Fix attempt duration",
                value = positionInput.gpsAttemptTime.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { positionInput = positionInput.copy { gpsAttemptTime = it } }
                })
        }

        item {
            EditTextPreference(title = "Position flags",
                value = positionInput.positionFlags.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { positionInput = positionInput.copy { positionFlags = it } }
                })
        }

        item {
            PreferenceFooter(
                enabled = positionInput != localConfig.position,
                onCancelClicked = {
                    focusManager.clearFocus()
                    positionInput = localConfig.position
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updatePositionConfig { positionInput }
                })
        }

        item { PreferenceCategory(text = "Power Config") }

        item {
            SwitchPreference(title = "Enable power saving mode",
                checked = powerInput.isPowerSaving,
                enabled = connected && hasWifi, // We consider hasWifi = ESP32
                onCheckedChange = { powerInput = powerInput.copy { isPowerSaving = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(
                title = "Shutdown on battery delay",
                value = powerInput.onBatteryShutdownAfterSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { onBatteryShutdownAfterSecs = it } }
                })
        }

        item {
            EditTextPreference(
                title = "ADC multiplier override ratio",
                value = powerInput.adcMultiplierOverride.toString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.toFloatOrNull()
                        ?.let { powerInput = powerInput.copy { adcMultiplierOverride = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Wait for Bluetooth duration",
                value = powerInput.waitBluetoothSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { waitBluetoothSecs = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Mesh SDS timeout",
                value = powerInput.meshSdsTimeoutSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { meshSdsTimeoutSecs = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Super deep sleep duration",
                value = powerInput.sdsSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { sdsSecs = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Light sleep duration",
                value = powerInput.lsSecs.uintToString(),
                enabled = connected && hasWifi, // we consider hasWifi = ESP32
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { lsSecs = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Minimum wake time",
                value = powerInput.minWakeSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { powerInput = powerInput.copy { minWakeSecs = it } }
                })
        }

        item {
            PreferenceFooter(
                enabled = powerInput != localConfig.power,
                onCancelClicked = {
                    focusManager.clearFocus()
                    powerInput = localConfig.power
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updatePowerConfig { powerInput }
                })
        }

        item { PreferenceCategory(text = "Network Config") }

        item {
            SwitchPreference(
                title = "WiFi enabled",
                checked = networkInput.wifiEnabled,
                enabled = connected && hasWifi,
                onCheckedChange = { networkInput = networkInput.copy { wifiEnabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(
                title = "SSID",
                value = networkInput.wifiSsid.toString(),
                enabled = connected && hasWifi,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 32) // wifi_ssid max_size:33
                        networkInput = networkInput.copy { wifiSsid = value }
                })
        }

        item {
            EditTextPreference(
                title = "PSK",
                value = networkInput.wifiPsk .toString(),
                enabled = connected && hasWifi,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 63) // wifi_psk max_size:64
                        networkInput = networkInput.copy { wifiPsk = value }
                })
        }

        item {
            EditTextPreference(
                title = "NTP server",
                value = networkInput.ntpServer.toString(),
                enabled = connected && hasWifi,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 32) // ntp_server max_size:33
                        networkInput = networkInput.copy { ntpServer = value }
                })
        }

        item {
            SwitchPreference(
                title = "Ethernet enabled",
                checked = networkInput.ethEnabled,
                enabled = connected,
                onCheckedChange = { networkInput = networkInput.copy { ethEnabled = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Ethernet mode",
                enabled = connected,
                items = ConfigProtos.Config.NetworkConfig.EthMode.values()
                    .filter { it != ConfigProtos.Config.NetworkConfig.EthMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = networkInput.ethMode,
                onItemSelected = { networkInput = networkInput.copy { ethMode = it } })
        }
        item { Divider() }

        item { PreferenceCategory(text = "IPv4 Config") }

        item {
            EditTextPreference(
                title = "IP",
                value = networkInput.ipv4Config.ip.toString(),
                enabled = connected && networkInput.ethMode == ConfigProtos.Config.NetworkConfig.EthMode.STATIC,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.toIntOrNull()?.let {
                        val ipv4 = networkInput.ipv4Config.copy { ip = it }
                        networkInput = networkInput.copy { ipv4Config = ipv4 }
                    }
                })
        }

        item {
            EditTextPreference(
                title = "Gateway",
                value = networkInput.ipv4Config.gateway.toString(),
                enabled = connected && networkInput.ethMode == ConfigProtos.Config.NetworkConfig.EthMode.STATIC,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.toIntOrNull()?.let {
                        val ipv4 = networkInput.ipv4Config.copy { gateway = it }
                        networkInput = networkInput.copy { ipv4Config = ipv4 }
                    }
                })
        }

        item {
            EditTextPreference(
                title = "Subnet",
                value = networkInput.ipv4Config.subnet.toString(),
                enabled = connected && networkInput.ethMode == ConfigProtos.Config.NetworkConfig.EthMode.STATIC,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.toIntOrNull()?.let {
                        val ipv4 = networkInput.ipv4Config.copy { subnet = it }
                        networkInput = networkInput.copy { ipv4Config = ipv4 }
                    }
                })
        }

        item {
            EditTextPreference(
                title = "DNS",
                value = networkInput.ipv4Config.dns.toString(),
                enabled = connected && networkInput.ethMode == ConfigProtos.Config.NetworkConfig.EthMode.STATIC,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.toIntOrNull()?.let {
                        val ipv4 = networkInput.ipv4Config.copy { dns = it }
                        networkInput = networkInput.copy { ipv4Config = ipv4 }
                    }
                })
        }

        item {
            PreferenceFooter(
                enabled = networkInput != localConfig.network,
                onCancelClicked = {
                    focusManager.clearFocus()
                    networkInput = localConfig.network
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateNetworkConfig { networkInput }
                })
        }

        item { PreferenceCategory(text = "Display Config") }

        item {
            EditTextPreference(
                title = "Screen timeout",
                value = displayInput.screenOnSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { displayInput = displayInput.copy { screenOnSecs = it } }
                })
        }

        item {
            DropDownPreference(title = "GPS coordinates format",
                enabled = connected,
                items = ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat.values()
                    .filter { it != ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = displayInput.gpsFormat,
                onItemSelected = { displayInput = displayInput.copy { gpsFormat = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(
                title = "Auto screen carousel",
                value = displayInput.autoScreenCarouselSecs.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { displayInput = displayInput.copy { autoScreenCarouselSecs = it } }
                })
        }

        item {
            SwitchPreference(
                title = "Compass north top",
                checked = displayInput.compassNorthTop,
                enabled = connected,
                onCheckedChange = { displayInput = displayInput.copy { compassNorthTop = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(
                title = "Flip screen",
                checked = displayInput.flipScreen,
                enabled = connected,
                onCheckedChange = { displayInput = displayInput.copy { flipScreen = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Display units",
                enabled = connected,
                items = ConfigProtos.Config.DisplayConfig.DisplayUnits.values()
                    .filter { it != ConfigProtos.Config.DisplayConfig.DisplayUnits.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = displayInput.units,
                onItemSelected = { displayInput = displayInput.copy { units = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Override OLED auto-detect",
                enabled = connected,
                items = ConfigProtos.Config.DisplayConfig.OledType.values()
                    .filter { it != ConfigProtos.Config.DisplayConfig.OledType.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = displayInput.oled,
                onItemSelected = { displayInput = displayInput.copy { oled = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = displayInput != localConfig.display,
                onCancelClicked = {
                    focusManager.clearFocus()
                    displayInput = localConfig.display
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateDisplayConfig { displayInput }
                })
        }

        item { PreferenceCategory(text = "LoRa Config") }

        item {
            SwitchPreference(
                title = "Use modem preset",
                checked = loraInput.usePreset,
                enabled = connected,
                onCheckedChange = { loraInput = loraInput.copy { usePreset = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Modem preset",
                enabled = connected && loraInput.usePreset,
                items = ConfigProtos.Config.LoRaConfig.ModemPreset.values()
                    .filter { it != ConfigProtos.Config.LoRaConfig.ModemPreset.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = loraInput.modemPreset,
                onItemSelected = { loraInput = loraInput.copy { modemPreset = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(
                title = "Bandwidth",
                value = loraInput.bandwidth.uintToString(),
                enabled = connected && !loraInput.usePreset,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { loraInput = loraInput.copy { bandwidth = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Spread factor",
                value = loraInput.spreadFactor.uintToString(),
                enabled = connected && !loraInput.usePreset,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { loraInput = loraInput.copy { spreadFactor = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Coding rate",
                value = loraInput.codingRate.uintToString(),
                enabled = connected && !loraInput.usePreset,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { loraInput = loraInput.copy { codingRate = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Frequency offset",
                value = loraInput.frequencyOffset.toString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.toFloatOrNull()
                        ?.let { loraInput = loraInput.copy { frequencyOffset = it } }
                })
        }

        item {
            DropDownPreference(title = "Region (frequency plan)",
                enabled = connected,
                items = ConfigProtos.Config.LoRaConfig.RegionCode.values()
                    .filter { it != ConfigProtos.Config.LoRaConfig.RegionCode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = loraInput.region,
                onItemSelected = { loraInput = loraInput.copy { region = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(
                title = "Hop limit",
                value = loraInput.hopLimit.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { loraInput = loraInput.copy { hopLimit = it } }
                })
        }

        item {
            SwitchPreference(
                title = "TX enabled",
                checked = loraInput.txEnabled,
                enabled = connected,
                onCheckedChange = { loraInput = loraInput.copy { txEnabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(
                title = "TX power",
                value = loraInput.txPower.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { loraInput = loraInput.copy { txPower = it } }
                })
        }

        item {
            EditTextPreference(
                title = "Channel number",
                value = loraInput.channelNum.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { loraInput = loraInput.copy { channelNum = it } }
                })
        }

        item {
            PreferenceFooter(
                enabled = loraInput != localConfig.lora,
                onCancelClicked = {
                    focusManager.clearFocus()
                    loraInput = localConfig.lora
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateLoraConfig { loraInput }
                })
        }

        item { PreferenceCategory(text = "Bluetooth Config") }

        item {
            SwitchPreference(
                title = "Bluetooth enabled",
                checked = bluetoothInput.enabled,
                enabled = connected,
                onCheckedChange = { bluetoothInput = bluetoothInput.copy { enabled = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Pairing mode",
                enabled = connected,
                items = ConfigProtos.Config.BluetoothConfig.PairingMode.values()
                    .filter { it != ConfigProtos.Config.BluetoothConfig.PairingMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = bluetoothInput.mode,
                onItemSelected = { bluetoothInput = bluetoothInput.copy { mode = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(
                title = "Fixed PIN",
                value = bluetoothInput.fixedPin.uintToString(),
                enabled = connected,
                keyboardActions = KeyboardActions(onSend = {
                    focusManager.clearFocus()
                }),
                onValueChanged = { value ->
                    value.stringToIntOrNull()
                        ?.let { bluetoothInput = bluetoothInput.copy { fixedPin = it } }
                })
        }

        item {
            PreferenceFooter(
                enabled = bluetoothInput != localConfig.bluetooth,
                onCancelClicked = {
                    focusManager.clearFocus()
                    bluetoothInput = localConfig.bluetooth
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateBluetoothConfig { bluetoothInput }
                })
        }
    }
}
