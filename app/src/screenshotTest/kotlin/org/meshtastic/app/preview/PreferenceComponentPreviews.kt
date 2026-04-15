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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.PreferenceFooter
import org.meshtastic.core.ui.component.RegularPreference
import org.meshtastic.core.ui.component.SliderPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TextDividerPreference
import org.meshtastic.core.ui.theme.AppTheme

@MultiPreview
@Composable
fun SwitchPreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                SwitchPreference(title = "Enable notifications", checked = true, enabled = true, onCheckedChange = {})
                SwitchPreference(
                    title = "Power saving mode",
                    summary = "Reduces update frequency",
                    checked = false,
                    enabled = true,
                    onCheckedChange = {},
                )
                SwitchPreference(title = "Disabled option", checked = true, enabled = false, onCheckedChange = {})
            }
        }
    }
}

@MultiPreview
@Composable
fun SliderPreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                SliderPreference(
                    title = "Hop limit",
                    enabled = true,
                    items = listOf(1 to "1", 2 to "2", 3 to "3", 4 to "4", 5 to "5"),
                    selectedValue = 3,
                    onValueChange = {},
                )
                SliderPreference(
                    title = "Disabled slider",
                    enabled = false,
                    items = listOf(1 to "Low", 2 to "Medium", 3 to "High"),
                    selectedValue = 2,
                    onValueChange = {},
                    summary = "Currently locked",
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun RegularPreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                RegularPreference(title = "Device settings", subtitle = "Configure radio parameters", onClick = {})
                RegularPreference(title = "Disabled setting", subtitle = "Not available", onClick = {}, enabled = false)
            }
        }
    }
}

@MultiPreview
@Composable
fun EditTextPreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                EditTextPreference(
                    title = "Device name",
                    value = "My Meshtastic Node",
                    enabled = true,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default,
                    keyboardActions = KeyboardActions(),
                    onValueChanged = {},
                )
                EditTextPreference(
                    title = "Transmit power",
                    value = 27,
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
fun DropDownPreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                DropDownPreference(
                    title = "Region",
                    enabled = true,
                    items = listOf("US" to "United States", "EU" to "Europe", "JP" to "Japan"),
                    selectedItem = "US",
                    onItemSelected = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun PreferenceCategoryPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                PreferenceCategory(text = "Radio Configuration")
                PreferenceCategory(text = "Display Settings") {
                    SwitchPreference(title = "Auto-rotate", checked = true, enabled = true, onCheckedChange = {})
                }
            }
        }
    }
}

@MultiPreview
@Composable
fun TextDividerPreferencePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                TextDividerPreference(title = "Advanced Settings")
                TextDividerPreference(title = "Disabled Section", enabled = false)
            }
        }
    }
}

@MultiPreview
@Composable
fun PreferenceFooterPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                PreferenceFooter(enabled = true, negativeText = "Reset", positiveText = "Save")
                PreferenceFooter(enabled = false, negativeText = "Reset", positiveText = "Save")
            }
        }
    }
}
