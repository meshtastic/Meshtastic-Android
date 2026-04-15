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
package org.meshtastic.app

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.WifiConnectedEmptyPreview
import org.meshtastic.app.preview.WifiConnectedFailedPreview
import org.meshtastic.app.preview.WifiConnectedProvisioningPreview
import org.meshtastic.app.preview.WifiConnectedScanningPreview
import org.meshtastic.app.preview.WifiConnectedSuccessPreview
import org.meshtastic.app.preview.WifiConnectedWithNetworksPreview
import org.meshtastic.app.preview.WifiDeviceFoundLongNamePreview
import org.meshtastic.app.preview.WifiDeviceFoundNoNamePreview
import org.meshtastic.app.preview.WifiDeviceFoundPreview
import org.meshtastic.app.preview.WifiLongSsidPreview
import org.meshtastic.app.preview.WifiManyNetworksPreview
import org.meshtastic.app.preview.WifiMpwrdDisclaimerPreview
import org.meshtastic.app.preview.WifiNetworkRowLongSsidPreview
import org.meshtastic.app.preview.WifiNetworkRowPreview
import org.meshtastic.app.preview.WifiProvisionStatusFailedPreview
import org.meshtastic.app.preview.WifiProvisionStatusProvisioningPreview
import org.meshtastic.app.preview.WifiProvisionStatusSuccessPreview
import org.meshtastic.app.preview.WifiScanningBlePreview
import org.meshtastic.app.preview.WifiScanningNetworksPreview

/** Screenshot tests for WiFi provisioning feature components. */
class WifiProvisionScreenshotTests {
    // Phase 1: BLE scanning
    @PreviewTest
    @MultiPreview
    @Composable
    fun scanningBleScreenshot() {
        WifiScanningBlePreview()
    }

    // Phase 2: Device found
    @PreviewTest
    @MultiPreview
    @Composable
    fun deviceFoundScreenshot() {
        WifiDeviceFoundPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun deviceFoundNoNameScreenshot() {
        WifiDeviceFoundNoNamePreview()
    }

    // Phase 3: Network scanning
    @PreviewTest
    @MultiPreview
    @Composable
    fun scanningNetworksScreenshot() {
        WifiScanningNetworksPreview()
    }

    // Phase 4: Connected states
    @PreviewTest
    @MultiPreview
    @Composable
    fun connectedWithNetworksScreenshot() {
        WifiConnectedWithNetworksPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun connectedEmptyScreenshot() {
        WifiConnectedEmptyPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun connectedScanningScreenshot() {
        WifiConnectedScanningPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun connectedProvisioningScreenshot() {
        WifiConnectedProvisioningPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun connectedSuccessScreenshot() {
        WifiConnectedSuccessPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun connectedFailedScreenshot() {
        WifiConnectedFailedPreview()
    }

    // Edge cases
    @PreviewTest
    @MultiPreview
    @Composable
    fun longSsidScreenshot() {
        WifiLongSsidPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun manyNetworksScreenshot() {
        WifiManyNetworksPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun deviceFoundLongNameScreenshot() {
        WifiDeviceFoundLongNamePreview()
    }

    // Standalone components
    @PreviewTest
    @MultiPreview
    @Composable
    fun provisionStatusProvisioningScreenshot() {
        WifiProvisionStatusProvisioningPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun provisionStatusSuccessScreenshot() {
        WifiProvisionStatusSuccessPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun provisionStatusFailedScreenshot() {
        WifiProvisionStatusFailedPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun networkRowScreenshot() {
        WifiNetworkRowPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun networkRowLongSsidScreenshot() {
        WifiNetworkRowLongSsidPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun mpwrdDisclaimerScreenshot() {
        WifiMpwrdDisclaimerPreview()
    }
}
