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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.AppTheme

@Composable
fun RegularPreference(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
    trailingIcon: ImageVector? = null,
    dropdownMenu: @Composable () -> Unit = {},
) {
    RegularPreference(
        title = title,
        subtitle = AnnotatedString(text = subtitle),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        summary = summary,
        trailingIcon = trailingIcon,
        dropdownMenu = dropdownMenu,
    )
}

@Composable
fun RegularPreference(
    title: String,
    subtitle: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
    trailingIcon: ImageVector? = null,
    dropdownMenu: @Composable () -> Unit = {},
) {
    ListItem(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shapes = ListItemDefaults.shapes(shape = RectangleShape),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = subtitle, style = MaterialTheme.typography.bodyLarge)
                if (trailingIcon != null) {
                    Box {
                        Icon(
                            imageVector = trailingIcon,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 8.dp).wrapContentWidth(Alignment.End),
                        )
                        dropdownMenu()
                    }
                }
            }
        },
        supportingContent = summary?.let { { Text(text = it, style = MaterialTheme.typography.bodyMedium) } },
        content = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
    )
}

@Preview(showBackground = true)
@Composable
fun RegularPreferencePreview() {
    AppTheme { RegularPreference(title = "Advanced settings", subtitle = "Text2", onClick = {}) }
}
