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
import org.meshtastic.feature.node.component.DeviceActionsLocalPreview
import org.meshtastic.feature.node.component.DeviceActionsRemotePreview
import org.meshtastic.feature.node.component.NodeDetailsSectionPreview
import org.meshtastic.feature.node.component.NodeItemCompactActivePreview
import org.meshtastic.feature.node.component.NodeItemCompactAllFieldsPreview
import org.meshtastic.feature.node.component.NodeItemCompactMinimalPreview
import org.meshtastic.feature.node.component.NodeItemCompactOnlineRemotePreview
import org.meshtastic.feature.node.component.NodeItemCompleteActivePreview
import org.meshtastic.feature.node.component.NodeItemCompleteOnlineRemotePreview
import org.meshtastic.feature.node.component.NodeItemCompletePreview
import org.meshtastic.feature.node.component.PositionInlineContentPreview
import org.meshtastic.feature.node.component.TelemetricActionsSectionEmptyPreview
import org.meshtastic.feature.node.component.TelemetricActionsSectionPreview
import org.meshtastic.feature.node.detail.NodeDetailContentLoadingPreview
import org.meshtastic.feature.node.detail.NodeDetailContentLocalPreview
import org.meshtastic.feature.node.detail.NodeDetailContentMinimalPreview
import org.meshtastic.feature.node.detail.NodeDetailContentRemotePreview
import org.meshtastic.feature.node.metrics.DeviceMetricsCardPreview
import org.meshtastic.feature.node.metrics.LegendPreview
import org.meshtastic.feature.node.metrics.PreviewEnvironmentMetricsContent

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceActionsRemote() {
    DeviceActionsRemotePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceActionsLocal() {
    DeviceActionsLocalPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTelemetricActionsSection() {
    TelemetricActionsSectionPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotTelemetricActionsSectionEmpty() {
    TelemetricActionsSectionEmptyPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotPositionInlineContent() {
    PositionInlineContentPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeDetailsSection() {
    NodeDetailsSectionPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeDetailContentRemote() {
    NodeDetailContentRemotePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeDetailContentLocal() {
    NodeDetailContentLocalPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeDetailContentLoading() {
    NodeDetailContentLoadingPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotLegend() {
    LegendPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDeviceMetricsCard() {
    DeviceMetricsCardPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotEnvironmentMetricsContent() {
    PreviewEnvironmentMetricsContent()
}

// ---------------------------------------------------------------------------
// Node list item screenshots (Complete + Compact densities)
// ---------------------------------------------------------------------------

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeItemComplete() {
    NodeItemCompletePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeItemCompleteActive() {
    NodeItemCompleteActivePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeItemCompactAllFields() {
    NodeItemCompactAllFieldsPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeItemCompactMinimal() {
    NodeItemCompactMinimalPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeItemCompactActive() {
    NodeItemCompactActivePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeItemCompactOnlineRemote() {
    NodeItemCompactOnlineRemotePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeItemCompleteOnlineRemote() {
    NodeItemCompleteOnlineRemotePreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeDetailContentMinimal() {
    NodeDetailContentMinimalPreview()
}
