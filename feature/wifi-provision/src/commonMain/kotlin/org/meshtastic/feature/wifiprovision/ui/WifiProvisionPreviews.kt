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
@file:Suppress("TooManyFunctions", "MagicNumber")

package org.meshtastic.feature.wifiprovision.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.wifiprovision.WifiProvisionError
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.Phase
import org.meshtastic.feature.wifiprovision.WifiProvisionUiState.ProvisionStatus
import org.meshtastic.feature.wifiprovision.model.DiscoveredDevice
import org.meshtastic.feature.wifiprovision.model.WifiNetwork

// ---------------------------------------------------------------------------
// Sample data for previews
// ---------------------------------------------------------------------------

private val sampleNetworks =
    listOf(
        WifiNetwork(ssid = "Meshtastic-HQ", bssid = "AA:BB:CC:DD:EE:01", signalStrength = 92, isProtected = true),
        WifiNetwork(ssid = "CoffeeShop-Free", bssid = "AA:BB:CC:DD:EE:02", signalStrength = 74, isProtected = false),
        WifiNetwork(ssid = "OffGrid-5G", bssid = "AA:BB:CC:DD:EE:03", signalStrength = 58, isProtected = true),
        WifiNetwork(ssid = "Neighbor-Net", bssid = "AA:BB:CC:DD:EE:04", signalStrength = 31, isProtected = true),
    )

private val edgeCaseNetworks =
    listOf(
        WifiNetwork(
            ssid = "My Super Long WiFi Network Name That Goes On And On Forever",
            bssid = "AA:BB:CC:DD:EE:10",
            signalStrength = 85,
            isProtected = true,
        ),
        WifiNetwork(ssid = "x", bssid = "AA:BB:CC:DD:EE:11", signalStrength = 99, isProtected = false),
        WifiNetwork(
            ssid = "Hidden-char \u200B\u200B",
            bssid = "AA:BB:CC:DD:EE:12",
            signalStrength = 42,
            isProtected = true,
        ),
    )

private val manyNetworks =
    (1..20).map { i ->
        WifiNetwork(
            ssid = "Network-$i",
            bssid = "AA:BB:CC:DD:EE:${i.toString().padStart(2, '0')}",
            signalStrength = (100 - i * 4).coerceAtLeast(5),
            isProtected = i % 3 != 0,
        )
    }

private val sampleDevices =
    listOf(
        DiscoveredDevice(name = "mpwrd-nm-A1B2", address = "AA:BB:CC:DD:EE:01", rssi = -52),
        DiscoveredDevice(name = null, address = "AA:BB:CC:DD:EE:02", rssi = -71),
        DiscoveredDevice(
            name = "mpwrd-nm-with-a-very-long-name-that-has-to-wrap-A1B2C3D4E5F6",
            address = "AA:BB:CC:DD:EE:04",
            rssi = -88,
        ),
        DiscoveredDevice(name = "mpwrd-nm-C3D4", address = "AA:BB:CC:DD:EE:03", rssi = null),
    )

private val noOp: () -> Unit = {}
private val noOpProvision: (String, String, Boolean) -> Unit = { _, _, _ -> }

// ---------------------------------------------------------------------------
// Phase 1: BLE scanning
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
fun ScanningBlePreview() {
    AppTheme { Surface(Modifier.fillMaxSize()) { ScanningBleContent() } }
}

// ---------------------------------------------------------------------------
// Phase 2: Device found confirmation
// ---------------------------------------------------------------------------

/** The whole screen for [state], so a preview shows the width rule and the banner as well as the step. */
@Composable
private fun WifiProvisionScreenPreview(state: WifiProvisionUiState, picksDevice: Boolean = false) {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            WifiProvisionScreenContent(
                uiState = state,
                picksDevice = picksDevice,
                snackbarHostState = remember { SnackbarHostState() },
                actions = WifiProvisionActions.None,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun DeviceFoundPreview() {
    WifiProvisionScreenPreview(
        WifiProvisionUiState(
            phase = Phase.DeviceFound,
            deviceName = "mpwrd-nm-A1B2",
            deviceAddress = "AA:BB:CC:DD:EE:01",
        ),
    )
}

@PreviewLightDark
@Composable
fun DeviceListPreview() {
    WifiProvisionScreenPreview(WifiProvisionUiState(phase = Phase.DeviceList, devices = sampleDevices))
}

@PreviewLightDark
@Composable
fun DeviceListScanningPreview() {
    WifiProvisionScreenPreview(WifiProvisionUiState(phase = Phase.Discovering, devices = sampleDevices.take(2)))
}

@PreviewLightDark
@Composable
fun DeviceListEmptyPreview() {
    WifiProvisionScreenPreview(WifiProvisionUiState(phase = Phase.DeviceList))
}

@PreviewLightDark
@Composable
fun ConfirmDevicePreview() {
    WifiProvisionScreenPreview(
        WifiProvisionUiState(
            phase = Phase.ConfirmDevice,
            pendingAddress = "AA:BB:CC:DD:EE:01",
            pendingName = "mpwrd-nm-A1B2",
        ),
    )
}

@PreviewLightDark
@Composable
private fun ConfirmDeviceUnnamedPreview() {
    WifiProvisionScreenPreview(WifiProvisionUiState(phase = Phase.ConfirmDevice, pendingAddress = "AA:BB:CC:DD:EE:01"))
}

/** The step a failed connect leaves: what happened, what to do, and a retry. */
@PreviewLightDark
@Composable
fun DeviceNotFoundPreview() {
    WifiProvisionScreenPreview(WifiProvisionUiState(error = WifiProvisionError.DeviceNotFound))
}

/** A platform whose device identifiers are not MACs: no address to compare, and a link's address is set aside. */
@PreviewLightDark
@Composable
fun DeviceListOpaqueIdentifiersPreview() {
    WifiProvisionScreenPreview(
        WifiProvisionUiState(
            phase = Phase.DeviceList,
            devices = sampleDevices,
            addressIsHardware = false,
            linkAddressIgnored = true,
        ),
    )
}

@PreviewLightDark
@Composable
private fun DeviceFoundNoNamePreview() {
    AppTheme {
        Surface(Modifier.fillMaxSize()) { DeviceFoundContent(deviceName = null, onProceed = noOp, onCancel = noOp) }
    }
}

// ---------------------------------------------------------------------------
// Phase 3: WiFi network scanning
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun ScanningNetworksPreview() {
    AppTheme { Surface(Modifier.fillMaxSize()) { ScanningNetworksContent() } }
}

// ---------------------------------------------------------------------------
// Phase 4: Connected — main configuration screen variants
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
fun ConnectedWithNetworksPreview() {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            ConnectedContent(
                networks = sampleNetworks,
                provisionStatus = ProvisionStatus.Idle,
                ipAddress = null,
                isProvisioning = false,
                isScanning = false,
                onScanNetworks = noOp,
                onProvision = noOpProvision,
                onDisconnect = noOp,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ConnectedEmptyNetworksPreview() {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            ConnectedContent(
                networks = emptyList(),
                provisionStatus = ProvisionStatus.Idle,
                ipAddress = null,
                isProvisioning = false,
                isScanning = false,
                onScanNetworks = noOp,
                onProvision = noOpProvision,
                onDisconnect = noOp,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ConnectedScanningPreview() {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            ConnectedContent(
                networks = sampleNetworks,
                provisionStatus = ProvisionStatus.Idle,
                ipAddress = null,
                isProvisioning = false,
                isScanning = true,
                onScanNetworks = noOp,
                onProvision = noOpProvision,
                onDisconnect = noOp,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ConnectedProvisioningPreview() {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            ConnectedContent(
                networks = sampleNetworks,
                provisionStatus = ProvisionStatus.Idle,
                ipAddress = null,
                isProvisioning = true,
                isScanning = false,
                onScanNetworks = noOp,
                onProvision = noOpProvision,
                onDisconnect = noOp,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ConnectedSuccessPreview() {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            ConnectedContent(
                networks = sampleNetworks,
                provisionStatus = ProvisionStatus.Success,
                ipAddress = "10.10.10.61",
                isProvisioning = false,
                isScanning = false,
                onScanNetworks = noOp,
                onProvision = noOpProvision,
                onDisconnect = noOp,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun ConnectedFailedPreview() {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            ConnectedContent(
                networks = sampleNetworks,
                provisionStatus = ProvisionStatus.Failed,
                ipAddress = null,
                isProvisioning = false,
                isScanning = false,
                onScanNetworks = noOp,
                onProvision = noOpProvision,
                onDisconnect = noOp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Edge-case previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun ConnectedLongSsidPreview() {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            ConnectedContent(
                networks = edgeCaseNetworks,
                provisionStatus = ProvisionStatus.Idle,
                ipAddress = null,
                isProvisioning = false,
                isScanning = false,
                onScanNetworks = noOp,
                onProvision = noOpProvision,
                onDisconnect = noOp,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ConnectedManyNetworksPreview() {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            ConnectedContent(
                networks = manyNetworks,
                provisionStatus = ProvisionStatus.Idle,
                ipAddress = null,
                isProvisioning = false,
                isScanning = false,
                onScanNetworks = noOp,
                onProvision = noOpProvision,
                onDisconnect = noOp,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceFoundLongNamePreview() {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            DeviceFoundContent(
                deviceName = "mpwrd-nm-A1B2C3D4E5F6-extra-long-identifier",
                onProceed = noOp,
                onCancel = noOp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Standalone component previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun ProvisionStatusCardProvisioningPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ProvisionStatusCard(provisionStatus = ProvisionStatus.Idle, isProvisioning = true)
            }
        }
    }
}

@PreviewLightDark
@Composable
fun ProvisionStatusCardSuccessPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ProvisionStatusCard(provisionStatus = ProvisionStatus.Success, isProvisioning = false)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ProvisionStatusCardFailedPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                ProvisionStatusCard(provisionStatus = ProvisionStatus.Failed, isProvisioning = false)
            }
        }
    }
}

@PreviewLightDark
@Composable
fun NetworkRowPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth()) {
                NetworkRow(network = sampleNetworks[0], isSelected = false, onClick = noOp)
                NetworkRow(network = sampleNetworks[1], isSelected = true, onClick = noOp)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NetworkRowLongSsidPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth()) {
                NetworkRow(network = edgeCaseNetworks[0], isSelected = false, onClick = noOp)
                NetworkRow(network = edgeCaseNetworks[1], isSelected = true, onClick = noOp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// mPWRD-OS disclaimer banner
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
fun MpwrdDisclaimerBannerPreview() {
    AppTheme { Surface { Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { MpwrdDisclaimerBanner() } } }
}
