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

package org.meshtastic.feature.node.component

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.proto.Config

// ---------------------------------------------------------------------------
// Sample data for previews
// ---------------------------------------------------------------------------

private val previewData = NodePreviewParameterProvider()

// ---------------------------------------------------------------------------
// DeviceActions previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun DeviceActionsRemotePreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            DeviceActions(
                node = node,
                ourNode = previewData.mickeyMouse.copy(num = 9999),
                lastTracerouteTime = null,
                lastRequestNeighborsTime = null,
                availableLogs =
                setOf(
                    LogsType.DEVICE,
                    LogsType.POSITIONS,
                    LogsType.ENVIRONMENT,
                    LogsType.SIGNAL,
                    LogsType.TRACEROUTE,
                ),
                onAction = {},
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                isFahrenheit = false,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceActionsLocalPreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            DeviceActions(
                node = node,
                ourNode = node,
                lastTracerouteTime = null,
                lastRequestNeighborsTime = null,
                availableLogs = setOf(LogsType.DEVICE, LogsType.POSITIONS),
                onAction = {},
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                isFahrenheit = false,
                isLocal = true,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// TelemetricActionsSection previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun TelemetricActionsSectionPreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            TelemetricActionsSection(
                node = node,
                ourNode = previewData.mickeyMouse.copy(num = 9999),
                availableLogs =
                setOf(
                    LogsType.DEVICE,
                    LogsType.POSITIONS,
                    LogsType.ENVIRONMENT,
                    LogsType.SIGNAL,
                    LogsType.TRACEROUTE,
                    LogsType.NEIGHBOR_INFO,
                ),
                lastTracerouteTime = null,
                lastRequestNeighborsTime = null,
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                isFahrenheit = false,
                onAction = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TelemetricActionsSectionEmptyPreview() {
    val node = previewData.minnieMouse
    AppTheme {
        Surface {
            TelemetricActionsSection(
                node = node,
                ourNode = previewData.mickeyMouse,
                availableLogs = emptySet(),
                lastTracerouteTime = null,
                lastRequestNeighborsTime = null,
                displayUnits = Config.DisplayConfig.DisplayUnits.IMPERIAL,
                isFahrenheit = true,
                onAction = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PositionInlineContent preview
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun PositionInlineContentPreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            PositionInlineContent(
                node = node,
                ourNode = previewData.mickeyMouse.copy(num = 9999),
                displayUnits = Config.DisplayConfig.DisplayUnits.METRIC,
                onAction = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------
// NodeDetailsSection preview
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun NodeDetailsSectionPreview() {
    val node = previewData.mickeyMouse
    AppTheme { Surface { NodeDetailsSection(node = node) } }
}
