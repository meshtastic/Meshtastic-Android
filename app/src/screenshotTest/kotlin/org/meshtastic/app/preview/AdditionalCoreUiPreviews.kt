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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.core.ui.component.BitwisePreference
import org.meshtastic.core.ui.component.ChannelItem
import org.meshtastic.core.ui.component.EditBase64Preference
import org.meshtastic.core.ui.component.EditIPv4Preference
import org.meshtastic.core.ui.component.EditPasswordPreference
import org.meshtastic.core.ui.component.EmptyDetailPlaceholder
import org.meshtastic.core.ui.component.PositionPrecisionPreference
import org.meshtastic.core.ui.component.PreferenceDivider
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Message
import org.meshtastic.core.ui.theme.AppTheme

@MultiPreview
@Composable
fun ChannelItemPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChannelItem(index = 0, title = "Primary", enabled = true) { Text("Default channel") }
                ChannelItem(index = 1, title = "Admin", enabled = true) { Text("Admin channel") }
                ChannelItem(index = 2, title = "Disabled", enabled = false) { Text("Disabled channel") }
            }
        }
    }
}

@MultiPreview
@Composable
fun EmptyDetailPlaceholderPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EmptyDetailPlaceholder(icon = MeshtasticIcons.Message, title = "Select a conversation")
                EmptyDetailPlaceholder(icon = MeshtasticIcons.Map, title = "No node selected")
            }
        }
    }
}

@MultiPreview
@Composable
fun PreferenceDividerPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Section above")
                PreferenceDivider()
                Text("Section below")
            }
        }
    }
}

@MultiPreview
@Composable
fun EditPasswordPreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditPasswordPreference(
                    title = "Admin key",
                    value = "secretpassword",
                    maxSize = 32,
                    enabled = true,
                    keyboardActions = KeyboardActions(),
                    onValueChanged = {},
                )
                EditPasswordPreference(
                    title = "Disabled key",
                    value = "locked",
                    maxSize = 32,
                    enabled = false,
                    keyboardActions = KeyboardActions(),
                    onValueChanged = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun EditIPv4PreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditIPv4Preference(
                    title = "MQTT server",
                    value = 0xC0A80164.toInt(), // 192.168.1.100
                    enabled = true,
                    keyboardActions = KeyboardActions(),
                    onValueChanged = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun EditBase64PreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditBase64Preference(
                    title = "Channel PSK",
                    value = "AQ==".encodeUtf8(),
                    enabled = true,
                    keyboardActions = KeyboardActions(),
                    onValueChange = {},
                )
                EditBase64Preference(
                    title = "Read-only key",
                    value = "dGVzdA==".encodeUtf8(),
                    enabled = true,
                    readOnly = true,
                    keyboardActions = KeyboardActions(),
                    onValueChange = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun PositionPrecisionPreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PositionPrecisionPreference(value = 13, enabled = true, onValueChanged = {})
            }
        }
    }
}

@MultiPreview
@Composable
fun BitwisePreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BitwisePreference(
                    title = "Position flags",
                    value = 811,
                    enabled = true,
                    items =
                    listOf(
                        1 to "Altitude",
                        2 to "Altitude MSL",
                        4 to "Geo sep",
                        8 to "DOP",
                        16 to "HDOP",
                        32 to "VDOP",
                    ),
                    onItemSelected = {},
                    summary = "Select position data to include",
                )
            }
        }
    }
}
