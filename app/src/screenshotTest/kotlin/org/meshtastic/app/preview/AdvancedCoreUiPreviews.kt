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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.BottomSheetDialog
import org.meshtastic.core.ui.component.ElevationInfo
import org.meshtastic.core.ui.component.MenuFAB
import org.meshtastic.core.ui.component.MenuFABItem
import org.meshtastic.core.ui.component.SecurityIcon
import org.meshtastic.core.ui.component.SecurityState
import org.meshtastic.core.ui.component.SignedIntegerEditTextPreference
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Settings
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits

@MultiPreview
@Composable
fun ElevationInfoPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Metric:", style = MaterialTheme.typography.labelSmall)
                ElevationInfo(altitude = 1500, system = DisplayUnits.METRIC, suffix = "ASL")
                Text("Imperial:", style = MaterialTheme.typography.labelSmall)
                ElevationInfo(altitude = 450, system = DisplayUnits.IMPERIAL, suffix = "ASL")
                Text("Sea level:", style = MaterialTheme.typography.labelSmall)
                ElevationInfo(altitude = 0, system = DisplayUnits.METRIC, suffix = "ASL")
            }
        }
    }
}

@MultiPreview
@Composable
fun SignedIntegerEditTextPreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SignedIntegerEditTextPreference(
                    title = "Frequency Offset",
                    value = -125,
                    enabled = true,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
                    onValueChanged = {},
                    summary = "Current offset from center frequency",
                )
                SignedIntegerEditTextPreference(
                    title = "TX Power",
                    value = 30,
                    enabled = false,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
                    onValueChanged = {},
                    summary = "Disabled when using default",
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun AdaptiveTwoPanePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            AdaptiveTwoPane(
                first = {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("First Pane", style = MaterialTheme.typography.titleMedium)
                        Text("Node list content goes here")
                    }
                },
                second = {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Second Pane", style = MaterialTheme.typography.titleMedium)
                        Text("Node detail content goes here")
                    }
                },
            )
        }
    }
}

@MultiPreview
@Composable
fun BottomSheetDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface(modifier = Modifier.height(300.dp)) {
            BottomSheetDialog(onDismiss = {}) {
                Text(
                    "Bottom Sheet Title",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
                Text(
                    "This is the bottom sheet content area.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun MenuFABPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Collapsed:", style = MaterialTheme.typography.labelSmall)
                MenuFAB(
                    expanded = false,
                    onExpandedChange = {},
                    items =
                    listOf(
                        MenuFABItem(label = "Settings", icon = MeshtasticIcons.Settings, onClick = {}),
                        MenuFABItem(label = "Warning", icon = MeshtasticIcons.Warning, onClick = {}),
                    ),
                )
                Text("Expanded:", style = MaterialTheme.typography.labelSmall)
                MenuFAB(
                    expanded = true,
                    onExpandedChange = {},
                    items =
                    listOf(
                        MenuFABItem(label = "Settings", icon = MeshtasticIcons.Settings, onClick = {}),
                        MenuFABItem(label = "Warning", icon = MeshtasticIcons.Warning, onClick = {}),
                    ),
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun SecurityIconPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("All security states:", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecurityIcon(securityState = SecurityState.SECURE)
                    SecurityIcon(securityState = SecurityState.INSECURE_NO_PRECISE)
                    SecurityIcon(securityState = SecurityState.INSECURE_PRECISE_ONLY)
                    SecurityIcon(securityState = SecurityState.INSECURE_PRECISE_MQTT_WARNING)
                }
                Text("Boolean flags overload:", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecurityIcon(isLowEntropyKey = false)
                    SecurityIcon(isLowEntropyKey = true)
                    SecurityIcon(isLowEntropyKey = true, isPreciseLocation = true)
                    SecurityIcon(isLowEntropyKey = true, isPreciseLocation = true, isMqttEnabled = true)
                }
            }
        }
    }
}
