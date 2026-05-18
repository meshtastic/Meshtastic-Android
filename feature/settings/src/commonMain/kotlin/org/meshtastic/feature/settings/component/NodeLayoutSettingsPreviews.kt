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
package org.meshtastic.feature.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.NodeListDensity
import org.meshtastic.core.ui.component.NodeItem
import org.meshtastic.core.ui.component.NodeItemCompact
import org.meshtastic.core.ui.theme.AppTheme

@PreviewLightDark
@Composable
fun NodeLayoutSettingsCompactPreview() {
    AppTheme {
        Surface {
            NodeLayoutSettings(
                density = NodeListDensity.COMPACT,
                onDensityChange = {},
                showPower = true,
                onShowPowerChange = {},
                showLastHeard = true,
                onShowLastHeardChange = {},
                lastHeardIsRelative = true,
                onLastHeardIsRelativeChange = {},
                showLocation = true,
                onShowLocationChange = {},
                showHops = true,
                onShowHopsChange = {},
                showSignal = true,
                onShowSignalChange = {},
                showChannel = false,
                onShowChannelChange = {},
                showRole = true,
                onShowRoleChange = {},
                showTelemetry = true,
                onShowTelemetryChange = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
fun NodeLayoutSettingsCompletePreview() {
    AppTheme {
        Surface {
            NodeLayoutSettings(
                density = NodeListDensity.COMPLETE,
                onDensityChange = {},
                showPower = true,
                onShowPowerChange = {},
                showLastHeard = true,
                onShowLastHeardChange = {},
                lastHeardIsRelative = true,
                onLastHeardIsRelativeChange = {},
                showLocation = true,
                onShowLocationChange = {},
                showHops = true,
                onShowHopsChange = {},
                showSignal = true,
                onShowSignalChange = {},
                showChannel = true,
                onShowChannelChange = {},
                showRole = true,
                onShowRoleChange = {},
                showTelemetry = true,
                onShowTelemetryChange = {},
            )
        }
    }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun NodeLayoutSettingsCompactMinimalPreview() {
    AppTheme {
        Surface {
            NodeLayoutSettings(
                density = NodeListDensity.COMPACT,
                onDensityChange = {},
                showPower = false,
                onShowPowerChange = {},
                showLastHeard = true,
                onShowLastHeardChange = {},
                lastHeardIsRelative = false,
                onLastHeardIsRelativeChange = {},
                showLocation = false,
                onShowLocationChange = {},
                showHops = false,
                onShowHopsChange = {},
                showSignal = true,
                onShowSignalChange = {},
                showChannel = false,
                onShowChannelChange = {},
                showRole = false,
                onShowRoleChange = {},
                showTelemetry = false,
                onShowTelemetryChange = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Isolated sample node previews — the preview node used in settings
// ---------------------------------------------------------------------------

private val sampleNode = previewSampleNode()

/** Sample node rendered in Complete density (all fields visible). */
@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SampleNodeCompletePreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItem(
                    thisNode = null,
                    thatNode = sampleNode,
                    distanceUnits = 0,
                    tempInFahrenheit = false,
                    connectionState = ConnectionState.Connected,
                )
            }
        }
    }
}

/** Sample node rendered in Compact density with all fields enabled. */
@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SampleNodeCompactAllFieldsPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItemCompact(thisNode = null, thatNode = sampleNode, distanceUnits = 0)
            }
        }
    }
}

/** Sample node in Compact with only signal + last heard (absolute time). */
@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SampleNodeCompactSignalOnlyPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItemCompact(
                    thisNode = null,
                    thatNode = sampleNode,
                    distanceUnits = 0,
                    showPower = false,
                    showLastHeard = true,
                    lastHeardIsRelative = false,
                    showLocation = false,
                    showHops = false,
                    showSignal = true,
                    showChannel = false,
                    showRole = false,
                    showTelemetry = false,
                )
            }
        }
    }
}

/** Sample node in Compact with no optional fields — name row only. */
@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SampleNodeCompactNameOnlyPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItemCompact(
                    thisNode = null,
                    thatNode = sampleNode,
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

/** Sample node in Complete density with Fahrenheit temperature. */
@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SampleNodeCompleteFahrenheitPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItem(
                    thisNode = null,
                    thatNode = sampleNode,
                    distanceUnits = 0,
                    tempInFahrenheit = true,
                    connectionState = ConnectionState.Connected,
                )
            }
        }
    }
}

/** Sample node in Complete density with Imperial units. */
@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SampleNodeCompleteImperialPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItem(
                    thisNode = null,
                    thatNode = sampleNode,
                    distanceUnits = 1,
                    tempInFahrenheit = true,
                    connectionState = ConnectionState.Connected,
                )
            }
        }
    }
}
