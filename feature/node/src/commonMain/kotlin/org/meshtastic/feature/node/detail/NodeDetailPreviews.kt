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

package org.meshtastic.feature.node.detail

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState

// ---------------------------------------------------------------------------
// Sample data for previews
// ---------------------------------------------------------------------------

private val previewData = NodePreviewParameterProvider()

// ---------------------------------------------------------------------------
// NodeDetailContent previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun NodeDetailContentRemotePreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            NodeDetailContent(
                uiState =
                NodeDetailUiState(
                    node = node,
                    ourNode = previewData.mickeyMouse.copy(num = 9999),
                    metricsState = MetricsState(isLocal = false, isManaged = false),
                    availableLogs =
                    setOf(
                        LogsType.DEVICE,
                        LogsType.POSITIONS,
                        LogsType.ENVIRONMENT,
                        LogsType.SIGNAL,
                        LogsType.TRACEROUTE,
                    ),
                ),
                onAction = {},
                onFirmwareSelect = {},
                onSaveNotes = { _, _ -> },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun NodeDetailContentLocalPreview() {
    val node = previewData.mickeyMouse
    AppTheme {
        Surface {
            NodeDetailContent(
                uiState =
                NodeDetailUiState(
                    node = node,
                    ourNode = node,
                    metricsState = MetricsState(isLocal = true, isManaged = false),
                    availableLogs = setOf(LogsType.DEVICE, LogsType.POSITIONS),
                ),
                onAction = {},
                onFirmwareSelect = {},
                onSaveNotes = { _, _ -> },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun NodeDetailContentLoadingPreview() {
    AppTheme {
        Surface {
            NodeDetailContent(
                uiState = NodeDetailUiState(),
                onAction = {},
                onFirmwareSelect = {},
                onSaveNotes = { _, _ -> },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun NodeDetailContentMinimalPreview() {
    val node = previewData.minnieMouse
    AppTheme {
        Surface {
            NodeDetailContent(
                uiState =
                NodeDetailUiState(
                    node = node,
                    ourNode = previewData.mickeyMouse,
                    metricsState = MetricsState(isLocal = false, isManaged = true),
                    availableLogs = emptySet(),
                ),
                onAction = {},
                onFirmwareSelect = {},
                onSaveNotes = { _, _ -> },
            )
        }
    }
}
