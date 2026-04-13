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
package org.meshtastic.app.preview

import androidx.compose.runtime.Composable
import org.meshtastic.feature.wifiprovision.ui.ConnectedEmptyNetworksPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedFailedPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedLongSsidPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedManyNetworksPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedProvisioningPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedScanningPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedSuccessPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedWithNetworksPreview
import org.meshtastic.feature.wifiprovision.ui.DeviceFoundLongNamePreview
import org.meshtastic.feature.wifiprovision.ui.DeviceFoundNoNamePreview
import org.meshtastic.feature.wifiprovision.ui.DeviceFoundPreview
import org.meshtastic.feature.wifiprovision.ui.MpwrdDisclaimerBannerPreview
import org.meshtastic.feature.wifiprovision.ui.NetworkRowLongSsidPreview
import org.meshtastic.feature.wifiprovision.ui.NetworkRowPreview
import org.meshtastic.feature.wifiprovision.ui.ProvisionStatusCardFailedPreview
import org.meshtastic.feature.wifiprovision.ui.ProvisionStatusCardProvisioningPreview
import org.meshtastic.feature.wifiprovision.ui.ProvisionStatusCardSuccessPreview
import org.meshtastic.feature.wifiprovision.ui.ScanningBlePreview
import org.meshtastic.feature.wifiprovision.ui.ScanningNetworksPreview

/** Re-exports of internal wifi-provision previews for screenshot testing. */

// Phase 1: BLE scanning
@MultiPreview
@Composable
fun WifiScanningBlePreview() {
    ScanningBlePreview()
}

// Phase 2: Device found
@MultiPreview
@Composable
fun WifiDeviceFoundPreview() {
    DeviceFoundPreview()
}

@MultiPreview
@Composable
fun WifiDeviceFoundNoNamePreview() {
    DeviceFoundNoNamePreview()
}

// Phase 3: Network scanning
@MultiPreview
@Composable
fun WifiScanningNetworksPreview() {
    ScanningNetworksPreview()
}

// Phase 4: Connected states
@MultiPreview
@Composable
fun WifiConnectedWithNetworksPreview() {
    ConnectedWithNetworksPreview()
}

@MultiPreview
@Composable
fun WifiConnectedEmptyPreview() {
    ConnectedEmptyNetworksPreview()
}

@MultiPreview
@Composable
fun WifiConnectedScanningPreview() {
    ConnectedScanningPreview()
}

@MultiPreview
@Composable
fun WifiConnectedProvisioningPreview() {
    ConnectedProvisioningPreview()
}

@MultiPreview
@Composable
fun WifiConnectedSuccessPreview() {
    ConnectedSuccessPreview()
}

@MultiPreview
@Composable
fun WifiConnectedFailedPreview() {
    ConnectedFailedPreview()
}

// Edge cases
@MultiPreview
@Composable
fun WifiLongSsidPreview() {
    ConnectedLongSsidPreview()
}

@MultiPreview
@Composable
fun WifiManyNetworksPreview() {
    ConnectedManyNetworksPreview()
}

@MultiPreview
@Composable
fun WifiDeviceFoundLongNamePreview() {
    DeviceFoundLongNamePreview()
}

// Standalone components
@MultiPreview
@Composable
fun WifiProvisionStatusProvisioningPreview() {
    ProvisionStatusCardProvisioningPreview()
}

@MultiPreview
@Composable
fun WifiProvisionStatusSuccessPreview() {
    ProvisionStatusCardSuccessPreview()
}

@MultiPreview
@Composable
fun WifiProvisionStatusFailedPreview() {
    ProvisionStatusCardFailedPreview()
}

@MultiPreview
@Composable
fun WifiNetworkRowPreview() {
    NetworkRowPreview()
}

@MultiPreview
@Composable
fun WifiNetworkRowLongSsidPreview() {
    NetworkRowLongSsidPreview()
}

@MultiPreview
@Composable
fun WifiMpwrdDisclaimerPreview() {
    MpwrdDisclaimerBannerPreview()
}
