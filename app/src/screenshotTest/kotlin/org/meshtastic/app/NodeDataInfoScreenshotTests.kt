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
import org.meshtastic.app.preview.ChannelInfoPreview
import org.meshtastic.app.preview.DistanceInfoPreview
import org.meshtastic.app.preview.HopsInfoPreview
import org.meshtastic.app.preview.IconInfoPreview
import org.meshtastic.app.preview.LastHeardInfoPreview
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.SnrRssiPreview

/** Screenshot tests for node data info components (distance, last heard, hops, channel, icons, SNR/RSSI). */
class NodeDataInfoScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun distanceInfoScreenshot() {
        DistanceInfoPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun lastHeardInfoScreenshot() {
        LastHeardInfoPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun hopsInfoScreenshot() {
        HopsInfoPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun channelInfoScreenshot() {
        ChannelInfoPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun iconInfoScreenshot() {
        IconInfoPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun snrRssiScreenshot() {
        SnrRssiPreview()
    }
}
