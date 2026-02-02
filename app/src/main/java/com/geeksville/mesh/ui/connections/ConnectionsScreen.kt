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
package com.geeksville.mesh.ui.connections

import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.DeviceListEntry
import com.geeksville.mesh.ui.connections.components.BLEDevices
import com.geeksville.mesh.ui.connections.components.ConnectingDeviceInfo
import com.geeksville.mesh.ui.connections.components.ConnectionsSegmentedBar
import com.geeksville.mesh.ui.connections.components.CurrentlyConnectedInfo
import com.geeksville.mesh.ui.connections.components.EmptyStateContent
import com.geeksville.mesh.ui.connections.components.NetworkDevices
import com.geeksville.mesh.ui.connections.components.UsbDevices
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.connected
import org.meshtastic.core.strings.connected_device
import org.meshtastic.core.strings.connected_sleeping
import org.meshtastic.core.strings.connecting
import org.meshtastic.core.strings.connections
import org.meshtastic.core.strings.must_set_region
import org.meshtastic.core.strings.no_device_selected
import org.meshtastic.core.strings.not_connected
import org.meshtastic.core.strings.set_your_region
import org.meshtastic.core.strings.warning_not_paired
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NoDevice
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.getNavRouteFrom
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.component.PacketResponseStateDialog
import org.meshtastic.proto.Config

fun String?.isValidAddress(): Boolean = if (this.isNullOrBlank()) {
    false
} else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
    @Suppress("DEPRECATION")
    Patterns.IP_ADDRESS.matcher(this).matches() || Patterns.DOMAIN_NAME.matcher(this).matches()
} else {
    InetAddresses.isNumericAddress(this) || Patterns.DOMAIN_NAME.matcher(this).matches()
}

/**
 * Composable screen for managing device connections (BLE, TCP, USB). It handles permission requests for location and
 * displays connection status.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber", "ModifierMissing", "ComposableParamOrder")
@Composable
fun ConnectionsScreen(
    connectionsViewModel: ConnectionsViewModel = hiltViewModel(),
    scanModel: BTScanModel = hiltViewModel(),
    radioConfigViewModel: RadioConfigViewModel = hiltViewModel(),
    onClickNodeChip: (Int) -> Unit,
    onNavigateToNodeDetails: (Int) -> Unit,
    onConfigNavigate: (Route) -> Unit,
) {
    val radioConfigState by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()
    val config by connectionsViewModel.localConfig.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val scanStatusText by scanModel.errorText.observeAsState("")
    val connectionState by connectionsViewModel.connectionState.collectAsStateWithLifecycle()
    val scanning by scanModel.spinner.collectAsStateWithLifecycle(false)
    val ourNode by connectionsViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val selectedDevice by scanModel.selectedNotNullFlow.collectAsStateWithLifecycle()
    val bluetoothState by connectionsViewModel.bluetoothState.collectAsStateWithLifecycle()
    val regionUnset = config.lora?.region == Config.LoRaConfig.RegionCode.UNSET

    val bleDevices by scanModel.bleDevicesForUi.collectAsStateWithLifecycle()
    val discoveredTcpDevices by scanModel.discoveredTcpDevicesForUi.collectAsStateWithLifecycle()
    val recentTcpDevices by scanModel.recentTcpDevicesForUi.collectAsStateWithLifecycle()
    val usbDevices by scanModel.usbDevicesForUi.collectAsStateWithLifecycle()

    /* Animate waiting for the configurations */
    var isWaiting by remember { mutableStateOf(false) }
    if (isWaiting) {
        PacketResponseStateDialog(
            state = radioConfigState.responseState,
            onDismiss = {
                isWaiting = false
                radioConfigViewModel.clearPacketResponse()
            },
            onComplete = {
                getNavRouteFrom(radioConfigState.route)?.let { route ->
                    isWaiting = false
                    radioConfigViewModel.clearPacketResponse()
                    if (route == SettingsRoutes.LoRa) {
                        onConfigNavigate(SettingsRoutes.LoRa)
                    }
                }
            },
        )
    }

    // when scanning is true - wait 10000ms and then stop scanning
    LaunchedEffect(scanning) {
        if (scanning) {
            delay(SCAN_PERIOD)
            scanModel.stopScan()
        }
    }

    LaunchedEffect(connectionState, regionUnset) {
        when (connectionState) {
            ConnectionState.Connected -> {
                if (regionUnset) Res.string.must_set_region else Res.string.connected
            }

            ConnectionState.Connecting -> Res.string.connecting

            ConnectionState.Disconnected -> Res.string.not_connected
            ConnectionState.DeviceSleep -> Res.string.connected_sleeping
        }.let { scanModel.setErrorText(getString(it)) }
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.connections),
                ourNode = ourNode,
                showNodeChip = ourNode != null && connectionState.isConnected(),
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = { onClickNodeChip(it.num) },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(scrollState)
                    .height(IntrinsicSize.Max)
                    .padding(paddingValues)
                    .padding(16.dp),
            ) {
                val uiState =
                    when {
                        connectionState.isConnected() && ourNode != null -> 2
                        connectionState == ConnectionState.Connecting ||
                            (connectionState == ConnectionState.Disconnected && selectedDevice != "n") -> 1

                        else -> 0
                    }

                Crossfade(
                    targetState = uiState,
                    label = "connection_state",
                    modifier = Modifier.padding(bottom = 16.dp),
                ) { state ->
                    when (state) {
                        2 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ourNode?.let { node ->
                                    TitledCard(title = stringResource(Res.string.connected_device)) {
                                        CurrentlyConnectedInfo(
                                            node = node,
                                            bleDevice =
                                            bleDevices.find { it.fullAddress == selectedDevice }
                                                as DeviceListEntry.Ble?,
                                            onNavigateToNodeDetails = onNavigateToNodeDetails,
                                            onClickDisconnect = { scanModel.disconnect() },
                                        )
                                    }
                                }

                                if (regionUnset && selectedDevice != "m") {
                                    TitledCard(title = null) {
                                        ListItem(
                                            leadingIcon = Icons.Rounded.Language,
                                            text = stringResource(Res.string.set_your_region),
                                        ) {
                                            isWaiting = true
                                            radioConfigViewModel.setResponseStateLoading(ConfigRoute.LORA)
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            val selectedEntry =
                                bleDevices.find { it.fullAddress == selectedDevice }
                                    ?: discoveredTcpDevices.find { it.fullAddress == selectedDevice }
                                    ?: recentTcpDevices.find { it.fullAddress == selectedDevice }
                                    ?: usbDevices.find { it.fullAddress == selectedDevice }

                            val name = selectedEntry?.name ?: "Unknown Device"
                            val address = selectedEntry?.address ?: selectedDevice

                            TitledCard(title = stringResource(Res.string.connected_device)) {
                                ConnectingDeviceInfo(
                                    deviceName = name,
                                    deviceAddress = address,
                                    onClickDisconnect = { scanModel.disconnect() },
                                )
                            }
                        }

                        else -> {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                EmptyStateContent(
                                    imageVector = MeshtasticIcons.NoDevice,
                                    text = stringResource(Res.string.no_device_selected),
                                    modifier = Modifier.height(160.dp),
                                )
                            }
                        }
                    }
                }

                var selectedDeviceType by remember { mutableStateOf(DeviceType.BLE) }
                LaunchedEffect(Unit) { DeviceType.fromAddress(selectedDevice)?.let { selectedDeviceType = it } }

                ConnectionsSegmentedBar(selectedDeviceType = selectedDeviceType, modifier = Modifier.fillMaxWidth()) {
                    selectedDeviceType = it
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column(modifier = Modifier.fillMaxSize()) {
                    when (selectedDeviceType) {
                        DeviceType.BLE -> {
                            val (bonded, available) = bleDevices.partition { it.bonded }
                            BLEDevices(
                                connectionState = connectionState,
                                bondedDevices = bonded,
                                availableDevices = available,
                                selectedDevice = selectedDevice,
                                scanModel = scanModel,
                                bluetoothEnabled = bluetoothState.enabled,
                            )
                        }

                        DeviceType.TCP -> {
                            NetworkDevices(
                                connectionState = connectionState,
                                discoveredNetworkDevices = discoveredTcpDevices,
                                recentNetworkDevices = recentTcpDevices,
                                selectedDevice = selectedDevice,
                                scanModel = scanModel,
                            )
                        }

                        DeviceType.USB -> {
                            UsbDevices(
                                connectionState = connectionState,
                                usbDevices = usbDevices,
                                selectedDevice = selectedDevice,
                                scanModel = scanModel,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Warning Not Paired
                    val hasShownNotPairedWarning by
                        connectionsViewModel.hasShownNotPairedWarning.collectAsStateWithLifecycle()
                    val (bonded, _) = bleDevices.partition { it.bonded }
                    val showWarningNotPaired =
                        !connectionState.isConnected() && !hasShownNotPairedWarning && bonded.isEmpty()
                    if (showWarningNotPaired) {
                        Text(
                            text = stringResource(Res.string.warning_not_paired),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        LaunchedEffect(Unit) { connectionsViewModel.suppressNoPairedWarning() }
                    }
                }
            }
            scanStatusText?.let {
                Card(
                    modifier = Modifier.padding(8.dp).align(Alignment.BottomStart),
                    colors =
                    CardDefaults.cardColors()
                        .copy(containerColor = CardDefaults.cardColors().containerColor.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

private const val SCAN_PERIOD: Long = 10000 // 10 seconds
