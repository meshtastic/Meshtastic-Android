package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.SerialConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun SerialConfigItemList(
    serialConfig: SerialConfig,
    enabled: Boolean,
    onSaveClicked: (SerialConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var serialInput by rememberSaveable { mutableStateOf(serialConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Serial Config") }

        item {
            SwitchPreference(title = "Serial enabled",
                checked = serialInput.enabled,
                enabled = enabled,
                onCheckedChange = { serialInput = serialInput.copy { this.enabled = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Echo enabled",
                checked = serialInput.echo,
                enabled = enabled,
                onCheckedChange = { serialInput = serialInput.copy { echo = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "RX",
                value = serialInput.rxd,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { serialInput = serialInput.copy { rxd = it } })
        }

        item {
            EditTextPreference(title = "TX",
                value = serialInput.txd,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { serialInput = serialInput.copy { txd = it } })
        }

        item {
            DropDownPreference(title = "Serial baud rate",
                enabled = enabled,
                items = SerialConfig.Serial_Baud.entries
                    .filter { it != SerialConfig.Serial_Baud.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = serialInput.baud,
                onItemSelected = { serialInput = serialInput.copy { baud = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Timeout",
                value = serialInput.timeout,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { serialInput = serialInput.copy { timeout = it } })
        }

        item {
            DropDownPreference(title = "Serial mode",
                enabled = enabled,
                items = SerialConfig.Serial_Mode.entries
                    .filter { it != SerialConfig.Serial_Mode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = serialInput.mode,
                onItemSelected = { serialInput = serialInput.copy { mode = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Override console serial port",
                checked = serialInput.overrideConsoleSerialPort,
                enabled = enabled,
                onCheckedChange = {
                    serialInput = serialInput.copy { overrideConsoleSerialPort = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = serialInput != serialConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    serialInput = serialConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(serialInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SerialConfigPreview() {
    SerialConfigItemList(
        serialConfig = SerialConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
