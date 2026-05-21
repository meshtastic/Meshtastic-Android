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
@file:Suppress("TooManyFunctions", "MagicNumber", "PreviewPublic")

package org.meshtastic.feature.node.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.ui.component.NodeItem
import org.meshtastic.core.ui.component.NodeItemCompact
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme

// ---------------------------------------------------------------------------
// Sample data for previews
// ---------------------------------------------------------------------------

private val previewNodes = NodePreviewParameterProvider()

// ---------------------------------------------------------------------------
// NodeItem (Complete density) previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
fun NodeItemCompletePreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItem(
                    thisNode = previewNodes.mickeyMouse,
                    thatNode = previewNodes.minnieMouse,
                    distanceUnits = 0,
                    tempInFahrenheit = false,
                    connectionState = ConnectionState.Connected,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
fun NodeItemCompleteActivePreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItem(
                    thisNode = previewNodes.mickeyMouse,
                    thatNode = previewNodes.mickeyMouse,
                    distanceUnits = 0,
                    tempInFahrenheit = false,
                    connectionState = ConnectionState.Connected,
                    isActive = true,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// NodeItemCompact previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
fun NodeItemCompactAllFieldsPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItemCompact(
                    thisNode = previewNodes.mickeyMouse,
                    thatNode = previewNodes.minnieMouse,
                    distanceUnits = 0,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
fun NodeItemCompactMinimalPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItemCompact(
                    thisNode = previewNodes.mickeyMouse,
                    thatNode = previewNodes.minnieMouse,
                    distanceUnits = 0,
                    showPower = false,
                    showLastHeard = false,
                    showLocation = false,
                    showHops = false,
                    showSignal = false,
                    showChannel = false,
                    showRole = false,
                    showTelemetry = false,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
fun NodeItemCompactActivePreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItemCompact(
                    thisNode = previewNodes.mickeyMouse,
                    thatNode = previewNodes.mickeyMouse,
                    distanceUnits = 0,
                    isActive = true,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
fun NodeItemCompactOnlineRemotePreview() {
    val onlineNode =
        previewNodes.minnieMouse.copy(lastHeard = (org.meshtastic.core.common.util.nowSeconds - 300).toInt())
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItemCompact(thisNode = previewNodes.mickeyMouse, thatNode = onlineNode, distanceUnits = 0)
            }
        }
    }
}

@PreviewLightDark
@Composable
fun NodeItemCompleteOnlineRemotePreview() {
    val onlineNode =
        previewNodes.minnieMouse.copy(lastHeard = (org.meshtastic.core.common.util.nowSeconds - 300).toInt())
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItem(
                    thisNode = previewNodes.mickeyMouse,
                    thatNode = onlineNode,
                    distanceUnits = 0,
                    tempInFahrenheit = false,
                    connectionState = ConnectionState.Connected,
                )
            }
        }
    }
}
