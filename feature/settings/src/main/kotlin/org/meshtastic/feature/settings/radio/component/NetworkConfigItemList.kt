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

package org.meshtastic.feature.settings.radio.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.geeksville.mesh.ConfigProtos.Config.NetworkConfig
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditIPv4Preference
import org.meshtastic.core.ui.component.EditPasswordPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SimpleAlertDialog
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

@Composable
private fun ScanErrorDialog(onDismiss: () -> Unit = {}) =
    SimpleAlertDialog(title = R.string.error, text = R.string.wifi_qr_code_error, onDismiss = onDismiss)

@Composable
fun NetworkConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val networkConfig = state.radioConfig.network
    val formState = rememberConfigState(initialValue = networkConfig)

    var showScanErrorDialog: Boolean by rememberSaveable { mutableStateOf(false) }
    if (showScanErrorDialog) {
        ScanErrorDialog { showScanErrorDialog = false }
    }

    val barcodeLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                val (ssid, psk) = extractWifiCredentials(result.contents)
                if (ssid != null && psk != null) {
                    formState.value =
                        formState.value.copy {
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
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.network),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = config { network = it }
            viewModel.setConfig(config)
        },
    ) {
        if (state.metadata?.hasWifi == true) {
            item { PreferenceCategory(text = stringResource(R.string.wifi_config)) }
            item {
                SwitchPreference(
                    title = stringResource(R.string.wifi_enabled),
                    summary = stringResource(id = R.string.config_network_wifi_enabled_summary),
                    checked = formState.value.wifiEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { wifiEnabled = it } },
                )
                HorizontalDivider()
            }

            item {
                EditTextPreference(
                    title = stringResource(R.string.ssid),
                    value = formState.value.wifiSsid,
                    maxSize = 32, // wifi_ssid max_size:33
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { wifiSsid = it } },
                )
            }

            item {
                EditPasswordPreference(
                    title = stringResource(R.string.password),
                    value = formState.value.wifiPsk,
                    maxSize = 64, // wifi_psk max_size:65
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { wifiPsk = it } },
                )
            }

            item {
                Button(
                    onClick = { zxingScan() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(48.dp),
                    enabled = state.connected,
                ) {
                    Text(text = stringResource(R.string.wifi_qr_code_scan))
                }
            }
        }
        if (state.metadata?.hasEthernet == true) {
            item { PreferenceCategory(text = stringResource(R.string.ethernet_config)) }
            item {
                SwitchPreference(
                    title = stringResource(R.string.ethernet_enabled),
                    summary = stringResource(id = R.string.config_network_eth_enabled_summary),
                    checked = formState.value.ethEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { ethEnabled = it } },
                )
                HorizontalDivider()
            }
        }

        if (state.metadata?.hasEthernet == true || state.metadata?.hasWifi == true) {
            item { PreferenceCategory(text = stringResource(R.string.udp_config)) }

            item {
                SwitchPreference(
                    title = stringResource(R.string.udp_enabled),
                    summary = stringResource(id = R.string.config_network_udp_enabled_summary),
                    checked = formState.value.enabledProtocols == 1,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value =
                            formState.value.copy { if (it) enabledProtocols = 1 else enabledProtocols = 0 }
                    },
                )
            }

            item { HorizontalDivider() }
        }

        item { PreferenceCategory(text = stringResource(R.string.advanced)) }
        item {
            EditTextPreference(
                title = stringResource(R.string.ntp_server),
                value = formState.value.ntpServer,
                maxSize = 32, // ntp_server max_size:33
                enabled = state.connected,
                isError = formState.value.ntpServer.isEmpty(),
                keyboardOptions =
                KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { ntpServer = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.rsyslog_server),
                value = formState.value.rsyslogServer,
                maxSize = 32, // rsyslog_server max_size:33
                enabled = state.connected,
                isError = false,
                keyboardOptions =
                KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { rsyslogServer = it } },
            )
        }

        item {
            DropDownPreference(
                title = stringResource(R.string.ipv4_mode),
                enabled = state.connected,
                items =
                NetworkConfig.AddressMode.entries
                    .filter { it != NetworkConfig.AddressMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = formState.value.addressMode,
                onItemSelected = { formState.value = formState.value.copy { addressMode = it } },
            )
            HorizontalDivider()
        }

        item {
            EditIPv4Preference(
                title = stringResource(R.string.ip),
                value = formState.value.ipv4Config.ip,
                enabled = state.connected && formState.value.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = formState.value.ipv4Config.copy { ip = it }
                    formState.value = formState.value.copy { ipv4Config = ipv4 }
                },
            )
        }

        item {
            EditIPv4Preference(
                title = stringResource(R.string.gateway),
                value = formState.value.ipv4Config.gateway,
                enabled = state.connected && formState.value.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = formState.value.ipv4Config.copy { gateway = it }
                    formState.value = formState.value.copy { ipv4Config = ipv4 }
                },
            )
        }

        item {
            EditIPv4Preference(
                title = stringResource(R.string.subnet),
                value = formState.value.ipv4Config.subnet,
                enabled = state.connected && formState.value.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = formState.value.ipv4Config.copy { subnet = it }
                    formState.value = formState.value.copy { ipv4Config = ipv4 }
                },
            )
        }

        item {
            EditIPv4Preference(
                title = "DNS",
                value = formState.value.ipv4Config.dns,
                enabled = state.connected && formState.value.addressMode == NetworkConfig.AddressMode.STATIC,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    val ipv4 = formState.value.ipv4Config.copy { dns = it }
                    formState.value = formState.value.copy { ipv4Config = ipv4 }
                },
            )
        }
        item { HorizontalDivider() }
    }
}

private fun extractWifiCredentials(qrCode: String) =
    Regex("""WIFI:S:(.*?);.*?P:(.*?);""").find(qrCode)?.destructured?.let { (ssid, password) -> ssid to password }
        ?: (null to null)
