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

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.feature.wifiprovision.ui.ConfirmDevicePreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedFailedPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedSuccessPreview
import org.meshtastic.feature.wifiprovision.ui.ConnectedWithNetworksPreview
import org.meshtastic.feature.wifiprovision.ui.DeviceFoundPreview
import org.meshtastic.feature.wifiprovision.ui.DeviceListEmptyPreview
import org.meshtastic.feature.wifiprovision.ui.DeviceListOpaqueIdentifiersPreview
import org.meshtastic.feature.wifiprovision.ui.DeviceListPreview
import org.meshtastic.feature.wifiprovision.ui.DeviceListScanningPreview
import org.meshtastic.feature.wifiprovision.ui.DeviceNotFoundPreview
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

// Device choice and confirmation: phone (light and dark), a wide window that exercises the width rule, and the
// largest font scale, which must not clip a row, a label or a button.

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceList() {
    DeviceListPreview()
}

@PreviewTest
@Preview(name = "wide", widthDp = WIDE_DP, heightDp = WIDE_HEIGHT_DP)
@Preview(name = "wide dark", widthDp = WIDE_DP, heightDp = WIDE_HEIGHT_DP, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ScreenshotDeviceListWide() {
    DeviceListPreview()
}

@PreviewTest
@Preview(name = "font scale 2x", widthDp = PHONE_DP, heightDp = LARGE_FONT_HEIGHT_DP, fontScale = 2.0f)
@Composable
fun ScreenshotDeviceListLargeFont() {
    DeviceListPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceListScanning() {
    DeviceListScanningPreview()
}

@PreviewTest
@Preview(name = "wide", widthDp = WIDE_DP, heightDp = WIDE_HEIGHT_DP)
@Preview(name = "wide dark", widthDp = WIDE_DP, heightDp = WIDE_HEIGHT_DP, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ScreenshotDeviceListScanningWide() {
    DeviceListScanningPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceListEmpty() {
    DeviceListEmptyPreview()
}

@PreviewTest
@Preview(name = "wide", widthDp = WIDE_DP, heightDp = WIDE_HEIGHT_DP)
@Preview(name = "wide dark", widthDp = WIDE_DP, heightDp = WIDE_HEIGHT_DP, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ScreenshotDeviceListEmptyWide() {
    DeviceListEmptyPreview()
}

@PreviewTest
@Preview(name = "font scale 2x", widthDp = PHONE_DP, heightDp = LARGE_FONT_HEIGHT_DP, fontScale = 2.0f)
@Composable
fun ScreenshotDeviceListEmptyLargeFont() {
    DeviceListEmptyPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotConfirmDevice() {
    ConfirmDevicePreview()
}

@PreviewTest
@Preview(name = "wide", widthDp = WIDE_DP, heightDp = WIDE_HEIGHT_DP)
@Preview(name = "wide dark", widthDp = WIDE_DP, heightDp = WIDE_HEIGHT_DP, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ScreenshotConfirmDeviceWide() {
    ConfirmDevicePreview()
}

@PreviewTest
@Preview(name = "font scale 2x", widthDp = PHONE_DP, heightDp = LARGE_FONT_HEIGHT_DP, fontScale = 2.0f)
@Composable
fun ScreenshotConfirmDeviceLargeFont() {
    ConfirmDevicePreview()
}

@PreviewTest
@Preview(name = "wide", widthDp = WIDE_DP, heightDp = WIDE_HEIGHT_DP)
@Preview(name = "wide dark", widthDp = WIDE_DP, heightDp = WIDE_HEIGHT_DP, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ScreenshotDeviceFoundWide() {
    DeviceFoundPreview()
}

@PreviewTest
@Preview(name = "font scale 2x", widthDp = PHONE_DP, heightDp = LARGE_FONT_HEIGHT_DP, fontScale = 2.0f)
@Composable
fun ScreenshotDeviceFoundLargeFont() {
    DeviceFoundPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceNotFound() {
    DeviceNotFoundPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceListOpaqueIdentifiers() {
    DeviceListOpaqueIdentifiersPreview()
}

private const val PHONE_DP = 411

// Tall enough that nothing at 2x font scale sits below the fold, so the golden shows every control.
private const val LARGE_FONT_HEIGHT_DP = 1600
private const val WIDE_DP = 900
private const val WIDE_HEIGHT_DP = 640
