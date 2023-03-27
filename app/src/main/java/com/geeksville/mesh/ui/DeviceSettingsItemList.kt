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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.ConfigProtos.Config.NetworkConfig
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.getInitials
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.components.BitwisePreference
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditIPv4Preference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.RegularPreference
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun DeviceSettingsItemList(viewModel: UIViewModel = viewModel()) {
    val focusManager = LocalFocusManager.current

    val hasWifi = viewModel.hasWifi()
    val connectionState by viewModel.connectionState.observeAsState()
    val connected = connectionState == MeshService.ConnectionState.CONNECTED

    val localConfig by viewModel.localConfig.collectAsState()
    val ourNodeInfo by viewModel.ourNodeInfo.collectAsState()
    var userInput by remember(ourNodeInfo?.user) { mutableStateOf(ourNodeInfo?.user) }
    var positionInfo by remember(ourNodeInfo?.position) { mutableStateOf(ourNodeInfo?.position) }

    // Temporary [ConfigProtos.Config] state holders
    var deviceInput by remember(localConfig.device) { mutableStateOf(localConfig.device) }
    var positionInput by remember(localConfig.position) { mutableStateOf(localConfig.position) }
    var powerInput by remember(localConfig.power) { mutableStateOf(localConfig.power) }
    var networkInput by remember(localConfig.network) { mutableStateOf(localConfig.network) }
    var displayInput by remember(localConfig.display) { mutableStateOf(localConfig.display) }
    var loraInput by remember(localConfig.lora) { mutableStateOf(localConfig.lora) }
    var bluetoothInput by remember(localConfig.bluetooth) { mutableStateOf(localConfig.bluetooth) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "User Config") }

        item {
            RegularPreference(title = "Node ID",
                subtitle = userInput?.id ?: stringResource(id = R.string.unknown),
                onClick = {})
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Long name",
                value = userInput?.longName ?: stringResource(id = R.string.unknown_username),
                enabled = connected && userInput?.longName != null,
                isError = userInput?.longName.isNullOrEmpty(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 39) // long_name max_size:40
                        userInput?.let { userInput = it.copy(longName = value) }
                    if (getInitials(value).toByteArray().size <= 4) // short_name max_size:5
                        userInput?.let { userInput = it.copy(shortName = getInitials(value)) }
                })
        }

        item {
            EditTextPreference(title = "Short name",
                value = userInput?.shortName ?: stringResource(id = R.string.unknown),
                enabled = connected && userInput?.shortName != null,
                isError = userInput?.shortName.isNullOrEmpty(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 4) // short_name max_size:5
                        userInput?.let { userInput = it.copy(shortName = value) }
                })
        }

        item {
            RegularPreference(title = "Hardware model",
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
                enabled = userInput != ourNodeInfo?.user,
                onCancelClicked = {
                    focusManager.clearFocus()
                    userInput = ourNodeInfo?.user
                }, onSaveClicked = {
                    focusManager.clearFocus()
                    userInput?.let { viewModel.setOwner(it) }
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
            EditTextPreference(title = "Redefine PIN_BUTTON",
                value = deviceInput.buttonGpio,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { buttonGpio = it }
                })
        }

        item {
            EditTextPreference(title = "Redefine PIN_BUZZER",
                value = deviceInput.buzzerGpio,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { buzzerGpio = it }
                })
        }

        item {
            DropDownPreference(title = "Rebroadcast mode",
                enabled = connected,
                items = ConfigProtos.Config.DeviceConfig.RebroadcastMode.values()
                    .filter { it != ConfigProtos.Config.DeviceConfig.RebroadcastMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = deviceInput.rebroadcastMode,
                onItemSelected = { deviceInput = deviceInput.copy { rebroadcastMode = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "NodeInfo broadcast interval",
                value = deviceInput.nodeInfoBroadcastSecs,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    deviceInput = deviceInput.copy { nodeInfoBroadcastSecs = it }
                })
        }

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
                value = positionInput.positionBroadcastSecs,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    positionInput = positionInput.copy { positionBroadcastSecs = it }
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

        if (positionInput.fixedPosition) {
            item {
                EditTextPreference(title = "Latitude",
                    value = positionInfo?.latitude ?: 0.0,
                    enabled = connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { value ->
                        if (value >= -90 && value <= 90.0)
                            positionInfo?.let { positionInfo = it.copy(latitude = value) }
                    })
            }
            item {
                EditTextPreference(title = "Longitude",
                    value = positionInfo?.longitude ?: 0.0,
                    enabled = connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { value ->
                        if (value >= -180 && value <= 180.0)
                            positionInfo?.let { positionInfo = it.copy(longitude = value) }
                    })
            }
            item {
                EditTextPreference(title = "Altitude",
                    value = positionInfo?.altitude ?: 0,
                    enabled = connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { value ->
                        positionInfo?.let { positionInfo = it.copy(altitude = value) }
                    })
            }
        }

        item {
            SwitchPreference(title = "GPS enabled",
                checked = positionInput.gpsEnabled,
                enabled = connected,
                onCheckedChange = { positionInput = positionInput.copy { gpsEnabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "GPS update interval",
                value = positionInput.gpsUpdateInterval,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { positionInput = positionInput.copy { gpsUpdateInterval = it } })
        }

        item {
            EditTextPreference(title = "Fix attempt duration",
                value = positionInput.gpsAttemptTime,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { positionInput = positionInput.copy { gpsAttemptTime = it } })
        }

        item {
            BitwisePreference(title = "Position flags",
                value = positionInput.positionFlags,
                enabled = connected,
                items = ConfigProtos.Config.PositionConfig.PositionFlags.values()
                    .filter { it != ConfigProtos.Config.PositionConfig.PositionFlags.UNSET && it != ConfigProtos.Config.PositionConfig.PositionFlags.UNRECOGNIZED }
                    .map { it.number to it.name },
                onItemSelected = { positionInput = positionInput.copy { positionFlags = it } }
            )
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Redefine GPS_RX_PIN",
                value = positionInput.rxGpio,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { positionInput = positionInput.copy { rxGpio = it } })
        }

        item {
            EditTextPreference(title = "Redefine GPS_TX_PIN",
                value = positionInput.txGpio,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { positionInput = positionInput.copy { txGpio = it } })
        }

        item {
            PreferenceFooter(
                enabled = positionInput != localConfig.position || positionInfo != ourNodeInfo?.position,
                onCancelClicked = {
                    focusManager.clearFocus()
                    positionInput = localConfig.position
                    positionInfo = ourNodeInfo?.position
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    if (positionInfo != ourNodeInfo?.position && positionInput.fixedPosition) positionInfo?.let {
                        viewModel.requestPosition(0, it.latitude, it.longitude, it.altitude)
                    }
                    if (positionInput != localConfig.position) viewModel.updatePositionConfig { positionInput }
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
            EditTextPreference(title = "Shutdown on battery delay",
                value = powerInput.onBatteryShutdownAfterSecs,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    powerInput = powerInput.copy { onBatteryShutdownAfterSecs = it }
                })
        }

        item {
            EditTextPreference(title = "ADC multiplier override ratio",
                value = powerInput.adcMultiplierOverride,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { adcMultiplierOverride = it } })
        }

        item {
            EditTextPreference(title = "Wait for Bluetooth duration",
                value = powerInput.waitBluetoothSecs,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { waitBluetoothSecs = it } })
        }

        item {
            EditTextPreference(title = "Mesh SDS timeout",
                value = powerInput.meshSdsTimeoutSecs,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { meshSdsTimeoutSecs = it } })
        }

        item {
            EditTextPreference(title = "Super deep sleep duration",
                value = powerInput.sdsSecs,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { sdsSecs = it } })
        }

        item {
            EditTextPreference(title = "Light sleep duration",
                value = powerInput.lsSecs,
                enabled = connected && hasWifi, // we consider hasWifi = ESP32
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { lsSecs = it } })
        }

        item {
            EditTextPreference(title = "Minimum wake time",
                value = powerInput.minWakeSecs,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { minWakeSecs = it } })
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
            SwitchPreference(title = "WiFi enabled",
                checked = networkInput.wifiEnabled,
                enabled = connected && hasWifi,
                onCheckedChange = { networkInput = networkInput.copy { wifiEnabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "SSID",
                value = networkInput.wifiSsid,
                enabled = connected && hasWifi,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 32) // wifi_ssid max_size:33
                        networkInput = networkInput.copy { wifiSsid = value }
                })
        }

        item {
            EditTextPreference(title = "PSK",
                value = networkInput.wifiPsk,
                enabled = connected && hasWifi,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 63) // wifi_psk max_size:64
                        networkInput = networkInput.copy { wifiPsk = value }
                })
        }

        item {
            EditTextPreference(title = "NTP server",
                value = networkInput.ntpServer,
                enabled = connected && hasWifi,
                isError = networkInput.ntpServer.isEmpty(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 32) // ntp_server max_size:33
                        networkInput = networkInput.copy { ntpServer = value }
                })
        }

        item {
            EditTextPreference(title = "rsyslog server",
                value = networkInput.rsyslogServer,
                enabled = connected && hasWifi,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 32) // rsyslog_server max_size:33
                        networkInput = networkInput.copy { rsyslogServer = value }
                })
        }

        item {
            SwitchPreference(title = "Ethernet enabled",
                checked = networkInput.ethEnabled,
                enabled = connected,
                onCheckedChange = { networkInput = networkInput.copy { ethEnabled = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "IPv4 mode",
                enabled = connected,
                items = NetworkConfig.AddressMode.values()
                    .filter { it != NetworkConfig.AddressMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = networkInput.addressMode,
                onItemSelected = { networkInput = networkInput.copy { addressMode = it } })
        }
        item { Divider() }

        item { PreferenceCategory(text = "IPv4 Config") }

        item {
            EditIPv4Preference(title = "IP",
                value = networkInput.ipv4Config.ip,
                enabled = connected && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { ip = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                })
        }

        item {
            EditIPv4Preference(title = "Gateway",
                value = networkInput.ipv4Config.gateway,
                enabled = connected && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { gateway = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                })
        }

        item {
            EditIPv4Preference(title = "Subnet",
                value = networkInput.ipv4Config.subnet,
                enabled = connected && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { subnet = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                })
        }

        item {
            EditIPv4Preference(title = "DNS",
                value = networkInput.ipv4Config.dns,
                enabled = connected && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { dns = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
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
            EditTextPreference(title = "Screen timeout",
                value = displayInput.screenOnSecs,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { displayInput = displayInput.copy { screenOnSecs = it } })
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
            EditTextPreference(title = "Auto screen carousel",
                value = displayInput.autoScreenCarouselSecs,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    displayInput = displayInput.copy { autoScreenCarouselSecs = it }
                })
        }

        item {
            SwitchPreference(title = "Compass north top",
                checked = displayInput.compassNorthTop,
                enabled = connected,
                onCheckedChange = { displayInput = displayInput.copy { compassNorthTop = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Flip screen",
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
            DropDownPreference(title = "Display mode",
                enabled = connected,
                items = ConfigProtos.Config.DisplayConfig.DisplayMode.values()
                    .filter { it != ConfigProtos.Config.DisplayConfig.DisplayMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = displayInput.displaymode,
                onItemSelected = { displayInput = displayInput.copy { displaymode = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Heading bold",
                checked = displayInput.headingBold,
                enabled = connected,
                onCheckedChange = { displayInput = displayInput.copy { headingBold = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Wake screen on tap or motion",
                checked = displayInput.wakeOnTapOrMotion,
                enabled = connected,
                onCheckedChange = { displayInput = displayInput.copy { wakeOnTapOrMotion = it } })
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
            SwitchPreference(title = "Use modem preset",
                checked = loraInput.usePreset,
                enabled = connected,
                onCheckedChange = { loraInput = loraInput.copy { usePreset = it } })
        }
        item { Divider() }

        if (loraInput.usePreset) {
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
        } else {
            item {
                EditTextPreference(title = "Bandwidth",
                    value = loraInput.bandwidth,
                    enabled = connected && !loraInput.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { loraInput = loraInput.copy { bandwidth = it } })
            }

            item {
                EditTextPreference(title = "Spread factor",
                    value = loraInput.spreadFactor,
                    enabled = connected && !loraInput.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { loraInput = loraInput.copy { spreadFactor = it } })
            }

            item {
                EditTextPreference(title = "Coding rate",
                    value = loraInput.codingRate,
                    enabled = connected && !loraInput.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { loraInput = loraInput.copy { codingRate = it } })
            }
        }

        item {
            EditTextPreference(title = "Frequency offset (MHz)",
                value = loraInput.frequencyOffset,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { loraInput = loraInput.copy { frequencyOffset = it } })
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
            EditTextPreference(title = "Hop limit",
                value = loraInput.hopLimit,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { loraInput = loraInput.copy { hopLimit = it } })
        }

        item {
            SwitchPreference(title = "TX enabled",
                checked = loraInput.txEnabled,
                enabled = connected,
                onCheckedChange = { loraInput = loraInput.copy { txEnabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "TX power",
                value = loraInput.txPower,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { loraInput = loraInput.copy { txPower = it } })
        }

        item {
            EditTextPreference(title = "Channel number",
                value = loraInput.channelNum,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { loraInput = loraInput.copy { channelNum = it } })
        }

        item {
            SwitchPreference(title = "Override Duty Cycle",
                checked = loraInput.overrideDutyCycle,
                enabled = connected,
                onCheckedChange = { loraInput = loraInput.copy { overrideDutyCycle = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Ignore incoming", // FIXME use proper Composable component
                value = loraInput.ignoreIncomingList.getOrNull(0) ?: 0,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    loraInput = loraInput.copy {
                        if (loraInput.ignoreIncomingCount == 0) ignoreIncoming.add(it)
                        else if (it == 0) ignoreIncoming.clear() else ignoreIncoming[0] = it
                    }
                })
        }

        item {
            SwitchPreference(title = "SX126X RX boosted gain",
                checked = loraInput.sx126XRxBoostedGain,
                enabled = connected,
                onCheckedChange = { loraInput = loraInput.copy { sx126XRxBoostedGain = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Override frequency (MHz)",
                value = loraInput.overrideFrequency,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { loraInput = loraInput.copy { overrideFrequency = it } })
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
            SwitchPreference(title = "Bluetooth enabled",
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
            EditTextPreference(title = "Fixed PIN",
                value = bluetoothInput.fixedPin,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    if (it.toString().length == 6) // ensure 6 digits
                        bluetoothInput = bluetoothInput.copy { fixedPin = it }
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
