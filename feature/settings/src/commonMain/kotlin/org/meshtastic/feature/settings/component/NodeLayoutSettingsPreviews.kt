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

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.model.NodeListDensity
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
