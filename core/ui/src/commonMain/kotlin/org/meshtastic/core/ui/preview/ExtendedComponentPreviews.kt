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
package org.meshtastic.core.ui.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.MeshtasticTheme

@MultiPreview
@Composable
fun CardVariantsPreview() {
    MeshtasticTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Standard Card", style = MaterialTheme.typography.labelMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("This is a standard card", modifier = Modifier.padding(16.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Elevated Card", style = MaterialTheme.typography.labelMedium)
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text("This is an elevated card", modifier = Modifier.padding(16.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Outlined Card", style = MaterialTheme.typography.labelMedium)
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Text("This is an outlined card", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@MultiPreview
@Composable
fun InputFieldVariantsPreview() {
    MeshtasticTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Text Input Fields", style = MaterialTheme.typography.labelMedium)

                androidx.compose.material3.TextField(
                    value = "Sample input",
                    onValueChange = {},
                    label = { Text("Standard") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = "Sample input",
                    onValueChange = {},
                    label = { Text("Outlined") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.material3.TextField(
                    value = "Disabled input",
                    onValueChange = {},
                    label = { Text("Disabled") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun CheckboxAndTogglePreview() {
    MeshtasticTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Checkboxes", style = MaterialTheme.typography.labelMedium)

                androidx.compose.material3.Row(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Checkbox(checked = true, onCheckedChange = {})
                    androidx.compose.material3.Checkbox(checked = false, onCheckedChange = {})
                    androidx.compose.material3.Checkbox(checked = true, onCheckedChange = {}, enabled = false)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Radio Buttons", style = MaterialTheme.typography.labelMedium)

                androidx.compose.material3.Row(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.RadioButton(selected = true, onClick = {})
                    androidx.compose.material3.RadioButton(selected = false, onClick = {})
                    androidx.compose.material3.RadioButton(selected = false, onClick = {}, enabled = false)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Switches", style = MaterialTheme.typography.labelMedium)

                androidx.compose.material3.Row(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Switch(checked = true, onCheckedChange = {})
                    androidx.compose.material3.Switch(checked = false, onCheckedChange = {})
                    androidx.compose.material3.Switch(checked = true, onCheckedChange = {}, enabled = false)
                }
            }
        }
    }
}

@MultiPreview
@Composable
fun AlertDialogPreview() {
    MeshtasticTheme(isSystemInDarkTheme()) {
        Surface {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Dialog Title") },
                text = { Text("This is an example alert dialog with confirmatory action.") },
                confirmButton = { TextButton(onClick = {}) { Text("OK") } },
                dismissButton = { TextButton(onClick = {}) { Text("Cancel") } },
            )
        }
    }
}

@MultiPreview
@Composable
fun ChipVariantsPreview() {
    MeshtasticTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Chips", style = MaterialTheme.typography.labelMedium)

                androidx.compose.material3.AssistChip(onClick = {}, label = { Text("Assist Chip") })

                androidx.compose.material3.FilterChip(selected = true, onClick = {}, label = { Text("Filter Chip") })

                androidx.compose.material3.InputChip(selected = true, onClick = {}, label = { Text("Input Chip") })

                androidx.compose.material3.SuggestionChip(onClick = {}, label = { Text("Suggestion Chip") })
            }
        }
    }
}
