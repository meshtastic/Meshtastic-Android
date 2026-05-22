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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.NodeListDensity
import org.meshtastic.core.ui.component.NodeItem
import org.meshtastic.core.ui.component.NodeItemCompact
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.NodeListSettingsState

@PreviewLightDark
@Composable
fun NodeLayoutSettingsCompactPreview() {
    AppTheme {
        Surface {
            NodeLayoutSettings(
                state =
                NodeListSettingsState(
                    density = NodeListDensity.COMPACT,
                    showPower = true,
                    showLastHeard = true,
                    lastHeardIsRelative = true,
                    showLocation = true,
                    showHops = true,
                    showSignal = true,
                    showChannel = false,
                    showRole = true,
                    showTelemetry = true,
                ),
                onDensityChange = {},
                onShowPowerChange = {},
                onShowLastHeardChange = {},
                onLastHeardIsRelativeChange = {},
                onShowLocationChange = {},
                onShowHopsChange = {},
                onShowSignalChange = {},
                onShowChannelChange = {},
                onShowRoleChange = {},
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
                state =
                NodeListSettingsState(
                    density = NodeListDensity.COMPLETE,
                    showPower = true,
                    showLastHeard = true,
                    lastHeardIsRelative = true,
                    showLocation = true,
                    showHops = true,
                    showSignal = true,
                    showChannel = true,
                    showRole = true,
                    showTelemetry = true,
                ),
                onDensityChange = {},
                onShowPowerChange = {},
                onShowLastHeardChange = {},
                onLastHeardIsRelativeChange = {},
                onShowLocationChange = {},
                onShowHopsChange = {},
                onShowSignalChange = {},
                onShowChannelChange = {},
                onShowRoleChange = {},
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
                state =
                NodeListSettingsState(
                    density = NodeListDensity.COMPACT,
                    showPower = false,
                    showLastHeard = true,
                    lastHeardIsRelative = true,
                    showLocation = false,
                    showHops = false,
                    showSignal = true,
                    showChannel = false,
                    showRole = false,
                    showTelemetry = false,
                ),
                onDensityChange = {},
                onShowPowerChange = {},
                onShowLastHeardChange = {},
                onLastHeardIsRelativeChange = {},
                onShowLocationChange = {},
                onShowHopsChange = {},
                onShowSignalChange = {},
                onShowChannelChange = {},
                onShowRoleChange = {},
                onShowTelemetryChange = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Isolated sample node previews — the preview node used in settings
// ---------------------------------------------------------------------------

private val sampleNode = previewSampleNode()
private val localNode = previewLocalNode()

/** Sample node rendered in Complete density (all fields visible). */
@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SampleNodeCompletePreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                NodeItem(
                    thisNode = localNode,
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
                NodeItemCompact(thisNode = localNode, thatNode = sampleNode, distanceUnits = 0)
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
                    thisNode = localNode,
                    thatNode = previewSampleNode(hopsAway = 0),
                    distanceUnits = 0,
                    showPower = false,
                    showLastHeard = true,
                    lastHeardIsRelative = true,
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
                    thisNode = localNode,
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

/**
 * Matrix view showing compact node item in various toggle states. Each row: label describing active toggles → rendered
 * node item.
 */
@Suppress("PreviewPublic", "LongMethod")
@PreviewLightDark
@Composable
fun SampleNodeCompactToggleMatrixPreview() {
    val node = previewSampleNode(hopsAway = 0)
    val local = previewLocalNode()
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MatrixRow("All fields") { NodeItemCompact(thisNode = local, thatNode = node, distanceUnits = 0) }
                MatrixRow("Health only") {
                    NodeItemCompact(
                        thisNode = local,
                        thatNode = node,
                        distanceUnits = 0,
                        showHops = false,
                        showChannel = false,
                        showRole = false,
                        showTelemetry = false,
                    )
                }
                MatrixRow("No metrics") {
                    NodeItemCompact(thisNode = local, thatNode = node, distanceUnits = 0, showTelemetry = false)
                }
                MatrixRow("No footer") {
                    NodeItemCompact(
                        thisNode = local,
                        thatNode = node,
                        distanceUnits = 0,
                        showHops = false,
                        showChannel = false,
                        showRole = false,
                    )
                }
                MatrixRow("Metrics + footer") {
                    NodeItemCompact(
                        thisNode = local,
                        thatNode = node,
                        distanceUnits = 0,
                        showPower = false,
                        showLastHeard = false,
                        showLocation = false,
                        showSignal = false,
                    )
                }
                MatrixRow("Minimal (name only)") {
                    NodeItemCompact(
                        thisNode = local,
                        thatNode = node,
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
}

/** Matrix view showing complete node item with/without metrics toggle. */
@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SampleNodeCompleteToggleMatrixPreview() {
    val node = previewSampleNode()
    val local = previewLocalNode()
    AppTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MatrixRow("With metrics") {
                    NodeItem(
                        thisNode = local,
                        thatNode = node,
                        distanceUnits = 0,
                        tempInFahrenheit = false,
                        connectionState = ConnectionState.Connected,
                        showTelemetry = true,
                    )
                }
                MatrixRow("Without metrics") {
                    NodeItem(
                        thisNode = local,
                        thatNode = node,
                        distanceUnits = 0,
                        tempInFahrenheit = false,
                        connectionState = ConnectionState.Connected,
                        showTelemetry = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                    thisNode = localNode,
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
                    thisNode = localNode,
                    thatNode = sampleNode,
                    distanceUnits = 1,
                    tempInFahrenheit = true,
                    connectionState = ConnectionState.Connected,
                )
            }
        }
    }
}
