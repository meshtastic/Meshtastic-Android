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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Settings
import org.meshtastic.core.ui.theme.AppTheme

@MultiPreview
@Composable
fun TitledCardPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                TitledCard(title = "Node Information") {
                    Text("Device: TBEAM", modifier = Modifier.padding(8.dp))
                    Text("Firmware: 2.7.14", modifier = Modifier.padding(8.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                TitledCard(title = null) { Text("Card without title", modifier = Modifier.padding(8.dp)) }
            }
        }
    }
}

@MultiPreview
@Composable
fun ListItemVariantsPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    text = "Device Settings",
                    supportingText = "Configure radio parameters",
                    leadingIcon = MeshtasticIcons.Settings,
                )
                ListItem(text = "About", supportingText = "Version 2.7.14", leadingIcon = MeshtasticIcons.Info)
                ListItem(text = "Disabled Item", supportingText = "Not available", enabled = false)
            }
        }
    }
}

@MultiPreview
@Composable
fun SwitchListItemPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth()) {
                SwitchListItem(
                    checked = true,
                    text = "Enable GPS",
                    onClick = {},
                    leadingIcon = MeshtasticIcons.Settings,
                )
                SwitchListItem(checked = false, text = "Power saving", onClick = {})
                SwitchListItem(checked = true, text = "Disabled toggle", onClick = {}, enabled = false)
            }
        }
    }
}

@MultiPreview
@Composable
fun BasicListItemPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth()) {
                BasicListItem(text = "Node name", supportingText = "Mickey Mouse", leadingIcon = MeshtasticIcons.Info)
                BasicListItem(text = "Channel", supportingText = "LongFast")
            }
        }
    }
}
