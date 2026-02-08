/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.barcode.extractWifiCredentials
import org.meshtastic.core.barcode.rememberBarcodeScanner
import org.meshtastic.core.model.util.handleMeshtasticUri
import org.meshtastic.core.nfc.NfcScannerEffect
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.advanced
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.config_network_eth_enabled_summary
import org.meshtastic.core.strings.config_network_udp_enabled_summary
import org.meshtastic.core.strings.config_network_wifi_enabled_summary
import org.meshtastic.core.strings.connection_status
import org.meshtastic.core.strings.error
import org.meshtastic.core.strings.ethernet_config
import org.meshtastic.core.strings.ethernet_enabled
import org.meshtastic.core.strings.ethernet_ip
import org.meshtastic.core.strings.gateway
import org.meshtastic.core.strings.ip
import org.meshtastic.core.strings.ipv4_mode
import org.meshtastic.core.strings.network
import org.meshtastic.core.strings.nfc_disabled
import org.meshtastic.core.strings.ntp_server
import org.meshtastic.core.strings.open_settings
import org.meshtastic.core.strings.password
import org.meshtastic.core.strings.rsyslog_server
import org.meshtastic.core.strings.scan_nfc
import org.meshtastic.core.strings.ssid
import org.meshtastic.core.strings.subnet
import org.meshtastic.core.strings.udp_enabled
import org.meshtastic.core.strings.wifi_config
import org.meshtastic.core.strings.wifi_enabled
import org.meshtastic.core.strings.wifi_ip
import org.meshtastic.core.strings.wifi_qr_code_error
import org.meshtastic.core.strings.wifi_qr_code_scan
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditIPv4Preference
import org.meshtastic.core.ui.component.EditPasswordPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.util.openNfcSettings
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.Config

@Composable
private fun ScanErrorDialog(onDismiss: () -> Unit = {}) =
    MeshtasticDialog(titleRes = Res.string.error, messageRes = Res.string.wifi_qr_code_error, onDismiss = onDismiss)

@Composable
fun NetworkConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val networkConfig = state.radioConfig.network ?: Config.NetworkConfig()
    val formState = rememberConfigState(initialValue = networkConfig)
    val context = LocalContext.current

    var showScanErrorDialog: Boolean by rememberSaveable { mutableStateOf(false) }
    if (showScanErrorDialog) {
        ScanErrorDialog { showScanErrorDialog = false }
    }

    var showNfcDisabledDialog: Boolean by rememberSaveable { mutableStateOf(false) }
    if (showNfcDisabledDialog) {
        MeshtasticDialog(
            onDismiss = { showNfcDisabledDialog = false },
            title = stringResource(Res.string.scan_nfc),
            message = stringResource(Res.string.nfc_disabled),
            confirmText = stringResource(Res.string.open_settings),
            onConfirm = {
                context.openNfcSettings()
                showNfcDisabledDialog = false
            },
            dismissText = stringResource(Res.string.cancel),
        )
    }

    val onResult: (String?) -> Unit = { contents ->
        if (contents != null) {
            val handled =
                handleMeshtasticUri(
                    uri = contents.toUri(),
                    onChannel = {}, // No-op, not supported in network config
                    onContact = {}, // No-op, not supported in network config
                )

            if (!handled) {
                val (ssid, psk) = extractWifiCredentials(contents)
                if (ssid != null && psk != null) {
                    formState.value = formState.value.copy(wifi_ssid = ssid, wifi_psk = psk)
                } else {
                    showScanErrorDialog = true
                }
            }
        }
    }

    val barcodeScanner = rememberBarcodeScanner(onResult = onResult)
    NfcScannerEffect(onResult = onResult, onNfcDisabled = { showNfcDisabledDialog = true })

    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.network),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = Config(network = it)
            viewModel.setConfig(config)
        },
    ) {
        // Display device connection status
        state.deviceConnectionStatus?.let { connectionStatus ->
            val ws = connectionStatus.wifi?.status
            val es = connectionStatus.ethernet?.status
            if (ws?.is_connected == true || es?.is_connected == true) {
                item {
                    TitledCard(title = stringResource(Res.string.connection_status)) {
                        ws?.let { wifiStatus ->
                            if (wifiStatus.is_connected) {
                                ListItem(
                                    text = stringResource(Res.string.wifi_ip),
                                    supportingText = formatIpAddress(wifiStatus.ip_address ?: 0),
                                    trailingIcon = null,
                                )
                            }
                        }
                        es?.let { ethernetStatus ->
                            if (ethernetStatus.is_connected) {
                                ListItem(
                                    text = stringResource(Res.string.ethernet_ip),
                                    supportingText = formatIpAddress(ethernetStatus.ip_address ?: 0),
                                    trailingIcon = null,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.metadata?.hasWifi == true) {
            item {
                TitledCard(title = stringResource(Res.string.wifi_config)) {
                    SwitchPreference(
                        title = stringResource(Res.string.wifi_enabled),
                        summary = stringResource(Res.string.config_network_wifi_enabled_summary),
                        checked = formState.value.wifi_enabled ?: false,
                        enabled = state.connected,
                        onCheckedChange = { formState.value = formState.value.copy(wifi_enabled = it) },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.ssid),
                        value = formState.value.wifi_ssid ?: "",
                        maxSize = 32, // wifi_ssid max_size:33
                        enabled = state.connected,
                        isError = false,
                        keyboardOptions =
                        KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy(wifi_ssid = it) },
                    )
                    HorizontalDivider()
                    EditPasswordPreference(
                        title = stringResource(Res.string.password),
                        value = formState.value.wifi_psk ?: "",
                        maxSize = 64, // wifi_psk max_size:65
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { formState.value = formState.value.copy(wifi_psk = it) },
                    )
                    HorizontalDivider()
                    Button(
                        onClick = { barcodeScanner.startScan() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(48.dp),
                        enabled = state.connected,
                    ) {
                        Text(text = stringResource(Res.string.wifi_qr_code_scan))
                    }
                }
            }
        }
        if (state.metadata?.hasEthernet == true) {
            item {
                TitledCard(title = stringResource(Res.string.ethernet_config)) {
                    SwitchPreference(
                        title = stringResource(Res.string.ethernet_enabled),
                        summary = stringResource(Res.string.config_network_eth_enabled_summary),
                        checked = formState.value.eth_enabled ?: false,
                        enabled = state.connected,
                        onCheckedChange = { formState.value = formState.value.copy(eth_enabled = it) },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                }
            }
        }

        if (state.metadata?.hasEthernet == true || state.metadata?.hasWifi == true) {
            item {
                TitledCard(title = stringResource(Res.string.network)) {
                    SwitchPreference(
                        title = stringResource(Res.string.udp_enabled),
                        summary = stringResource(Res.string.config_network_udp_enabled_summary),
                        checked = (formState.value.enabled_protocols ?: 0) == 1,
                        enabled = state.connected,
                        onCheckedChange = {
                            formState.value = formState.value.copy(enabled_protocols = if (it) 1 else 0)
                        },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                }
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.advanced)) {
                EditTextPreference(
                    title = stringResource(Res.string.ntp_server),
                    value = formState.value.ntp_server ?: "",
                    maxSize = 32, // ntp_server max_size:33
                    enabled = state.connected,
                    isError = formState.value.ntp_server?.isEmpty() ?: true,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(ntp_server = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.rsyslog_server),
                    value = formState.value.rsyslog_server ?: "",
                    maxSize = 32, // rsyslog_server max_size:33
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(rsyslog_server = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.ipv4_mode),
                    enabled = state.connected,
                    items = Config.NetworkConfig.AddressMode.entries.map { it to it.name },
                    selectedItem = formState.value.address_mode ?: Config.NetworkConfig.AddressMode.DHCP,
                    onItemSelected = { formState.value = formState.value.copy(address_mode = it) },
                )
                HorizontalDivider()
                val ipv4 = formState.value.ipv4_config ?: Config.NetworkConfig.IpV4Config()
                EditIPv4Preference(
                    title = stringResource(Res.string.ip),
                    value = ipv4.ip ?: 0,
                    enabled =
                    state.connected && formState.value.address_mode == Config.NetworkConfig.AddressMode.STATIC,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(ipv4_config = ipv4.copy(ip = it)) },
                )
                HorizontalDivider()
                EditIPv4Preference(
                    title = stringResource(Res.string.gateway),
                    value = ipv4.gateway ?: 0,
                    enabled =
                    state.connected && formState.value.address_mode == Config.NetworkConfig.AddressMode.STATIC,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(ipv4_config = ipv4.copy(gateway = it)) },
                )
                HorizontalDivider()
                EditIPv4Preference(
                    title = stringResource(Res.string.subnet),
                    value = ipv4.subnet ?: 0,
                    enabled =
                    state.connected && formState.value.address_mode == Config.NetworkConfig.AddressMode.STATIC,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(ipv4_config = ipv4.copy(subnet = it)) },
                )
                HorizontalDivider()
                EditIPv4Preference(
                    title = "DNS",
                    value = ipv4.dns ?: 0,
                    enabled =
                    state.connected && formState.value.address_mode == Config.NetworkConfig.AddressMode.STATIC,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(ipv4_config = ipv4.copy(dns = it)) },
                )
            }
        }
    }
}

@Suppress("detekt:MagicNumber")
private fun formatIpAddress(ipAddress: Int): String = "${(ipAddress) and 0xFF}." +
    "${(ipAddress shr 8) and 0xFF}." +
    "${(ipAddress shr 16) and 0xFF}." +
    "${(ipAddress shr 24) and 0xFF}"
