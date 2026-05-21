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

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.core.common.ContextServices
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
import org.meshtastic.feature.node.metrics.DeviceMetricsCardPreview
import org.meshtastic.feature.node.metrics.LegendPreview
import org.meshtastic.feature.node.metrics.PreviewEnvironmentMetricsContent

@Composable
private fun initializeContextServicesAppForPreview() {
    val appContext = LocalContext.current.applicationContext
    val app =
        Application().also {
            val attachBaseContext = ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            attachBaseContext.isAccessible = true
            attachBaseContext.invoke(it, appContext)
        }
    ContextServices.app = app
}

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
    initializeContextServicesAppForPreview()
    DeviceMetricsCardPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotEnvironmentMetricsContent() {
    initializeContextServicesAppForPreview()
    PreviewEnvironmentMetricsContent()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotNodeDetailContentMinimal() {
    NodeDetailContentMinimalPreview()
}
