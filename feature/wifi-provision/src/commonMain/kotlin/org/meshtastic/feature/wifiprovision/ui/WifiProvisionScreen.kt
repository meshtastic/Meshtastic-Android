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
package org.meshtastic.feature.wifiprovision.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth_disabled
import org.meshtastic.core.resources.connected
import org.meshtastic.core.resources.connecting
import org.meshtastic.core.resources.hide_password
import org.meshtastic.core.resources.network
import org.meshtastic.core.resources.show_password
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState
import org.meshtastic.feature.wifiprovision.WifiProvisionViewModel
import org.meshtastic.feature.wifiprovision.model.WifiNetwork

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun WifiProvisionScreen(
    onNavigateUp: () -> Unit,
    address: String? = null,
    viewModel: WifiProvisionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show errors in a snackbar
    LaunchedEffect(uiState.errorMessage) { uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) } }

    // Kick off the BLE scan when the screen first appears; forward any deep-link address
    LaunchedEffect(Unit) { viewModel.connectAndScanNetworks(address) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Provisioning") },
                actions = {
                    if (uiState.phase == WifiProvisionUiState.Phase.Idle && uiState.networks.isNotEmpty()) {
                        IconButton(onClick = viewModel::refreshNetworks) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh networks")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.provisionSuccess -> SuccessContent(onNavigateUp)

                uiState.phase != WifiProvisionUiState.Phase.Idle -> LoadingContent(uiState.phase)

                uiState.selectedNetwork != null ->
                    PasswordContent(
                        network = uiState.selectedNetwork!!,
                        onConfirm = { password -> viewModel.provision(password) },
                        onCancel = { viewModel.reset() },
                    )

                uiState.networks.isNotEmpty() ->
                    NetworkListContent(networks = uiState.networks, onSelect = viewModel::selectNetwork)

                else -> EmptyContent(onRetry = viewModel::refreshNetworks)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun LoadingContent(phase: WifiProvisionUiState.Phase) {
    val label =
        when (phase) {
            WifiProvisionUiState.Phase.ConnectingBle -> stringResource(Res.string.connecting)
            WifiProvisionUiState.Phase.LoadingNetworks -> "Scanning WiFi networks…"
            WifiProvisionUiState.Phase.Provisioning -> "Sending credentials…"
            WifiProvisionUiState.Phase.Idle -> ""
        }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NetworkListContent(networks: List<WifiNetwork>, onSelect: (WifiNetwork) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(Res.string.network),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(networks, key = { it.bssid }) { network ->
                NetworkRow(network = network, onClick = { onSelect(network) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun NetworkRow(network: WifiNetwork, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(network.ssid) },
        supportingContent = { Text("${network.signalStrength}%") },
        leadingContent = { Icon(Icons.Default.Wifi, contentDescription = null) },
        trailingContent = {
            if (network.isProtected) {
                Icon(Icons.Default.Lock, contentDescription = "Password required")
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Suppress("LongMethod")
@Composable
private fun PasswordContent(network: WifiNetwork, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connect to \"${network.ssid}\"", style = MaterialTheme.typography.titleMedium)

        if (network.isProtected) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation =
                if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        val label =
                            if (passwordVisible) {
                                stringResource(Res.string.hide_password)
                            } else {
                                stringResource(Res.string.show_password)
                            }
                        Text(if (passwordVisible) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                        // Accessibility description only; icon handled above
                        @Suppress("UNUSED_VARIABLE")
                        val a11y = label
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onConfirm(password) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.connected))
            }
            Button(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    }
}

@Composable
private fun EmptyContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.bluetooth_disabled), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun SuccessContent(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("WiFi credentials sent!", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("The device will connect to the network shortly.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone) { Text("Done") }
    }
}
