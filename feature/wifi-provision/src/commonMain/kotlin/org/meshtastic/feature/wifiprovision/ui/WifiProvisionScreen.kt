/*
 * Copyright (c) 2026 Meshtastic LLC
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
@file:Suppress("TooManyFunctions", "LongMethod")

package org.meshtastic.feature.wifiprovision.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.action_select_network
import org.meshtastic.core.resources.apply
import org.meshtastic.core.resources.back
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.hide_password
import org.meshtastic.core.resources.img_mpwrd_logo
import org.meshtastic.core.resources.mpwrd_os
import org.meshtastic.core.resources.password
import org.meshtastic.core.resources.show_password
import org.meshtastic.core.resources.wifi_provision_available_networks
import org.meshtastic.core.resources.wifi_provision_connect_failed
import org.meshtastic.core.resources.wifi_provision_description
import org.meshtastic.core.resources.wifi_provision_device_found
import org.meshtastic.core.resources.wifi_provision_device_found_detail
import org.meshtastic.core.resources.wifi_provision_mpwrd_disclaimer
import org.meshtastic.core.resources.wifi_provision_no_networks
import org.meshtastic.core.resources.wifi_provision_scan_failed
import org.meshtastic.core.resources.wifi_provision_scan_networks
import org.meshtastic.core.resources.wifi_provision_scanning_ble
import org.meshtastic.core.resources.wifi_provision_scanning_wifi
import org.meshtastic.core.resources.wifi_provision_sending_credentials
import org.meshtastic.core.resources.wifi_provision_signal_strength
import org.meshtastic.core.resources.wifi_provision_ssid_label
import org.meshtastic.core.resources.wifi_provision_ssid_placeholder
import org.meshtastic.core.resources.wifi_provision_success_description
import org.meshtastic.core.resources.wifi_provision_success_device_connected
import org.meshtastic.core.resources.wifi_provision_success_done
import org.meshtastic.core.resources.wifi_provision_success_ip_address
import org.meshtastic.core.resources.wifi_provision_success_missing_ip
import org.meshtastic.core.resources.wifi_provision_success_open_ssh
import org.meshtastic.core.resources.wifi_provision_success_open_ssh_fallback
import org.meshtastic.core.resources.wifi_provision_success_password_value
import org.meshtastic.core.resources.wifi_provision_success_setup_description
import org.meshtastic.core.resources.wifi_provision_success_setup_title
import org.meshtastic.core.resources.wifi_provision_success_ssh_command
import org.meshtastic.core.resources.wifi_provision_success_ssh_label
import org.meshtastic.core.resources.wifi_provision_success_ssh_unavailable
import org.meshtastic.core.resources.wifi_provision_success_username
import org.meshtastic.core.resources.wifi_provision_success_username_value
import org.meshtastic.core.resources.wifi_provisioning
import org.meshtastic.core.ui.component.AutoLinkText
import org.meshtastic.core.ui.component.CopyIconButton
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.CheckCircle
import org.meshtastic.core.ui.icon.Lock
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Serial
import org.meshtastic.core.ui.icon.Visibility
import org.meshtastic.core.ui.icon.VisibilityOff
import org.meshtastic.core.ui.icon.Wifi
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.rememberOpenUrl
import org.meshtastic.feature.wifiprovision.WifiProvisionError
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.Phase
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.ProvisionStatus
import org.meshtastic.feature.wifiprovision.WifiProvisionViewModel
import org.meshtastic.feature.wifiprovision.model.WifiNetwork

private const val NETWORK_LIST_MAX_HEIGHT_DP = 240

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod")
@Composable
fun WifiProvisionScreen(
    onNavigateUp: () -> Unit,
    address: String? = null,
    viewModel: WifiProvisionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage =
        uiState.error?.let { error ->
            when (error) {
                is WifiProvisionError.ConnectFailed ->
                    stringResource(Res.string.wifi_provision_connect_failed, error.detail)
                is WifiProvisionError.ScanFailed -> stringResource(Res.string.wifi_provision_scan_failed, error.detail)
                is WifiProvisionError.ProvisionFailed -> error.detail
            }
        }

    LaunchedEffect(uiState.error) { errorMessage?.let { snackbarHostState.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.connectToDevice(address) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.wifi_provisioning)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(MeshtasticIcons.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().animateContentSize()) {
            // Indeterminate progress bar for active operations
            if (uiState.phase.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }

            MpwrdDisclaimerBanner()

            Crossfade(targetState = screenKey(uiState), label = "wifi_provision") { key ->
                when (key) {
                    ScreenKey.ConnectingBle -> ScanningBleContent()
                    ScreenKey.DeviceFound ->
                        DeviceFoundContent(
                            deviceName = uiState.deviceName,
                            onProceed = viewModel::scanNetworks,
                            onCancel = onNavigateUp,
                        )
                    ScreenKey.LoadingNetworks -> ScanningNetworksContent()
                    ScreenKey.Connected ->
                        ConnectedContent(
                            networks = uiState.networks,
                            provisionStatus = uiState.provisionStatus,
                            ipAddress = uiState.ipAddress,
                            isProvisioning = uiState.phase == Phase.Provisioning,
                            isScanning = uiState.phase == Phase.LoadingNetworks,
                            onScanNetworks = viewModel::scanNetworks,
                            onProvision = viewModel::provisionWifi,
                            onDisconnect = {
                                viewModel.disconnect()
                                onNavigateUp()
                            },
                        )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen-key helper for Crossfade
// ---------------------------------------------------------------------------

private enum class ScreenKey {
    ConnectingBle,
    DeviceFound,
    LoadingNetworks,
    Connected,
}

private fun screenKey(state: WifiProvisionUiState): ScreenKey = when (state.phase) {
    Phase.Idle,
    Phase.ConnectingBle,
    -> ScreenKey.ConnectingBle
    Phase.DeviceFound -> ScreenKey.DeviceFound
    Phase.LoadingNetworks -> if (state.networks.isEmpty()) ScreenKey.LoadingNetworks else ScreenKey.Connected
    Phase.Connected,
    Phase.Provisioning,
    -> ScreenKey.Connected
}

private val Phase.isLoading: Boolean
    get() = this == Phase.ConnectingBle || this == Phase.LoadingNetworks || this == Phase.Provisioning

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

/** BLE scanning spinner — shown while searching for a device. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScanningBleContent() {
    CenteredStatusContent {
        LoadingIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(Res.string.wifi_provision_scanning_ble), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Confirmation step shown after BLE device discovery — the Android analog of the web flasher's native BLE pairing
 * prompt. Gives the user a clear "device found" moment before proceeding.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeviceFoundContent(deviceName: String?, onProceed: () -> Unit, onCancel: () -> Unit) {
    CenteredStatusContent {
        Icon(
            MeshtasticIcons.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(Res.string.wifi_provision_device_found),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
        )
        if (deviceName != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                deviceName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.wifi_provision_device_found_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            Button(onClick = onProceed) { Text(stringResource(Res.string.wifi_provision_scan_networks)) }
        }
    }
}

/** Network scanning spinner — shown during the initial scan when no networks are loaded yet. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScanningNetworksContent() {
    CenteredStatusContent {
        LoadingIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(Res.string.wifi_provision_scanning_wifi), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Main configuration screen shown after BLE connection — mirrors the web flasher's connected state. All controls (scan
 * button, network list, SSID/password fields, Apply, status) are on one screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod", "LongParameterList")
@Composable
internal fun ConnectedContent(
    networks: List<WifiNetwork>,
    provisionStatus: ProvisionStatus,
    ipAddress: String?,
    isProvisioning: Boolean,
    isScanning: Boolean,
    onScanNetworks: () -> Unit,
    onProvision: (ssid: String, password: String) -> Unit,
    onDisconnect: () -> Unit,
) {
    if (provisionStatus == ProvisionStatus.Success) {
        ProvisionSuccessContent(ipAddress = ipAddress, onDone = onDisconnect)
        return
    }

    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(provisionStatus) {
        if (provisionStatus == ProvisionStatus.Success) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        modifier =
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.wifi_provision_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Scan button — FilledTonalButton for prominent secondary action
        FilledTonalButton(
            onClick = onScanNetworks,
            enabled = !isScanning && !isProvisioning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isScanning) {
                LoadingIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(MeshtasticIcons.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (isScanning) {
                    stringResource(Res.string.wifi_provision_scanning_wifi)
                } else {
                    stringResource(Res.string.wifi_provision_scan_networks)
                },
            )
        }

        // Network list (scrollable, capped height) — animated entrance
        AnimatedVisibility(
            visible = networks.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Text(
                    stringResource(Res.string.wifi_provision_available_networks),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = NETWORK_LIST_MAX_HEIGHT_DP.dp)) {
                        items(networks, key = { it.ssid }) { network ->
                            NetworkRow(
                                network = network,
                                isSelected = network.ssid == ssid,
                                onClick = { ssid = network.ssid },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = networks.isEmpty() && !isScanning, enter = fadeIn(), exit = fadeOut()) {
            Text(
                stringResource(Res.string.wifi_provision_no_networks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // SSID input
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text(stringResource(Res.string.wifi_provision_ssid_label)) },
            placeholder = { Text(stringResource(Res.string.wifi_provision_ssid_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Password input
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(Res.string.password)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconToggleButton(checked = passwordVisible, onCheckedChange = { passwordVisible = it }) {
                    Icon(
                        imageVector =
                        if (passwordVisible) MeshtasticIcons.VisibilityOff else MeshtasticIcons.Visibility,
                        contentDescription =
                        if (passwordVisible) {
                            stringResource(Res.string.hide_password)
                        } else {
                            stringResource(Res.string.show_password)
                        },
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onProvision(ssid, password) }),
            modifier = Modifier.fillMaxWidth(),
        )

        // Inline provision status (matches web flasher's status chip) — animated entrance
        AnimatedVisibility(
            visible = provisionStatus != ProvisionStatus.Idle || isProvisioning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            ProvisionStatusCard(provisionStatus = provisionStatus, isProvisioning = isProvisioning)
        }

        // Action buttons — cancel left, primary action right (app convention)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDisconnect) { Text(stringResource(Res.string.cancel)) }
            Button(
                onClick = { onProvision(ssid, password) },
                enabled = ssid.isNotBlank() && !isProvisioning,
                modifier = Modifier.weight(1f),
            ) {
                if (isProvisioning) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.wifi_provision_sending_credentials))
                } else {
                    Icon(MeshtasticIcons.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.apply))
                }
            }
        }
    }
}

@Composable
private fun ProvisionSuccessContent(ipAddress: String?, onDone: () -> Unit) {
    val openUrl = rememberOpenUrl()
    val defaultUsername = stringResource(Res.string.wifi_provision_success_username_value)
    val defaultPassword = stringResource(Res.string.wifi_provision_success_password_value)
    val resolvedIp = ipAddress ?: stringResource(Res.string.wifi_provision_success_missing_ip)
    val sshCommand =
        ipAddress?.let { stringResource(Res.string.wifi_provision_success_ssh_command, defaultUsername, it) }
            ?: stringResource(Res.string.wifi_provision_success_ssh_unavailable)
    val sshUri = ipAddress?.let { "ssh://$defaultUsername@$it" }

    Column(
        modifier =
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = MeshtasticIcons.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally),
        )
        Text(
            text = stringResource(Res.string.wifi_provision_success_device_connected),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            text = stringResource(Res.string.wifi_provision_success_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            ProvisionInfoItem(
                label = stringResource(Res.string.wifi_provision_success_ip_address),
                value = resolvedIp,
                copyEnabled = ipAddress != null,
            )
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Text(
                    text = stringResource(Res.string.wifi_provision_success_setup_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                )
                Text(
                    text = stringResource(Res.string.wifi_provision_success_setup_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
                ProvisionInfoItem(
                    label = stringResource(Res.string.wifi_provision_success_username),
                    value = defaultUsername,
                )
                ProvisionInfoItem(label = stringResource(Res.string.password), value = defaultPassword)
                ProvisionInfoItem(
                    label = stringResource(Res.string.wifi_provision_success_ssh_label),
                    value = sshCommand,
                    copyEnabled = ipAddress != null,
                )

                FilledTonalButton(
                    onClick = { sshUri?.let(openUrl) },
                    enabled = sshUri != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp),
                ) {
                    Icon(
                        imageVector = MeshtasticIcons.Serial,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.wifi_provision_success_open_ssh))
                }
                Text(
                    text = stringResource(Res.string.wifi_provision_success_open_ssh_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.wifi_provision_success_done))
        }
    }
}

@Composable
private fun ProvisionInfoItem(label: String, value: String, copyEnabled: Boolean = true) {
    ListItem(
        overlineContent = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
        headlineContent = { Text(text = value, style = MaterialTheme.typography.bodyLargeEmphasized) },
        trailingContent = {
            if (copyEnabled) {
                CopyIconButton(valueToCopy = value)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun NetworkRow(network: WifiNetwork, isSelected: Boolean, onClick: () -> Unit) {
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ListItem(
        headlineContent = { Text(network.ssid) },
        supportingContent = { Text(stringResource(Res.string.wifi_provision_signal_strength, network.signalStrength)) },
        leadingContent = {
            Icon(MeshtasticIcons.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            if (network.isProtected) {
                Icon(
                    MeshtasticIcons.Lock,
                    contentDescription = stringResource(Res.string.password),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = containerColor),
        modifier =
        Modifier.clickable(
            onClickLabel = stringResource(Res.string.action_select_network),
            role = Role.Button,
            onClick = onClick,
        ),
    )
}

// ---------------------------------------------------------------------------
// mPWRD-OS disclaimer banner
// ---------------------------------------------------------------------------

private const val MPWRD_LOGO_SIZE_DP = 40

/** Branded disclaimer banner shown at the top of the provisioning screen. */
@Composable
internal fun MpwrdDisclaimerBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Image(
                painter = painterResource(Res.drawable.img_mpwrd_logo),
                contentDescription = stringResource(Res.string.mpwrd_os),
                modifier = Modifier.size(MPWRD_LOGO_SIZE_DP.dp).clip(RoundedCornerShape(8.dp)),
            )
            AutoLinkText(
                text = stringResource(Res.string.wifi_provision_mpwrd_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared layout wrapper for centered status screens
// ---------------------------------------------------------------------------

@Composable
private fun CenteredStatusContent(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@PreviewLightDark
@Composable
private fun ProvisionSuccessContentPreview() {
    AppTheme { Surface { ProvisionSuccessContent(ipAddress = "192.168.1.100", onDone = {}) } }
}
