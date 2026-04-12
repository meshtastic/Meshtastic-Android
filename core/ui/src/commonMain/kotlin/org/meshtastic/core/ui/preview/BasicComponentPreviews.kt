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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.MeshtasticTheme

/**
 * Multi-preview annotation for testing components in light and dark themes. Usage: @MultiPreview @Composable fun
 * ComponentPreview() { ... }
 */
@androidx.compose.ui.tooling.preview.Preview(name = "Light", showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
annotation class MultiPreview

@MultiPreview
@Composable
fun ButtonVariantsPreview() {
    MeshtasticTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Filled Button", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                Button(onClick = {}) { Text("Click Me") }

                Text(
                    "Elevated Button",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                ElevatedButton(onClick = {}) { Text("Elevated") }

                Text(
                    "Filled Tonal Button",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                FilledTonalButton(onClick = {}) { Text("Tonal") }

                Text(
                    "Outlined Button",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedButton(onClick = {}) { Text("Outlined") }

                Text(
                    "Button with Icon",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Button(onClick = {}) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                    Text("Add Item")
                }
            }
        }
    }
}

@MultiPreview
@Composable
fun TextVariantsPreview() {
    MeshtasticTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Display Large", style = androidx.compose.material3.MaterialTheme.typography.displayLarge)
                Text("Display Medium", style = androidx.compose.material3.MaterialTheme.typography.displayMedium)
                Text("Display Small", style = androidx.compose.material3.MaterialTheme.typography.displaySmall)

                Text(
                    "Headline Large",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text("Headline Medium", style = androidx.compose.material3.MaterialTheme.typography.headlinesMedium)
                Text("Headline Small", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)

                Text(
                    "Title Large",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text("Title Medium", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text("Title Small", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)

                Text(
                    "Body Large",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text("Body Medium", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Text("Body Small", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)

                Text(
                    "Label Large",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text("Label Medium", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                Text("Label Small", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@MultiPreview
@Composable
fun IconsPreview() {
    MeshtasticTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Icon Buttons", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)

                androidx.compose.material3.IconButton(onClick = {}) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }

                androidx.compose.material3.IconButton(onClick = {}) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
