/*
 * Copyright (c) 2025 Meshtastic LLC
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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.AppTheme

/**
 * A clickable settings item with optional supporting text and trailing content. Defaults to a trailing arrow icon if no
 * custom trailing content is provided.
 */
@Composable
fun SettingsItem(
    text: String,
    supportingText: String? = null,
    textColor: Color = LocalContentColor.current,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = LocalContentColor.current,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val finalTrailingContent: @Composable (() -> Unit) =
        trailingContent ?: { Icons.AutoMirrored.Rounded.KeyboardArrowRight.Icon(LocalContentColor.current) }

    SettingsListItem(
        text = text,
        textColor = textColor,
        enabled = enabled,
        onClick = onClick,
        leadingContent = { leadingIcon.Icon(leadingIconTint) },
        supportingContent = { supportingText?.let { Text(text = it, style = MaterialTheme.typography.titleMedium) } },
        trailingContent = finalTrailingContent,
    )
}

/** A toggleable settings switch item. */
@Composable
fun SettingsItemSwitch(
    checked: Boolean,
    text: String,
    textColor: Color = LocalContentColor.current,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = LocalContentColor.current,
    onClick: () -> Unit,
) {
    SettingsListItem(
        text = text,
        textColor = textColor,
        enabled = enabled,
        onClick = onClick,
        leadingContent = { leadingIcon.Icon(leadingIconTint) },
        trailingContent = { Switch(checked = checked, enabled = enabled, onCheckedChange = null) },
    )
}

/** A settings detail item. */
@Composable
fun SettingsItemDetail(
    text: String,
    supportingText: String?,
    textColor: Color = LocalContentColor.current,
    icon: ImageVector? = null,
    iconTint: Color = LocalContentColor.current,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    SettingsListItem(
        text = text,
        textColor = textColor,
        enabled = enabled,
        onClick = onClick,
        leadingContent = { icon.Icon(iconTint) },
        supportingContent = {
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor, // Detail style explicitly sets color
                )
            }
        },
        trailingContent = {},
    )
}

/**
 * Base composable for all settings screen list items. It handles the Material3 [ListItem] structure and the conditional
 * click wrapper.
 */
@Composable
private fun SettingsListItem(
    text: String,
    textColor: Color,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    leadingContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val listItemContent: @Composable ColumnScope.() -> Unit = {
        ListItem(
            modifier = Modifier.padding(horizontal = 8.dp),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(text = text, color = textColor) },
            supportingContent = { SelectionContainer { supportingContent?.invoke() } },
            leadingContent = leadingContent,
            trailingContent = trailingContent,
        )
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            enabled = enabled,
            colors =
            CardDefaults.cardColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
            content = listItemContent,
        )
    } else {
        Column(content = listItemContent)
    }
}

@Composable
private fun ImageVector?.Icon(tint: Color = LocalContentColor.current) =
    this?.let { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(24.dp), tint = tint) }

@Preview(showBackground = true)
@Composable
private fun SettingsItemPreview() {
    AppTheme { SettingsItem(text = "Text", leadingIcon = Icons.Rounded.Android, enabled = true) {} }
}

@Preview(showBackground = true)
@Composable
private fun SettingsItemDisabledPreview() {
    AppTheme { SettingsItem(text = "Text", leadingIcon = Icons.Rounded.Android, enabled = false) {} }
}

@Preview(showBackground = true)
@Composable
private fun SettingsItemSwitchPreview() {
    AppTheme { SettingsItemSwitch(text = "Text", leadingIcon = Icons.Rounded.Android, checked = true) {} }
}

@Preview(showBackground = true)
@Composable
private fun SettingsItemDetailPreview() {
    AppTheme { SettingsItemDetail(text = "Text 1", icon = Icons.Rounded.Android, supportingText = "Text2") }
}
