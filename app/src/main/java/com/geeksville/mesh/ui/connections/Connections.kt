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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.info
import com.geeksville.mesh.android.BuildUtils.reportError
import com.geeksville.mesh.android.BuildUtils.warn
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.getBluetoothPermissions
import com.geeksville.mesh.android.getLocationPermissions
import com.geeksville.mesh.android.gpsDisabled
import com.geeksville.mesh.android.hasLocationPermission
import com.geeksville.mesh.android.isGooglePlayAvailable
import com.geeksville.mesh.android.permissionMissing
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.NO_DEVICE_SELECTED
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.ConfigRoute
import com.geeksville.mesh.navigation.RadioConfigRoutes
import com.geeksville.mesh.navigation.Route
import com.geeksville.mesh.navigation.getNavRouteFrom
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.ui.node.NodeActionButton
import com.geeksville.mesh.ui.node.components.NodeChip
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel
import com.geeksville.mesh.ui.radioconfig.components.PacketResponseStateDialog
import com.geeksville.mesh.ui.sharing.SharedContactDialog
import kotlinx.coroutines.delay

fun String?.isIPAddress(): Boolean {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        @Suppress("DEPRECATION")
        this != null && Patterns.IP_ADDRESS.matcher(this).matches()
    } else {
        InetAddresses.isNumericAddress(this.toString())
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber")
@Composable
fun ConnectionsScreen(
    uiViewModel: UIViewModel = hiltViewModel(),
    scanModel: BTScanModel = hiltViewModel(),
    bluetoothViewModel: BluetoothViewModel = hiltViewModel(),
    radioConfigViewModel: RadioConfigViewModel = hiltViewModel(),
    onNavigateToRadioConfig: () -> Unit,
    onNavigateToNodeDetails: (Int) -> Unit,
    onConfigNavigate: (Route) -> Unit
) {
    val radioConfigState by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()
    val config by uiViewModel.localConfig.collectAsState()
    val currentRegion = config.lora.region
    val scrollState = rememberScrollState()
    val scanStatusText by scanModel.errorText.observeAsState("")
    val connectionState by uiViewModel.connectionState.collectAsState(MeshService.ConnectionState.DISCONNECTED)
    val devices by scanModel.devices.observeAsState(emptyMap())
    val scanning by scanModel.spinner.observeAsState(false)
    val receivingLocationUpdates by uiViewModel.receivingLocationUpdates.collectAsState(false)
    val context = LocalContext.current
    val app = (context.applicationContext as GeeksvilleApplication)
    val info by uiViewModel.myNodeInfo.collectAsState()
    val selectedDevice = scanModel.selectedNotNull
    val bluetoothEnabled by bluetoothViewModel.enabled.observeAsState()
    val regionUnset = currentRegion == ConfigProtos.Config.LoRaConfig.RegionCode.UNSET &&
            connectionState == MeshService.ConnectionState.CONNECTED

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
                    if (route == RadioConfigRoutes.LoRa) {
                        onConfigNavigate(RadioConfigRoutes.LoRa)
                    }
                }
            },
        )
    }

    val isGpsDisabled = context.gpsDisabled()
    LaunchedEffect(isGpsDisabled) {
        if (isGpsDisabled) {
            uiViewModel.showSnackbar(context.getString(R.string.location_disabled))
        }
    }
    LaunchedEffect(bluetoothEnabled) {
        if (bluetoothEnabled == false) {
            uiViewModel.showSnackbar(context.getString(R.string.bluetooth_disabled))
        }
    }
    // when scanning is true - wait 10000ms and then stop scanning
    LaunchedEffect(scanning) {
        if (scanning) {
            delay(SCAN_PERIOD)
            scanModel.stopScan()
        }
    }

    // State for manual IP address input
    var manualIpAddress by remember { mutableStateOf("") }
    var manualIpPort by remember { mutableStateOf(NetworkRepository.SERVICE_PORT.toString()) }

    // State for the device scan dialog
    var showScanDialog by remember { mutableStateOf(false) }
    val scanResults by scanModel.scanResult.observeAsState(emptyMap())

    // State for the location permission rationale dialog
    var showLocationRationaleDialog by remember { mutableStateOf(false) }

    // State for the Bluetooth permission rationale dialog
    var showBluetoothRationaleDialog by remember { mutableStateOf(false) }

    // State for the Report Bug dialog
    var showReportBugDialog by remember { mutableStateOf(false) }

    // Remember the permission launchers
    val requestLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.entries.all { it.value }) {
                uiViewModel.setProvideLocation(true)
                uiViewModel.meshService?.startProvideLocation()
            } else {
                debug("User denied location permission")
                uiViewModel.showSnackbar(context.getString(R.string.why_background_required))
            }
            bluetoothViewModel.permissionsUpdated()
        }
    )

    val requestBluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.entries.all { it.value }) {
                info("Bluetooth permissions granted")
                // We need to call the scan function which is in the Fragment
                // Since we can't directly call scanLeDevice() from Composable,
                // we might need to rethink how scanning is triggered or
                // pass the scan trigger as a lambda.
                // For now, let's assume we trigger the scan outside the Composable
                // after permissions are granted. We can add a callback to the ViewModel.
                scanModel.startScan()
            } else {
                warn("Bluetooth permissions denied")
                uiViewModel.showSnackbar(context.permissionMissing)
            }
            bluetoothViewModel.permissionsUpdated()
        }
    )

    // Observe scan results to show the dialog
    if (scanResults.isNotEmpty()) {
        showScanDialog = true
    }

    LaunchedEffect(connectionState, regionUnset) {
        when (connectionState) {
            MeshService.ConnectionState.CONNECTED -> {
                if (regionUnset) R.string.must_set_region else R.string.connected_to
            }

            MeshService.ConnectionState.DISCONNECTED -> R.string.not_connected
            MeshService.ConnectionState.DEVICE_SLEEP -> R.string.connected_sleeping
        }.let {
            val firmwareString =
                info?.firmwareString ?: context.getString(R.string.unknown)
            scanModel.setErrorText(context.getString(it, firmwareString))
        }
    }
    var showSharedContact by remember { mutableStateOf<Node?>(null) }
    if (showSharedContact != null) {
        SharedContactDialog(
            contact = showSharedContact,
            onDismiss = { showSharedContact = null }
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Scan Status Text
            Text(
                text = scanStatusText.orEmpty(),
                fontSize = 14.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            val isConnected by uiViewModel.isConnected.collectAsState(false)
            val ourNode by uiViewModel.ourNodeInfo.collectAsState()
            if (isConnected) {
                ourNode?.let { node ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NodeChip(
                            node = node,
                            isThisNode = true,
                            isConnected = true,
                            onAction = { action ->
                                when (action) {
                                    is NodeMenuAction.MoreDetails -> {
                                        onNavigateToNodeDetails(node.num)
                                    }

                                    is NodeMenuAction.Share -> {
                                        showSharedContact = node
                                    }

                                    else -> {}
                                }
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = node.user.longName,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                NodeActionButton(
                    title = stringResource(id = R.string.radio_configuration),
                    icon = Icons.Default.Settings,
                    enabled = true,
                    onClick = onNavigateToRadioConfig
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (regionUnset && selectedDevice != "m") {
                    NodeActionButton(
                        title = stringResource(id = R.string.set_your_region),
                        icon = ConfigRoute.LORA.icon,
                        enabled = true,
                        onClick = {
                            isWaiting = true
                            radioConfigViewModel.setResponseStateLoading(ConfigRoute.LORA)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Device List and Manual Input
            Text(
                text = stringResource(R.string.device),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Progress bar while scanning
            if (scanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(modifier = Modifier.selectableGroup()) {
                devices.values.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (device.fullAddress == selectedDevice) ||
                                        device.fullAddress == NO_DEVICE_SELECTED,
                                onClick = {
                                    if (!device.bonded) {
                                        uiViewModel.showSnackbar(context.getString(R.string.starting_pairing))
                                    }
                                    scanModel.onSelected(device)
                                },
                                role = Role.RadioButton,
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (device.fullAddress == selectedDevice),
                            onClick = null
                        )
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }

                // Manual IP Address Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = ("t$manualIpAddress:$manualIpPort" == selectedDevice),
                            onClick = {
                                if (manualIpAddress.isIPAddress()) {
                                    scanModel.onSelected(
                                        BTScanModel.DeviceListEntry(
                                            "",
                                            "t$manualIpAddress:$manualIpPort",
                                            true
                                        )
                                    )
                                }
                            },
                            enabled = manualIpAddress.isIPAddress(),
                            role = Role.RadioButton
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = ("t$manualIpAddress:$manualIpPort" == selectedDevice),
                        onClick = null,
                        enabled = manualIpAddress.isIPAddress()
                    )
                    OutlinedTextField(
                        value = manualIpAddress,
                        onValueChange = { manualIpAddress = it },
                        label = { Text(stringResource(R.string.ip_address)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions {
                            if (manualIpAddress.isIPAddress()) {
                                scanModel.onSelected(
                                    BTScanModel.DeviceListEntry(
                                        "",
                                        "t$manualIpAddress:$manualIpPort",
                                        true
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(0.7f)
                            .padding(start = 16.dp)
                    )
                    OutlinedTextField(
                        value = manualIpPort,
                        onValueChange = {
                            // Only allow numeric input for port
                            if (it.all { char -> char.isDigit() }) {
                                manualIpPort = it
                            }
                        },
                        label = { Text(stringResource(R.string.ip_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions {
                            if (manualIpAddress.isIPAddress()) {
                                scanModel.onSelected(
                                    BTScanModel.DeviceListEntry(
                                        "",
                                        "t$manualIpAddress:$manualIpPort",
                                        true
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(weight = 0.3f)
                            .padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LaunchedEffect(ourNode) {
                if (ourNode != null) {
                    uiViewModel.refreshProvideLocation()
                }
            }
            AnimatedVisibility(isConnected) {
                val provideLocation by uiViewModel.provideLocation.collectAsState(false)
                LaunchedEffect(provideLocation) {
                    if (provideLocation) {
                        if (!context.hasLocationPermission()) {
                            debug("Requesting location permission for providing location")
                            showLocationRationaleDialog = true
                        } else if (isGpsDisabled) {
                            uiViewModel.showSnackbar(context.getString(R.string.location_disabled))
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = provideLocation,
                            onValueChange = { checked ->
                                uiViewModel.setProvideLocation(checked)
                            },
                            enabled = !isGpsDisabled
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = receivingLocationUpdates,
                        onCheckedChange = null,
                        enabled = !isGpsDisabled // Disable if GPS is disabled
                    )
                    Text(
                        text = stringResource(R.string.provide_location_to_mesh),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            // Provide Location Checkbox

            Spacer(modifier = Modifier.height(16.dp))

            // Warning Not Paired
            val showWarningNotPaired = !devices.any { it.value.bonded }
            if (showWarningNotPaired) {
                Text(
                    text = stringResource(R.string.warning_not_paired),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Analytics Okay Checkbox

            val isGooglePlayAvailable = app.isGooglePlayAvailable()
            val isAnalyticsAllowed = app.isAnalyticsAllowed && isGooglePlayAvailable
            if (isGooglePlayAvailable) {
                var loading by remember { mutableStateOf(false) }
                LaunchedEffect(isAnalyticsAllowed) {
                    loading = false
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = isAnalyticsAllowed,
                            onValueChange = {
                                debug("User changed analytics to $it")
                                app.isAnalyticsAllowed = it
                                loading = true
                            },
                            role = Role.Checkbox,
                            enabled = isGooglePlayAvailable && !loading
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        enabled = isGooglePlayAvailable,
                        checked = isAnalyticsAllowed,
                        onCheckedChange = null
                    )
                    Text(
                        text = stringResource(R.string.analytics_okay),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Report Bug Button
                Button(
                    onClick = { showReportBugDialog = true }, // Set state to show Report Bug dialog
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    enabled = isAnalyticsAllowed
                ) {
                    Text(stringResource(R.string.report_bug))
                }
            }
        }
        // Floating Action Button (Change Radio)
        FloatingActionButton(
            onClick = {
                val bluetoothPermissions = context.getBluetoothPermissions()
                if (bluetoothPermissions.isEmpty()) {
                    // If no permissions needed, trigger the scan directly (or via ViewModel)
                    scanModel.startScan()
                } else {
                    if (
                        context.findActivity()
                            .shouldShowRequestPermissionRationale(bluetoothPermissions.first())
                    ) {
                        showBluetoothRationaleDialog = true
                    } else {
                        requestBluetoothPermissionLauncher.launch(bluetoothPermissions)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.change_radio))
        }
    }

// Compose Device Scan Dialog
    if (showScanDialog) {
        Dialog(onDismissRequest = {
            showScanDialog = false
            scanModel.clearScanResults()
        }) {
            Surface(shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Select a Bluetooth device",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Column(modifier = Modifier.selectableGroup()) {
                        scanResults.values.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = false, // No pre-selection in this dialog
                                        onClick = {
                                            scanModel.onSelected(device)
                                            scanModel.clearScanResults()
                                            showScanDialog = false
                                        }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = device.name)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = {
                        scanModel.clearScanResults()
                        showScanDialog = false
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

// Compose Location Permission Rationale Dialog
    if (showLocationRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showLocationRationaleDialog = false },
            title = { Text(stringResource(R.string.background_required)) },
            text = { Text(stringResource(R.string.why_background_required)) },
            confirmButton = {
                Button(onClick = {
                    showLocationRationaleDialog = false
                    if (!context.hasLocationPermission()) {
                        requestLocationPermissionLauncher.launch(context.getLocationPermissions())
                    }
                }) {
                    Text(stringResource(R.string.accept))
                }
            },
            dismissButton = {
                Button(onClick = { showLocationRationaleDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

// Compose Bluetooth Permission Rationale Dialog
    if (showBluetoothRationaleDialog) {
        val bluetoothPermissions = context.getBluetoothPermissions()
        AlertDialog(
            onDismissRequest = { showBluetoothRationaleDialog = false },
            title = { Text(stringResource(R.string.required_permissions)) },
            text = { Text(stringResource(R.string.permission_missing_31)) },
            confirmButton = {
                Button(onClick = {
                    showBluetoothRationaleDialog = false
                    if (bluetoothPermissions.isNotEmpty()) {
                        requestBluetoothPermissionLauncher.launch(bluetoothPermissions)
                    } else {
                        // If somehow no permissions are required, just scan
                        scanModel.startScan()
                    }
                }) {
                    Text(stringResource(R.string.okay))
                }
            },
            dismissButton = {
                Button(onClick = { showBluetoothRationaleDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

// Compose Report Bug Dialog
    if (showReportBugDialog) {
        AlertDialog(
            onDismissRequest = { showReportBugDialog = false },
            title = { Text(stringResource(R.string.report_a_bug)) },
            text = { Text(stringResource(R.string.report_bug_text)) },
            confirmButton = {
                Button(onClick = {
                    showReportBugDialog = false
                    reportError("Clicked Report A Bug")
                    uiViewModel.showSnackbar("Bug report sent!")
                }) {
                    Text(stringResource(R.string.report))
                }
            },
            dismissButton = {
                Button(onClick = {
                    showReportBugDialog = false
                    debug("Decided not to report a bug")
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No activity found")
}

private const val SCAN_PERIOD: Long = 10000 // 10 seconds
