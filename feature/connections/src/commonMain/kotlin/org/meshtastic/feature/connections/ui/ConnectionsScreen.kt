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
package org.meshtastic.feature.connections.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connected_device
import org.meshtastic.core.resources.connected_sleeping
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.connections
import org.meshtastic.core.resources.must_set_region
import org.meshtastic.core.resources.no_device_selected
import org.meshtastic.core.resources.not_connected
import org.meshtastic.core.resources.set_your_region
import org.meshtastic.core.resources.unknown_device
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NoDevice
import org.meshtastic.core.ui.viewmodel.ConnectionsViewModel
import org.meshtastic.feature.connections.NO_DEVICE_SELECTED
import org.meshtastic.feature.connections.ScannerViewModel
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.ui.components.BLEDevices
import org.meshtastic.feature.connections.ui.components.ConnectingDeviceInfo
import org.meshtastic.feature.connections.ui.components.ConnectionsSegmentedBar
import org.meshtastic.feature.connections.ui.components.CurrentlyConnectedInfo
import org.meshtastic.feature.connections.ui.components.EmptyStateContent
import org.meshtastic.feature.connections.ui.components.NetworkDevices
import org.meshtastic.feature.connections.ui.components.UsbDevices
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.getNavRouteFrom
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.component.PacketResponseStateDialog
import kotlin.uuid.ExperimentalUuidApi

/** Composable screen for managing device connections (BLE, TCP, USB). It displays connection status. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber", "ModifierMissing", "ComposableParamOrder")
@Composable
fun ConnectionsScreen(
    connectionsViewModel: ConnectionsViewModel = koinViewModel(),
    scanModel: ScannerViewModel = koinViewModel(),
    radioConfigViewModel: RadioConfigViewModel = koinViewModel(),
    onClickNodeChip: (Int) -> Unit,
    onNavigateToNodeDetails: (Int) -> Unit,
    onConfigNavigate: (Route) -> Unit,
) {
    val radioConfigState by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()
    val scanStatusText by scanModel.errorText.collectAsStateWithLifecycle()
    val connectionState by connectionsViewModel.connectionState.collectAsStateWithLifecycle()
    val ourNode by connectionsViewModel.ourNodeForDisplay.collectAsStateWithLifecycle()
    val regionUnset by connectionsViewModel.regionUnset.collectAsStateWithLifecycle()

    val selectedDevice by scanModel.selectedNotNullFlow.collectAsStateWithLifecycle()
    val persistedDeviceName by scanModel.persistedDeviceName.collectAsStateWithLifecycle()

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
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                val uiState =
                    when {
                        connectionState.isConnected() && ourNode != null -> 2
                        connectionState.isConnected() ||
                            connectionState == ConnectionState.Connecting ||
                            selectedDevice != NO_DEVICE_SELECTED -> 1

                        else -> 0
                    }

                Crossfade(targetState = uiState, label = "connection_state") { state ->
                    when (state) {
                        2 ->
                            ConnectedDeviceContent(
                                ourNode = ourNode,
                                regionUnset = regionUnset,
                                selectedDevice = selectedDevice,
                                bleDevices = bleDevices,
                                onNavigateToNodeDetails = onNavigateToNodeDetails,
                                onClickDisconnect = { scanModel.disconnect() },
                                onSetRegion = {
                                    isWaiting = true
                                    radioConfigViewModel.setResponseStateLoading(ConfigRoute.LORA)
                                },
                            )

                        1 ->
                            ConnectingDeviceContent(
                                connectionState = connectionState,
                                selectedDevice = selectedDevice,
                                persistedDeviceName = persistedDeviceName,
                                bleDevices = bleDevices,
                                discoveredTcpDevices = discoveredTcpDevices,
                                recentTcpDevices = recentTcpDevices,
                                usbDevices = usbDevices,
                                onClickDisconnect = { scanModel.disconnect() },
                            )

                        else -> NoDeviceContent()
                    }
                }

                var selectedDeviceType by remember { mutableStateOf(DeviceType.BLE) }
                LaunchedEffect(Unit) { DeviceType.fromAddress(selectedDevice)?.let { selectedDeviceType = it } }

                val supportedDeviceTypes = scanModel.supportedDeviceTypes

                // Fallback to a supported type if the current one isn't
                LaunchedEffect(supportedDeviceTypes) {
                    if (selectedDeviceType !in supportedDeviceTypes && supportedDeviceTypes.isNotEmpty()) {
                        selectedDeviceType = supportedDeviceTypes.first()
                    }
                }

                ConnectionsSegmentedBar(
                    selectedDeviceType = selectedDeviceType,
                    supportedDeviceTypes = supportedDeviceTypes,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    selectedDeviceType = it
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedDeviceType) {
                        DeviceType.BLE -> {
                            BLEDevices(
                                connectionState = connectionState,
                                selectedDevice = selectedDevice,
                                scanModel = scanModel,
                            )
                        }

                        DeviceType.TCP -> {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                NetworkDevices(
                                    connectionState = connectionState,
                                    discoveredNetworkDevices = discoveredTcpDevices,
                                    recentNetworkDevices = recentTcpDevices,
                                    selectedDevice = selectedDevice,
                                    scanModel = scanModel,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        DeviceType.USB -> {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                UsbDevices(
                                    connectionState = connectionState,
                                    usbDevices = usbDevices,
                                    selectedDevice = selectedDevice,
                                    scanModel = scanModel,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
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

/** Content shown when connected to a device with node info available. */
@Composable
private fun ConnectedDeviceContent(
    ourNode: org.meshtastic.core.model.Node?,
    regionUnset: Boolean,
    selectedDevice: String,
    bleDevices: List<DeviceListEntry>,
    onNavigateToNodeDetails: (Int) -> Unit,
    onClickDisconnect: () -> Unit,
    onSetRegion: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ourNode?.let { node ->
            TitledCard(title = stringResource(Res.string.connected_device)) {
                CurrentlyConnectedInfo(
                    node = node,
                    bleDevice = bleDevices.find { it.fullAddress == selectedDevice } as DeviceListEntry.Ble?,
                    onNavigateToNodeDetails = onNavigateToNodeDetails,
                    onClickDisconnect = onClickDisconnect,
                )
            }
        }

        if (regionUnset && selectedDevice != "m") {
            TitledCard(title = null) {
                ListItem(
                    leadingIcon = Icons.Rounded.Language,
                    text = stringResource(Res.string.set_your_region),
                    onClick = onSetRegion,
                )
            }
        }
    }
}

/** Content shown when connecting or a device is selected but node info is not yet available. */
@Composable
private fun ConnectingDeviceContent(
    connectionState: ConnectionState,
    selectedDevice: String,
    persistedDeviceName: String?,
    bleDevices: List<DeviceListEntry>,
    discoveredTcpDevices: List<DeviceListEntry>,
    recentTcpDevices: List<DeviceListEntry>,
    usbDevices: List<DeviceListEntry>,
    onClickDisconnect: () -> Unit,
) {
    val selectedEntry =
        bleDevices.find { it.fullAddress == selectedDevice }
            ?: discoveredTcpDevices.find { it.fullAddress == selectedDevice }
            ?: recentTcpDevices.find { it.fullAddress == selectedDevice }
            ?: usbDevices.find { it.fullAddress == selectedDevice }

    // Use the entry name if found in scan lists, otherwise fall back to the persisted name
    // from the last successful selection, and only show "Unknown Device" as a last resort.
    val name = selectedEntry?.name ?: persistedDeviceName ?: stringResource(Res.string.unknown_device)
    val address = selectedEntry?.address ?: selectedDevice

    TitledCard(title = stringResource(Res.string.connected_device)) {
        ConnectingDeviceInfo(
            connectionState = connectionState,
            deviceName = name,
            deviceAddress = address,
            onClickDisconnect = onClickDisconnect,
        )
    }
}

/** Content shown when no device is selected. */
@Composable
private fun NoDeviceContent() {
    Card(modifier = Modifier.fillMaxWidth()) {
        EmptyStateContent(
            imageVector = MeshtasticIcons.NoDevice,
            text = stringResource(Res.string.no_device_selected),
            modifier = Modifier.height(160.dp),
        )
    }
}
