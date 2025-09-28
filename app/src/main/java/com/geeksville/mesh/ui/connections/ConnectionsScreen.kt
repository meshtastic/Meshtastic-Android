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

package com.geeksville.mesh.ui.connections

import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.DeviceListEntry
import com.geeksville.mesh.navigation.ConfigRoute
import com.geeksville.mesh.navigation.getNavRouteFrom
import com.geeksville.mesh.service.ConnectionState
import com.geeksville.mesh.ui.common.components.MainAppBar
import com.geeksville.mesh.ui.connections.components.BLEDevices
import com.geeksville.mesh.ui.connections.components.ConnectionsSegmentedBar
import com.geeksville.mesh.ui.connections.components.CurrentlyConnectedInfo
import com.geeksville.mesh.ui.connections.components.NetworkDevices
import com.geeksville.mesh.ui.connections.components.UsbDevices
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import com.geeksville.mesh.ui.settings.components.SettingsItem
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import com.geeksville.mesh.ui.settings.radio.components.PacketResponseStateDialog
import com.geeksville.mesh.ui.sharing.SharedContactDialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.delay
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.TitledCard

fun String?.isIPAddress(): Boolean = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
    @Suppress("DEPRECATION")
    this != null && Patterns.IP_ADDRESS.matcher(this).matches()
} else {
    InetAddresses.isNumericAddress(this.toString())
}

/**
 * Composable screen for managing device connections (BLE, TCP, USB). It handles permission requests for location and
 * displays connection status.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber", "ModifierMissing", "ComposableParamOrder")
@Composable
fun ConnectionsScreen(
    connectionsViewModel: ConnectionsViewModel = hiltViewModel(),
    scanModel: BTScanModel = hiltViewModel(),
    radioConfigViewModel: RadioConfigViewModel = hiltViewModel(),
    onClickNodeChip: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToNodeDetails: (Int) -> Unit,
    onConfigNavigate: (Route) -> Unit,
) {
    val radioConfigState by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()
    val config by connectionsViewModel.localConfig.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val scanStatusText by scanModel.errorText.observeAsState("")
    val connectionState by
        connectionsViewModel.connectionState.collectAsStateWithLifecycle(ConnectionState.DISCONNECTED)
    val scanning by scanModel.spinner.collectAsStateWithLifecycle(false)
    val context = LocalContext.current
    val info by connectionsViewModel.myNodeInfo.collectAsStateWithLifecycle()
    val ourNode by connectionsViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val selectedDevice by scanModel.selectedNotNullFlow.collectAsStateWithLifecycle()
    val bluetoothState by connectionsViewModel.bluetoothState.collectAsStateWithLifecycle()
    val regionUnset = config.lora.region == ConfigProtos.Config.LoRaConfig.RegionCode.UNSET
    val bluetoothRssi by connectionsViewModel.bluetoothRssi.collectAsStateWithLifecycle()

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

    // State for the device scan dialog
    var showScanDialog by remember { mutableStateOf(false) }
    val scanResults by scanModel.scanResult.observeAsState(emptyMap())

    // Observe scan results to show the dialog
    if (scanResults.isNotEmpty()) {
        showScanDialog = true
    }

    LaunchedEffect(connectionState, regionUnset) {
        when (connectionState) {
            ConnectionState.CONNECTED -> {
                if (regionUnset) R.string.must_set_region else R.string.connected_to
            }

            ConnectionState.DISCONNECTED -> R.string.not_connected
            ConnectionState.DEVICE_SLEEP -> R.string.connected_sleeping
        }.let {
            val firmwareString = info?.firmwareString ?: context.getString(R.string.unknown)
            scanModel.setErrorText(context.getString(it, firmwareString))
        }
    }
    var showSharedContact by remember { mutableStateOf<Node?>(null) }
    if (showSharedContact != null) {
        SharedContactDialog(contact = showSharedContact, onDismiss = { showSharedContact = null })
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(R.string.connections),
                ourNode = ourNode,
                isConnected = connectionState.isConnected(),
                showNodeChip = ourNode != null && connectionState.isConnected(),
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onAction = { action ->
                    when (action) {
                        is NodeMenuAction.MoreDetails -> onClickNodeChip(action.node.num)
                        else -> {}
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                Column(
                    modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(scrollState)
                        .height(IntrinsicSize.Max)
                        .padding(paddingValues)
                        .padding(16.dp),
                ) {
                    AnimatedVisibility(
                        visible = connectionState.isConnected(),
                        modifier = Modifier.padding(bottom = 16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ourNode?.let { node ->
                                TitledCard(title = stringResource(R.string.connected_device)) {
                                    CurrentlyConnectedInfo(
                                        node = node,
                                        onNavigateToNodeDetails = onNavigateToNodeDetails,
                                        onSetShowSharedContact = { showSharedContact = it },
                                        onNavigateToSettings = onNavigateToSettings,
                                        onClickDisconnect = { scanModel.disconnect() },
                                        bluetoothRssi = bluetoothRssi,
                                    )
                                }
                            }

                            if (regionUnset && selectedDevice != "m") {
                                TitledCard(title = null) {
                                    SettingsItem(
                                        leadingIcon = Icons.Rounded.Language,
                                        text = stringResource(id = R.string.set_your_region),
                                    ) {
                                        isWaiting = true
                                        radioConfigViewModel.setResponseStateLoading(ConfigRoute.LORA)
                                    }
                                }
                            }
                        }
                    }

                    var selectedDeviceType by remember { mutableStateOf(DeviceType.BLE) }
                    LaunchedEffect(Unit) { DeviceType.fromAddress(selectedDevice)?.let { selectedDeviceType = it } }

                    ConnectionsSegmentedBar(
                        selectedDeviceType = selectedDeviceType,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        selectedDeviceType = it
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Column(modifier = Modifier.fillMaxSize()) {
                        when (selectedDeviceType) {
                            DeviceType.BLE -> {
                                BLEDevices(
                                    connectionState = connectionState,
                                    btDevices = bleDevices,
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
                        val showWarningNotPaired =
                            !connectionState.isConnected() &&
                                !hasShownNotPairedWarning &&
                                bleDevices.none { it is DeviceListEntry.Ble && it.bonded }
                        if (showWarningNotPaired) {
                            Text(
                                text = stringResource(R.string.warning_not_paired),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            LaunchedEffect(Unit) { connectionsViewModel.suppressNoPairedWarning() }
                        }
                    }
                }

                // Compose Device Scan Dialog
                if (showScanDialog) {
                    Dialog(
                        onDismissRequest = {
                            showScanDialog = false
                            scanModel.clearScanResults()
                        },
                    ) {
                        Surface(shape = MaterialTheme.shapes.medium) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Select a Bluetooth device",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )
                                Column(modifier = Modifier.selectableGroup()) {
                                    scanResults.values.forEach { device ->
                                        Row(
                                            modifier =
                                            Modifier.fillMaxWidth()
                                                .selectable(
                                                    selected = false, // No pre-selection in this dialog
                                                    onClick = {
                                                        scanModel.onSelected(device)
                                                        scanModel.clearScanResults()
                                                        showScanDialog = false
                                                    },
                                                )
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(text = device.name)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    onClick = {
                                        scanModel.clearScanResults()
                                        showScanDialog = false
                                    },
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text(
                    text = scanStatusText.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 10.sp,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private const val SCAN_PERIOD: Long = 10000 // 10 seconds
