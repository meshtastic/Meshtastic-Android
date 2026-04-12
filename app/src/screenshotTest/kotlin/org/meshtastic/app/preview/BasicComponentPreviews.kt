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

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.icon.Add
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme

/**
 * Multi-preview annotation for testing components in light and dark themes.
 *
 * Usage: `@MultiPreview @Composable fun ComponentPreview() { ... }`
 *
 * This annotation is Android-only (uses [Configuration.UI_MODE_NIGHT_YES]) and must live in an Android source set, not
 * `commonMain`.
 */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class MultiPreview

@MultiPreview
@Composable
fun ButtonVariantsPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Filled Button", style = MaterialTheme.typography.labelMedium)
                Button(onClick = {}) { Text("Click Me") }

                Text(
                    "Elevated Button",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                ElevatedButton(onClick = {}) { Text("Elevated") }

                Text(
                    "Filled Tonal Button",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                FilledTonalButton(onClick = {}) { Text("Tonal") }

                Text(
                    "Outlined Button",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedButton(onClick = {}) { Text("Outlined") }

                Text(
                    "Button with Icon",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Button(onClick = {}) {
                    Icon(MeshtasticIcons.Add, contentDescription = "Add")
                    Text("Add Item")
                }
            }
        }
    }
}

@MultiPreview
@Composable
fun TextVariantsPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Display Large", style = MaterialTheme.typography.displayLarge)
                Text("Display Medium", style = MaterialTheme.typography.displayMedium)
                Text("Display Small", style = MaterialTheme.typography.displaySmall)

                Text(
                    "Headline Large",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text("Headline Medium", style = MaterialTheme.typography.headlineMedium)
                Text("Headline Small", style = MaterialTheme.typography.headlineSmall)

                Text(
                    "Title Large",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text("Title Medium", style = MaterialTheme.typography.titleMedium)
                Text("Title Small", style = MaterialTheme.typography.titleSmall)

                Text("Body Large", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                Text("Body Medium", style = MaterialTheme.typography.bodyMedium)
                Text("Body Small", style = MaterialTheme.typography.bodySmall)

                Text(
                    "Label Large",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text("Label Medium", style = MaterialTheme.typography.labelMedium)
                Text("Label Small", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@MultiPreview
@Composable
fun IconsPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Icon Buttons", style = MaterialTheme.typography.labelMedium)

                IconButton(onClick = {}) { Icon(MeshtasticIcons.Add, contentDescription = "Add") }

                IconButton(onClick = {}) { Icon(MeshtasticIcons.Delete, contentDescription = "Delete") }
            }
        }
    }
}
