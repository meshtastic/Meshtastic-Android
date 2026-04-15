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
import org.meshtastic.feature.node.component.DeviceActionsLocalPreview
import org.meshtastic.feature.node.component.DeviceActionsRemotePreview
import org.meshtastic.feature.node.component.NodeDetailsSectionPreview
import org.meshtastic.feature.node.component.PositionInlineContentPreview
import org.meshtastic.feature.node.component.TelemetricActionsSectionEmptyPreview
import org.meshtastic.feature.node.component.TelemetricActionsSectionPreview
import org.meshtastic.feature.node.detail.NodeDetailContentLoadingPreview
import org.meshtastic.feature.node.detail.NodeDetailContentLocalPreview
import org.meshtastic.feature.node.detail.NodeDetailContentMinimalPreview
import org.meshtastic.feature.node.detail.NodeDetailContentRemotePreview

/** Re-exports of internal node detail previews for screenshot testing. */

// DeviceActions
@MultiPreview
@Composable
fun NodeDeviceActionsRemotePreview() {
    DeviceActionsRemotePreview()
}

@MultiPreview
@Composable
fun NodeDeviceActionsLocalPreview() {
    DeviceActionsLocalPreview()
}

// TelemetricActionsSection
@MultiPreview
@Composable
fun NodeTelemetricActionsSectionPreview() {
    TelemetricActionsSectionPreview()
}

@MultiPreview
@Composable
fun NodeTelemetricActionsSectionEmptyPreview() {
    TelemetricActionsSectionEmptyPreview()
}

// PositionInlineContent
@MultiPreview
@Composable
fun NodePositionInlineContentPreview() {
    PositionInlineContentPreview()
}

// NodeDetailsSection
@MultiPreview
@Composable
fun NodeNodeDetailsSectionPreview() {
    NodeDetailsSectionPreview()
}

// NodeDetailContent
@MultiPreview
@Composable
fun NodeDetailContentRemoteScreenshotPreview() {
    NodeDetailContentRemotePreview()
}

@MultiPreview
@Composable
fun NodeDetailContentLocalScreenshotPreview() {
    NodeDetailContentLocalPreview()
}

@MultiPreview
@Composable
fun NodeDetailContentLoadingScreenshotPreview() {
    NodeDetailContentLoadingPreview()
}

@MultiPreview
@Composable
fun NodeDetailContentMinimalScreenshotPreview() {
    NodeDetailContentMinimalPreview()
}
