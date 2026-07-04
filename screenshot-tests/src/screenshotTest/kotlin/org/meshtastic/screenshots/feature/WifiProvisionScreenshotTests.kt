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
package org.meshtastic.screenshots.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.feature.wifiprovision.ui.ConnectedFailedPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedSuccessPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedWithNetworksPreview
import org.meshtastic.feature.wifiprovision.ui.DeviceFoundPreview
import org.meshtastic.feature.wifiprovision.ui.MpwrdDisclaimerBannerPreview
import org.meshtastic.feature.wifiprovision.ui.NetworkRowPreview
import org.meshtastic.feature.wifiprovision.ui.ProvisionStatusCardSuccessPreview
import org.meshtastic.feature.wifiprovision.ui.ScanningBlePreview

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotScanningBle() {
    ScanningBlePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceFound() {
    DeviceFoundPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotConnectedWithNetworks() {
    ConnectedWithNetworksPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotConnectedSuccess() {
    ConnectedSuccessPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotConnectedFailed() {
    ConnectedFailedPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNetworkRow() {
    NetworkRowPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotProvisionStatusCardSuccess() {
    ProvisionStatusCardSuccessPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotMpwrdDisclaimerBanner() {
    MpwrdDisclaimerBannerPreview()
}
