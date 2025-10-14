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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
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
 * A settings item with an optional [leadingIcon], headline [text], optional [supportingText], and optional
 * [trailingIcon].
 */
@Composable
fun SettingsItem(
    text: String,
    supportingText: String? = null,
    textColor: Color = LocalContentColor.current,
    supportingTextColor: Color = LocalContentColor.current,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = LocalContentColor.current,
    trailingIcon: ImageVector? = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    trailingIconTint: Color = LocalContentColor.current,
    onClick: (() -> Unit)? = null,
) {
    BasicSettingsItem(
        text = text,
        textColor = textColor,
        supportingText = supportingText,
        supportingTextColor = supportingTextColor,
        enabled = enabled,
        leadingIcon = leadingIcon,
        leadingIconTint = leadingIconTint,
        trailingContent = trailingIcon.icon(trailingIconTint),
        onClick = onClick,
    )
}

/**
 * The foundational settings item. It supports a [leadingIcon] (optional), headline [text] and [supportingText]
 * (optional), and a [trailingContent] composable (optional).
 */
@Composable
fun BasicSettingsItem(
    text: String,
    textColor: Color = LocalContentColor.current,
    supportingText: String? = null,
    supportingTextColor: Color = LocalContentColor.current,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = LocalContentColor.current,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable ColumnScope.() -> Unit = {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(text = text, color = textColor) },
            supportingContent = supportingText?.let { { Text(text = it, color = supportingTextColor) } },
            leadingContent = leadingIcon.icon(leadingIconTint),
            trailingContent = trailingContent,
        )
    }

    // Ensures that click ripples are disabled for non-clickable list items
    if (onClick != null) {
        Card(
            onClick = onClick,
            enabled = enabled,
            colors =
            CardDefaults.cardColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
            content = content,
        )
    } else {
        Column(content = content)
    }
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
    BasicSettingsItem(
        text = text,
        textColor = textColor,
        enabled = enabled,
        leadingIcon = leadingIcon,
        leadingIconTint = leadingIconTint,
        trailingContent = { Switch(checked = checked, enabled = enabled, onCheckedChange = null) },
        onClick = onClick,
    )
}

@Composable
private fun ImageVector?.icon(tint: Color = LocalContentColor.current): @Composable (() -> Unit)? =
    this?.let { { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(24.dp), tint = tint) } }

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
    AppTheme { SettingsItem(text = "Text 1", leadingIcon = Icons.Rounded.Android, supportingText = "Text2") }
}
