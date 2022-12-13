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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun ModuleSettingsItemList(viewModel: UIViewModel) {
    val focusManager = LocalFocusManager.current

    val connectionState by viewModel.connectionState.observeAsState()
    val connected = connectionState == MeshService.ConnectionState.CONNECTED

    val moduleConfig by viewModel.moduleConfig.collectAsState()

    // Temporary [ModuleConfigProtos.ModuleConfig] state holders
    var mqttInput by remember(moduleConfig.mqtt) { mutableStateOf(moduleConfig.mqtt) }
    var serialInput by remember(moduleConfig.serial) { mutableStateOf(moduleConfig.serial) }
    var externalNotificationInput by remember(moduleConfig.externalNotification) { mutableStateOf(moduleConfig.externalNotification) }
    var storeForwardInput by remember(moduleConfig.storeForward) { mutableStateOf(moduleConfig.storeForward) }
    var rangeTestInput by remember(moduleConfig.rangeTest) { mutableStateOf(moduleConfig.rangeTest) }
    var telemetryInput by remember(moduleConfig.telemetry) { mutableStateOf(moduleConfig.telemetry) }
    var cannedMessageInput by remember(moduleConfig.cannedMessage) { mutableStateOf(moduleConfig.cannedMessage) }
    var audioInput by remember(moduleConfig.audio) { mutableStateOf(moduleConfig.audio) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "MQTT Config") }

        item {
            SwitchPreference(title = "MQTT enabled",
                checked = mqttInput.enabled,
                enabled = connected,
                onCheckedChange = { mqttInput = mqttInput.copy { enabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Address",
                value = mqttInput.address,
                enabled = connected,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 31) // address max_size:32
                        mqttInput = mqttInput.copy { address = value }
                })
        }

        item {
            EditTextPreference(title = "Username",
                value = mqttInput.username,
                enabled = connected,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 63) // username max_size:64
                        mqttInput = mqttInput.copy { username = value }
                })
        }

        item {
            EditTextPreference(title = "Password",
                value = mqttInput.password,
                enabled = connected,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 63) // password max_size:64
                        mqttInput = mqttInput.copy { password = value }
                })
        }

        item {
            SwitchPreference(title = "Encryption enabled",
                checked = mqttInput.encryptionEnabled,
                enabled = connected,
                onCheckedChange = { mqttInput = mqttInput.copy { encryptionEnabled = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "JSON output enabled",
                checked = mqttInput.jsonEnabled,
                enabled = connected,
                onCheckedChange = { mqttInput = mqttInput.copy { jsonEnabled = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = mqttInput != moduleConfig.mqtt,
                onCancelClicked = {
                    focusManager.clearFocus()
                    mqttInput = moduleConfig.mqtt
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateMQTTConfig { mqttInput }
                })
        }

        item { PreferenceCategory(text = "Serial Config") }

        item {
            SwitchPreference(title = "Serial enabled",
                checked = serialInput.enabled,
                enabled = connected,
                onCheckedChange = { serialInput = serialInput.copy { enabled = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Echo enabled",
                checked = serialInput.echo,
                enabled = connected,
                onCheckedChange = { serialInput = serialInput.copy { echo = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "RX",
                value = serialInput.rxd,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { serialInput = serialInput.copy { rxd = it } })
        }

        item {
            EditTextPreference(title = "TX",
                value = serialInput.txd,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { serialInput = serialInput.copy { txd = it } })
        }

        item {
            DropDownPreference(title = "Serial baud rate",
                enabled = connected,
                items = ModuleConfig.SerialConfig.Serial_Baud.values()
                    .filter { it != ModuleConfig.SerialConfig.Serial_Baud.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = serialInput.baud,
                onItemSelected = { serialInput = serialInput.copy { baud = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Timeout",
                value = serialInput.timeout,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { serialInput = serialInput.copy { timeout = it } })
        }

        item {
            DropDownPreference(title = "Serial mode",
                enabled = connected,
                items = ModuleConfig.SerialConfig.Serial_Mode.values()
                    .filter { it != ModuleConfig.SerialConfig.Serial_Mode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = serialInput.mode,
                onItemSelected = { serialInput = serialInput.copy { mode = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = serialInput != moduleConfig.serial,
                onCancelClicked = {
                    focusManager.clearFocus()
                    serialInput = moduleConfig.serial
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateSerialConfig { serialInput }
                })
        }

        item { PreferenceCategory(text = "External Notification Config") }

        item {
            SwitchPreference(title = "External notification enabled",
                checked = externalNotificationInput.enabled,
                enabled = connected,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { enabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Output milliseconds",
                value = externalNotificationInput.outputMs,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    externalNotificationInput = externalNotificationInput.copy { outputMs = it }
                })
        }

        item {
            EditTextPreference(title = "Output",
                value = externalNotificationInput.output,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    externalNotificationInput = externalNotificationInput.copy { output = it }
                })
        }

        item {
            SwitchPreference(title = "Active",
                checked = externalNotificationInput.active,
                enabled = connected,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { active = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Alert message",
                checked = externalNotificationInput.alertMessage,
                enabled = connected,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { alertMessage = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Alert bell",
                checked = externalNotificationInput.alertBell,
                enabled = connected,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { alertBell = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Use PWM buzzer",
                checked = externalNotificationInput.usePwm,
                enabled = connected,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { usePwm = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = externalNotificationInput != moduleConfig.externalNotification,
                onCancelClicked = {
                    focusManager.clearFocus()
                    externalNotificationInput = moduleConfig.externalNotification
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateExternalNotificationConfig { externalNotificationInput }
                })
        }

        item { PreferenceCategory(text = "Store & Forward Config") }

        item {
            SwitchPreference(title = "Store & Forward enabled",
                checked = storeForwardInput.enabled,
                enabled = connected,
                onCheckedChange = { storeForwardInput = storeForwardInput.copy { enabled = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Heartbeat",
                checked = storeForwardInput.heartbeat,
                enabled = connected,
                onCheckedChange = { storeForwardInput = storeForwardInput.copy { heartbeat = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Number of records",
                value = storeForwardInput.records,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { storeForwardInput = storeForwardInput.copy { records = it } })
        }

        item {
            EditTextPreference(title = "History return max",
                value = storeForwardInput.historyReturnMax,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    storeForwardInput = storeForwardInput.copy { historyReturnMax = it }
                })
        }

        item {
            EditTextPreference(title = "History return window",
                value = storeForwardInput.historyReturnWindow,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    storeForwardInput = storeForwardInput.copy { historyReturnWindow = it }
                })
        }

        item {
            PreferenceFooter(
                enabled = storeForwardInput != moduleConfig.storeForward,
                onCancelClicked = {
                    focusManager.clearFocus()
                    storeForwardInput = moduleConfig.storeForward
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateStoreForwardConfig { storeForwardInput }
                })
        }

        item { PreferenceCategory(text = "Range Test Config") }

        item {
            SwitchPreference(title = "Range test enabled",
                checked = rangeTestInput.enabled,
                enabled = connected,
                onCheckedChange = { rangeTestInput = rangeTestInput.copy { enabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Sender message interval",
                value = rangeTestInput.sender,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { rangeTestInput = rangeTestInput.copy { sender = it } })
        }

        item {
            SwitchPreference(title = "Save .CSV in storage (ESP32 only)",
                checked = rangeTestInput.save,
                enabled = connected,
                onCheckedChange = { rangeTestInput = rangeTestInput.copy { save = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = rangeTestInput != moduleConfig.rangeTest,
                onCancelClicked = {
                    focusManager.clearFocus()
                    rangeTestInput = moduleConfig.rangeTest
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateRangeTestConfig { rangeTestInput }
                })
        }

        item { PreferenceCategory(text = "Telemetry Config") }

        item {
            EditTextPreference(title = "Device metrics update interval",
                value = telemetryInput.deviceUpdateInterval,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    telemetryInput = telemetryInput.copy { deviceUpdateInterval = it }
                })
        }

        item {
            EditTextPreference(title = "Environment metrics update interval",
                value = telemetryInput.environmentUpdateInterval,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    telemetryInput = telemetryInput.copy { environmentUpdateInterval = it }
                })
        }

        item {
            SwitchPreference(title = "Environment metrics module enabled",
                checked = telemetryInput.environmentMeasurementEnabled,
                enabled = connected,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { environmentMeasurementEnabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Environment metrics on-screen enabled",
                checked = telemetryInput.environmentScreenEnabled,
                enabled = connected,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { environmentScreenEnabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Environment metrics use Fahrenheit",
                checked = telemetryInput.environmentDisplayFahrenheit,
                enabled = connected,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { environmentDisplayFahrenheit = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = telemetryInput != moduleConfig.telemetry,
                onCancelClicked = {
                    focusManager.clearFocus()
                    telemetryInput = moduleConfig.telemetry
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateTelemetryConfig { telemetryInput }
                })
        }

        item { PreferenceCategory(text = "Canned Message Config") }

        item {
            SwitchPreference(title = "Canned message enabled",
                checked = cannedMessageInput.enabled,
                enabled = connected,
                onCheckedChange = {
                    cannedMessageInput = cannedMessageInput.copy { enabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Rotary encoder #1 enabled",
                checked = cannedMessageInput.rotary1Enabled,
                enabled = connected,
                onCheckedChange = {
                    cannedMessageInput = cannedMessageInput.copy { rotary1Enabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "GPIO pin for rotary encoder A port",
                value = cannedMessageInput.inputbrokerPinA,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerPinA = it }
                })
        }

        item {
            EditTextPreference(title = "GPIO pin for rotary encoder B port",
                value = cannedMessageInput.inputbrokerPinB,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerPinB = it }
                })
        }

        item {
            EditTextPreference(title = "GPIO pin for rotary encoder Press port",
                value = cannedMessageInput.inputbrokerPinPress,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerPinPress = it }
                })
        }

        item {
            DropDownPreference(title = "Generate input event on Press",
                enabled = connected,
                items = ModuleConfig.CannedMessageConfig.InputEventChar.values()
                    .filter { it != ModuleConfig.CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = cannedMessageInput.inputbrokerEventPress,
                onItemSelected = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerEventPress = it }
                })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Generate input event on CW",
                enabled = connected,
                items = ModuleConfig.CannedMessageConfig.InputEventChar.values()
                    .filter { it != ModuleConfig.CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = cannedMessageInput.inputbrokerEventCw,
                onItemSelected = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerEventCw = it }
                })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Generate input event on CCW",
                enabled = connected,
                items = ModuleConfig.CannedMessageConfig.InputEventChar.values()
                    .filter { it != ModuleConfig.CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = cannedMessageInput.inputbrokerEventCcw,
                onItemSelected = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerEventCcw = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Up/Down/Select input enabled",
                checked = cannedMessageInput.updown1Enabled,
                enabled = connected,
                onCheckedChange = {
                    cannedMessageInput = cannedMessageInput.copy { updown1Enabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Allow input source",
                value = cannedMessageInput.allowInputSource,
                enabled = connected,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { value ->
                    if (value.toByteArray().size <= 15) // allow_input_source max_size:16
                        cannedMessageInput = cannedMessageInput.copy { allowInputSource = value }
                })
        }

        item {
            SwitchPreference(title = "Send bell",
                checked = cannedMessageInput.sendBell,
                enabled = connected,
                onCheckedChange = {
                    cannedMessageInput = cannedMessageInput.copy { sendBell = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = cannedMessageInput != moduleConfig.cannedMessage,
                onCancelClicked = {
                    focusManager.clearFocus()
                    cannedMessageInput = moduleConfig.cannedMessage
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateCannedMessageConfig { cannedMessageInput }
                })
        }

        item { PreferenceCategory(text = "Audio Config") }

        item {
            SwitchPreference(title = "CODEC 2 enabled",
                checked = audioInput.codec2Enabled,
                enabled = connected,
                onCheckedChange = { audioInput = audioInput.copy { codec2Enabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "PTT pin",
                value = audioInput.pttPin,
                enabled = connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { pttPin = it } })
        }

        item {
            DropDownPreference(title = "CODEC2 sample rate",
                enabled = connected,
                items = ModuleConfig.AudioConfig.Audio_Baud.values()
                    .filter { it != ModuleConfig.AudioConfig.Audio_Baud.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = audioInput.bitrate,
                onItemSelected = { audioInput = audioInput.copy { bitrate = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = audioInput != moduleConfig.audio,
                onCancelClicked = {
                    focusManager.clearFocus()
                    audioInput = moduleConfig.audio
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    viewModel.updateAudioConfig { audioInput }
                })
        }
    }
}

