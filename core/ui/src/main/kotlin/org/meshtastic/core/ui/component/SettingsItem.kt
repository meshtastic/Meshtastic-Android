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

/** A clickable settings button item. */
@Composable
fun SettingsItem(
    text: String,
    textColor: Color = LocalContentColor.current,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = LocalContentColor.current,
    trailingIcon: ImageVector? = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    trailingIconTint: Color = LocalContentColor.current,
    onClick: () -> Unit,
) {
    SettingsItem(
        text = text,
        textColor = textColor,
        enabled = enabled,
        leadingIcon = leadingIcon,
        leadingIconTint = leadingIconTint,
        trailingContent = { trailingIcon.Icon(trailingIconTint) },
        onClick = onClick,
    )
}

/** A clickable settings button item. */
@Composable
fun SettingsItem(
    text: String,
    textColor: Color = LocalContentColor.current,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = LocalContentColor.current,
    trailingContent: @Composable (() -> Unit),
    onClick: () -> Unit,
) {
    ClickableWrapper(enabled = enabled, onClick = onClick) {
        Content(
            leading = { leadingIcon.Icon(leadingIconTint) },
            text = text,
            textColor = textColor,
            trailing = trailingContent,
        )
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
    ClickableWrapper(enabled = enabled, onClick = onClick) {
        Content(
            leading = { leadingIcon.Icon(leadingIconTint) },
            text = text,
            textColor = textColor,
            trailing = { Switch(checked = checked, enabled = enabled, onCheckedChange = null) },
        )
    }
}

/** A settings detail item. */
@Composable
fun SettingsItemDetail(
    text: String,
    textColor: Color = LocalContentColor.current,
    icon: ImageVector? = null,
    iconTint: Color = LocalContentColor.current,
    trailingText: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable ColumnScope.() -> Unit = {
        Content(
            leading = { icon.Icon(iconTint) },
            text = text,
            textColor = textColor,
            trailing = { trailingText?.let { Text(
                text = it,
                style = MaterialTheme.typography.titleMedium
            ) } },
        )
    }

    if (onClick != null) {
        ClickableWrapper(enabled = enabled, onClick = onClick, content = content)
    } else {
        Column(content = content)
    }
}

/** A clickable Card wrapper used for all clickable settings items. */
@Composable
private fun ClickableWrapper(enabled: Boolean, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors =
        CardDefaults.cardColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        content = content,
    )
}

/** The row content to display for a settings item. */
@Composable
private fun Content(leading: @Composable () -> Unit, text: String, textColor: Color, trailing: @Composable () -> Unit) {
    ListItem(
        modifier = Modifier.padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text = text, color = textColor) },
        leadingContent = { leading() },
        trailingContent = { trailing() },
    )
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
    AppTheme { SettingsItemDetail(text = "Text 1", icon = Icons.Rounded.Android, trailingText = "Text2") }
}
