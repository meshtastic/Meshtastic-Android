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
package org.meshtastic.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    title: String,
    summary: String = "",
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    padding: PaddingValues? = null,
    containerColor: Color? = null,
    loading: Boolean = false,
) {
    val defaultColors = ListItemDefaults.colors()

    @Suppress("DEPRECATION")
    val currentColors =
        if (enabled) {
            defaultColors
        } else {
            defaultColors.copy(
                headlineColor = defaultColors.contentColor.copy(alpha = 0.5f),
                supportingTextColor = defaultColors.supportingContentColor.copy(alpha = 0.5f),
            )
        }
            .let { if (containerColor != null) it.copy(containerColor = containerColor) else it }

    ListItem(
        colors = currentColors,
        modifier =
        (padding?.let { Modifier.padding(it) } ?: modifier).toggleable(
            value = checked,
            enabled = enabled,
            onValueChange = onCheckedChange,
        ),
        trailingContent = {
            AnimatedContent(targetState = loading) { loading ->
                if (loading) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Switch(enabled = enabled, checked = checked, onCheckedChange = null)
                }
            }
        },
        supportingContent = {
            if (summary.isNotEmpty()) {
                Text(text = summary)
            }
        },
        headlineContent = { Text(text = title) },
    )
}

@Preview(showBackground = true)
@Composable
private fun SwitchPreferencePreview() {
    SwitchPreference(title = "Setting", checked = true, enabled = true, onCheckedChange = {})
}
