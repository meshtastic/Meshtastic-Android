package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ChannelProtos.ChannelSettings
import com.geeksville.mesh.ConfigProtos.Config.LoRaConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.numChannels
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditListPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun LoRaConfigItemList(
    loraConfig: LoRaConfig,
    primarySettings: ChannelSettings,
    enabled: Boolean,
    onSaveClicked: (LoRaConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var loraInput by remember(loraConfig) { mutableStateOf(loraConfig) }
    val primaryChannel = Channel(primarySettings, loraInput)

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "LoRa Config") }

        item {
            SwitchPreference(title = "Use modem preset",
                checked = loraInput.usePreset,
                enabled = enabled,
                onCheckedChange = { loraInput = loraInput.copy { usePreset = it } })
        }
        item { Divider() }

        if (loraInput.usePreset) {
            item {
                DropDownPreference(title = "Modem preset",
                    enabled = enabled && loraInput.usePreset,
                    items = LoRaConfig.ModemPreset.entries
                        .filter { it != LoRaConfig.ModemPreset.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = loraInput.modemPreset,
                    onItemSelected = { loraInput = loraInput.copy { modemPreset = it } })
            }
            item { Divider() }
        } else {
            item {
                EditTextPreference(title = "Bandwidth",
                    value = loraInput.bandwidth,
                    enabled = enabled && !loraInput.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { loraInput = loraInput.copy { bandwidth = it } })
            }

            item {
                EditTextPreference(title = "Spread factor",
                    value = loraInput.spreadFactor,
                    enabled = enabled && !loraInput.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { loraInput = loraInput.copy { spreadFactor = it } })
            }

            item {
                EditTextPreference(title = "Coding rate",
                    value = loraInput.codingRate,
                    enabled = enabled && !loraInput.usePreset,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { loraInput = loraInput.copy { codingRate = it } })
            }
        }

        item {
            EditTextPreference(title = "Frequency offset (MHz)",
                value = loraInput.frequencyOffset,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { loraInput = loraInput.copy { frequencyOffset = it } })
        }

        item {
            DropDownPreference(title = "Region (frequency plan)",
                enabled = enabled,
                items = LoRaConfig.RegionCode.entries
                    .filter { it != LoRaConfig.RegionCode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = loraInput.region,
                onItemSelected = { loraInput = loraInput.copy { region = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Hop limit",
                value = loraInput.hopLimit,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { loraInput = loraInput.copy { hopLimit = it } })
        }

        item {
            SwitchPreference(title = "TX enabled",
                checked = loraInput.txEnabled,
                enabled = enabled,
                onCheckedChange = { loraInput = loraInput.copy { txEnabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "TX power (dBm)",
                value = loraInput.txPower,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { loraInput = loraInput.copy { txPower = it } })
        }

        item {
            var isFocused by remember { mutableStateOf(false) }
            EditTextPreference(title = "Frequency slot",
                value = if (isFocused || loraInput.channelNum != 0) loraInput.channelNum else primaryChannel.channelNum,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onFocusChanged = { isFocused = it.isFocused },
                onValueChanged = {
                    if (it <= loraInput.numChannels) // total num of LoRa channels
                        loraInput = loraInput.copy { channelNum = it }
                })
        }

        item {
            SwitchPreference(title = "Override Duty Cycle",
                checked = loraInput.overrideDutyCycle,
                enabled = enabled,
                onCheckedChange = { loraInput = loraInput.copy { overrideDutyCycle = it } })
        }
        item { Divider() }

        item {
            EditListPreference(title = "Ignore incoming",
                list = loraInput.ignoreIncomingList,
                maxCount = 3, // ignore_incoming max_count:3
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValuesChanged = { list ->
                    loraInput = loraInput.copy {
                        ignoreIncoming.clear()
                        ignoreIncoming.addAll(list.filter { it != 0 })
                    }
                })
        }

        item {
            SwitchPreference(title = "SX126X RX boosted gain",
                checked = loraInput.sx126XRxBoostedGain,
                enabled = enabled,
                onCheckedChange = { loraInput = loraInput.copy { sx126XRxBoostedGain = it } })
        }
        item { Divider() }

        item {
            var isFocused by remember { mutableStateOf(false) }
            EditTextPreference(title = "Override frequency (MHz)",
                value = if (isFocused || loraInput.overrideFrequency != 0f) loraInput.overrideFrequency else primaryChannel.radioFreq,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onFocusChanged = { isFocused = it.isFocused },
                onValueChanged = { loraInput = loraInput.copy { overrideFrequency = it } })
        }

        item {
            SwitchPreference(title = "Ignore MQTT",
                checked = loraInput.ignoreMqtt,
                enabled = enabled,
                onCheckedChange = { loraInput = loraInput.copy { ignoreMqtt = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = loraInput != loraConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    loraInput = loraConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(loraInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoRaConfigPreview() {
    LoRaConfigItemList(
        loraConfig = Channel.default.loraConfig,
        primarySettings = Channel.default.settings,
        enabled = true,
        onSaveClicked = { },
    )
}
