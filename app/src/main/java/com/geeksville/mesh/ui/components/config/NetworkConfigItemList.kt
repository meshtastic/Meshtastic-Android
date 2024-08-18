package com.geeksville.mesh.ui.components.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ConfigProtos.Config.NetworkConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditIPv4Preference
import com.geeksville.mesh.ui.components.EditPasswordPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun NetworkConfigItemList(
    networkConfig: NetworkConfig,
    enabled: Boolean,
    onSaveClicked: (NetworkConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var networkInput by rememberSaveable { mutableStateOf(networkConfig) }

    fun extractWifiCredentials(qrCode: String) = Regex("""WIFI:S:(.*?);.*?P:(.*?);""")
        .find(qrCode)?.destructured
        ?.let { (ssid, password) -> ssid to password }
        ?: (null to null)

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val (ssid, psk) = extractWifiCredentials(result.contents)
            if (ssid != null && psk != null) {
                networkInput = networkInput.copy { wifiSsid = ssid }
                networkInput = networkInput.copy { wifiPsk = psk }
            } else {
                // TODO show: "Invalid WiFi Credential QR code format"
            }
        }
    }

    fun zxingScan() {
        val zxingScan = ScanOptions().apply {
            setCameraId(0)
            setPrompt("")
            setBeepEnabled(false)
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        }
        barcodeLauncher.launch(zxingScan)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Network Config") }

        item {
            SwitchPreference(title = "WiFi enabled",
                checked = networkInput.wifiEnabled,
                enabled = enabled,
                onCheckedChange = { networkInput = networkInput.copy { wifiEnabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "SSID",
                value = networkInput.wifiSsid,
                maxSize = 32, // wifi_ssid max_size:33
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    networkInput = networkInput.copy { wifiSsid = it }
                })
        }

        item {
            EditPasswordPreference(title = "PSK",
                value = networkInput.wifiPsk,
                maxSize = 64, // wifi_psk max_size:65
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { networkInput = networkInput.copy { wifiPsk = it } })
        }

        item {
            Button(
                onClick = { zxingScan() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .height(48.dp),
            ) {
                Text(text = "Scan WiFi QR code")
            }
        }

        item {
            EditTextPreference(title = "NTP server",
                value = networkInput.ntpServer,
                maxSize = 32, // ntp_server max_size:33
                enabled = enabled,
                isError = networkInput.ntpServer.isEmpty(),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    networkInput = networkInput.copy { ntpServer = it }
                })
        }

        item {
            EditTextPreference(title = "rsyslog server",
                value = networkInput.rsyslogServer,
                maxSize = 32, // rsyslog_server max_size:33
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    networkInput = networkInput.copy { rsyslogServer = it }
                })
        }

        item {
            SwitchPreference(title = "Ethernet enabled",
                checked = networkInput.ethEnabled,
                enabled = enabled,
                onCheckedChange = { networkInput = networkInput.copy { ethEnabled = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "IPv4 mode",
                enabled = enabled,
                items = NetworkConfig.AddressMode.entries
                    .filter { it != NetworkConfig.AddressMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = networkInput.addressMode,
                onItemSelected = { networkInput = networkInput.copy { addressMode = it } })
        }
        item { Divider() }

        item {
            EditIPv4Preference(title = "IP",
                value = networkInput.ipv4Config.ip,
                enabled = enabled && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { ip = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                })
        }

        item {
            EditIPv4Preference(title = "Gateway",
                value = networkInput.ipv4Config.gateway,
                enabled = enabled && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { gateway = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                })
        }

        item {
            EditIPv4Preference(title = "Subnet",
                value = networkInput.ipv4Config.subnet,
                enabled = enabled && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { subnet = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                })
        }

        item {
            EditIPv4Preference(title = "DNS",
                value = networkInput.ipv4Config.dns,
                enabled = enabled && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { dns = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                })
        }

        item {
            PreferenceFooter(
                enabled = networkInput != networkConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    networkInput = networkConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(networkInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworkConfigPreview() {
    NetworkConfigItemList(
        networkConfig = NetworkConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
