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
package org.meshtastic.screenshots.core

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.ui.qr.PreviewScannedQrCodeAddWithRoom
import org.meshtastic.core.ui.qr.PreviewScannedQrCodeReplaceFewChanges
import org.meshtastic.core.ui.qr.PreviewScannedQrCodeReplaceManyChanges

// The channel import dialog keeps its title and its Cancel and confirm buttons on screen while the channels and the
// list of changes scroll between them. The short-window and large-font references fail if the buttons are pushed off.

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotScannedQrCodeReplaceManyChanges() {
    PreviewScannedQrCodeReplaceManyChanges()
}

// A fixed window height: a short dialog in an auto-sized preview is measured twice and records a clipped list.
@PreviewTest
@Preview(name = "phone - light", widthDp = 360, heightDp = 800)
@Preview(name = "phone - dark", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ScreenshotScannedQrCodeReplaceFewChanges() {
    PreviewScannedQrCodeReplaceFewChanges()
}

@PreviewTest
@Preview(name = "phone - light", widthDp = 360, heightDp = 800)
@Preview(name = "phone - dark", widthDp = 360, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ScreenshotScannedQrCodeAddWithRoom() {
    PreviewScannedQrCodeAddWithRoom()
}

@PreviewTest
@Preview(name = "short phone - light", widthDp = 360, heightDp = 640)
@Preview(
    name = "short phone - dark",
    widthDp = 360,
    heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ScreenshotScannedQrCodeReplaceManyChangesShortPhone() {
    PreviewScannedQrCodeReplaceManyChanges()
}

@PreviewTest
@Preview(name = "short phone - font scale 2x", widthDp = 360, heightDp = 640, fontScale = 2.0f)
@Composable
fun ScreenshotScannedQrCodeReplaceManyChangesLargeFont() {
    PreviewScannedQrCodeReplaceManyChanges()
}
