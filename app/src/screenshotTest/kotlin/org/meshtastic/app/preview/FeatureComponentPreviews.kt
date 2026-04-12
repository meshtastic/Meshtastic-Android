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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.component.NodeFilterTextField
import org.meshtastic.feature.node.metrics.LogLine
import org.meshtastic.feature.settings.component.ExpressiveSection
import org.meshtastic.feature.settings.component.HomoglyphSetting
import org.meshtastic.feature.settings.component.NotificationSection
import org.meshtastic.feature.settings.component.ThemePickerDialog
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.feature.settings.radio.component.LoadingOverlay
import org.meshtastic.feature.settings.radio.component.MapReportingPreference
import org.meshtastic.feature.settings.radio.component.NodeActionButton
import org.meshtastic.feature.settings.radio.component.WarningDialog

@MultiPreview
@Composable
fun LogLinePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LogLine(label = "Temperature", value = "24.5 C")
                LogLine(label = "Humidity", value = "62%")
                LogLine(label = "Battery", value = "85%")
            }
        }
    }
}

@MultiPreview
@Composable
fun NodeFilterTextFieldPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                NodeFilterTextField(
                    filterText = "",
                    onTextChange = {},
                    currentSortOption = NodeSortOption.LAST_HEARD,
                    onSortSelect = {},
                    includeUnknown = false,
                    onToggleIncludeUnknown = {},
                    excludeInfrastructure = true,
                    onToggleExcludeInfrastructure = {},
                    onlyOnline = false,
                    onToggleOnlyOnline = {},
                    onlyDirect = false,
                    onToggleOnlyDirect = {},
                    showIgnored = false,
                    onToggleShowIgnored = {},
                    ignoredNodeCount = 2,
                    excludeMqtt = false,
                    onToggleExcludeMqtt = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun ExpressiveSectionPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExpressiveSection(title = "Radio Configuration") {
                    Text("Setting 1", modifier = Modifier.padding(8.dp))
                    Text("Setting 2", modifier = Modifier.padding(8.dp))
                }
                ExpressiveSection(title = "Display Settings") {
                    Text("Brightness: 80%", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@MultiPreview
@Composable
fun ThemePickerDialogPreview() {
    AppTheme(isSystemInDarkTheme()) { Surface { ThemePickerDialog(onClickTheme = {}, onDismiss = {}) } }
}

@MultiPreview
@Composable
fun HomoglyphSettingPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomoglyphSetting(homoglyphEncodingEnabled = true, onToggle = {})
                HomoglyphSetting(homoglyphEncodingEnabled = false, onToggle = {})
            }
        }
    }
}

@MultiPreview
@Composable
fun NotificationSectionPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                NotificationSection(
                    messagesEnabled = true,
                    onToggleMessages = {},
                    nodeEventsEnabled = false,
                    onToggleNodeEvents = {},
                    lowBatteryEnabled = true,
                    onToggleLowBattery = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun WarningDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            WarningDialog(
                icon = MeshtasticIcons.Warning,
                title = "Factory Reset",
                text = { Text("All settings will be erased. This cannot be undone.") },
                onDismiss = {},
                onConfirm = {},
            )
        }
    }
}

@MultiPreview
@Composable
fun NodeActionButtonPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NodeActionButton(title = "Reboot", enabled = true, icon = MeshtasticIcons.Warning, onClick = {})
                NodeActionButton(title = "Shutdown", enabled = false, onClick = {})
            }
        }
    }
}

@MultiPreview
@Composable
fun LoadingOverlayPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Empty state:", style = MaterialTheme.typography.labelSmall)
                LoadingOverlay(state = ResponseState.Empty)
                Text("Loading state:", style = MaterialTheme.typography.labelSmall)
                LoadingOverlay(state = ResponseState.Loading(total = 5, completed = 2))
            }
        }
    }
}

@MultiPreview
@Composable
fun MapReportingPreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                MapReportingPreference(
                    mapReportingEnabled = true,
                    shouldReportLocation = true,
                    positionPrecision = 14,
                    publishIntervalSecs = 3600,
                    enabled = true,
                )
            }
        }
    }
}
