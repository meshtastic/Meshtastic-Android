/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui.settings.radio.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos.Config.NetworkConfig
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.common.components.DropDownPreference
import com.geeksville.mesh.ui.common.components.EditIPv4Preference
import com.geeksville.mesh.ui.common.components.EditPasswordPreference
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SimpleAlertDialog
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.meshtastic.core.ui.R

@Composable
private fun ScanErrorDialog(onDismiss: () -> Unit = {}) =
    SimpleAlertDialog(title = R.string.error, text = R.string.wifi_qr_code_error, onDismiss = onDismiss)

@Composable
fun NetworkConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(state = state.responseState, onDismiss = viewModel::clearPacketResponse)
    }

    NetworkConfigItemList(
        hasWifi = state.metadata?.hasWifi ?: true,
        hasEthernet = state.metadata?.hasEthernet ?: true,
        networkConfig = state.radioConfig.network,
        enabled = state.connected,
        onSaveClicked = { networkInput ->
            val config = config { network = networkInput }
            viewModel.setConfig(config)
        },
    )
}

private fun extractWifiCredentials(qrCode: String) =
    Regex("""WIFI:S:(.*?);.*?P:(.*?);""").find(qrCode)?.destructured?.let { (ssid, password) -> ssid to password }
        ?: (null to null)

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NetworkConfigItemList(
    hasWifi: Boolean,
    hasEthernet: Boolean,
    networkConfig: NetworkConfig,
    enabled: Boolean,
    onSaveClicked: (NetworkConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var networkInput by rememberSaveable { mutableStateOf(networkConfig) }

    var showScanErrorDialog: Boolean by rememberSaveable { mutableStateOf(false) }
    if (showScanErrorDialog) {
        ScanErrorDialog { showScanErrorDialog = false }
    }

    val barcodeLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                val (ssid, psk) = extractWifiCredentials(result.contents)
                if (ssid != null && psk != null) {
                    networkInput =
                        networkInput.copy {
                            wifiSsid = ssid
                            wifiPsk = psk
                        }
                } else {
                    showScanErrorDialog = true
                }
            }
        }

    fun zxingScan() {
        val zxingScan =
            ScanOptions().apply {
                setCameraId(0)
                setPrompt("")
                setBeepEnabled(false)
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            }
        barcodeLauncher.launch(zxingScan)
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (hasWifi) {
            item { PreferenceCategory(text = stringResource(R.string.wifi_config)) }
            item {
                SwitchPreference(
                    title = stringResource(R.string.wifi_enabled),
                    summary = stringResource(id = R.string.config_network_wifi_enabled_summary),
                    checked = networkInput.wifiEnabled,
                    enabled = enabled && hasWifi,
                    onCheckedChange = { networkInput = networkInput.copy { wifiEnabled = it } },
                )
                HorizontalDivider()
            }

            item {
                EditTextPreference(
                    title = stringResource(R.string.ssid),
                    value = networkInput.wifiSsid,
                    maxSize = 32, // wifi_ssid max_size:33
                    enabled = enabled && hasWifi,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { networkInput = networkInput.copy { wifiSsid = it } },
                )
            }

            item {
                EditPasswordPreference(
                    title = stringResource(R.string.password),
                    value = networkInput.wifiPsk,
                    maxSize = 64, // wifi_psk max_size:65
                    enabled = enabled && hasWifi,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { networkInput = networkInput.copy { wifiPsk = it } },
                )
            }

            item {
                Button(
                    onClick = { zxingScan() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(48.dp),
                    enabled = enabled && hasWifi,
                ) {
                    Text(text = stringResource(R.string.wifi_qr_code_scan))
                }
            }
        }
        if (hasEthernet) {
            item { PreferenceCategory(text = stringResource(R.string.ethernet_config)) }
            item {
                SwitchPreference(
                    title = stringResource(R.string.ethernet_enabled),
                    summary = stringResource(id = R.string.config_network_eth_enabled_summary),
                    checked = networkInput.ethEnabled,
                    enabled = enabled && hasEthernet,
                    onCheckedChange = { networkInput = networkInput.copy { ethEnabled = it } },
                )
                HorizontalDivider()
            }
        }

        if (hasEthernet || hasWifi) {
            item { PreferenceCategory(text = stringResource(R.string.udp_config)) }

            item {
                SwitchPreference(
                    title = stringResource(R.string.udp_enabled),
                    summary = stringResource(id = R.string.config_network_udp_enabled_summary),
                    checked = networkInput.enabledProtocols == 1,
                    enabled = enabled,
                    onCheckedChange = {
                        networkInput = networkInput.copy { if (it) enabledProtocols = 1 else enabledProtocols = 0 }
                    },
                )
            }

            item { HorizontalDivider() }
        }

        item { PreferenceCategory(text = stringResource(R.string.advanced)) }
        item {
            EditTextPreference(
                title = stringResource(R.string.ntp_server),
                value = networkInput.ntpServer,
                maxSize = 32, // ntp_server max_size:33
                enabled = enabled,
                isError = networkInput.ntpServer.isEmpty(),
                keyboardOptions =
                KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { networkInput = networkInput.copy { ntpServer = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.rsyslog_server),
                value = networkInput.rsyslogServer,
                maxSize = 32, // rsyslog_server max_size:33
                enabled = enabled,
                isError = false,
                keyboardOptions =
                KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { networkInput = networkInput.copy { rsyslogServer = it } },
            )
        }

        item {
            DropDownPreference(
                title = stringResource(R.string.ipv4_mode),
                enabled = enabled,
                items =
                NetworkConfig.AddressMode.entries
                    .filter { it != NetworkConfig.AddressMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = networkInput.addressMode,
                onItemSelected = { networkInput = networkInput.copy { addressMode = it } },
            )
            HorizontalDivider()
        }

        item {
            EditIPv4Preference(
                title = stringResource(R.string.ip),
                value = networkInput.ipv4Config.ip,
                enabled = enabled && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { ip = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                },
            )
        }

        item {
            EditIPv4Preference(
                title = stringResource(R.string.gateway),
                value = networkInput.ipv4Config.gateway,
                enabled = enabled && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { gateway = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                },
            )
        }

        item {
            EditIPv4Preference(
                title = stringResource(R.string.subnet),
                value = networkInput.ipv4Config.subnet,
                enabled = enabled && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { subnet = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                },
            )
        }

        item {
            EditIPv4Preference(
                title = "DNS",
                value = networkInput.ipv4Config.dns,
                enabled = enabled && networkInput.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = networkInput.ipv4Config.copy { dns = it }
                    networkInput = networkInput.copy { ipv4Config = ipv4 }
                },
            )
        }
        item { HorizontalDivider() }

        item {
            PreferenceFooter(
                enabled = enabled && networkInput != networkConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    networkInput = networkConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(networkInput)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworkConfigPreview() {
    NetworkConfigItemList(
        hasWifi = true,
        hasEthernet = true,
        networkConfig = NetworkConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun QrCodeErrorDialogPreview() {
    ScanErrorDialog()
}
