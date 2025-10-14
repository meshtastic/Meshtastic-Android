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

import android.content.ClipData
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.meshtastic.core.ui.theme.AppTheme
import timber.log.Timber

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
    copyable: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = LocalContentColor.current,
    trailingIcon: ImageVector? = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    trailingIconTint: Color = LocalContentColor.current,
    onClick: (() -> Unit)? = null,
) {
    val clipboard: Clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

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
        onLongClick =
        if (supportingText != null && copyable) {
            {
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", supportingText)))
                    Timber.d("Copied to clipboard")
                }
            }
        } else {
            null
        },
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
    onLongClick: (() -> Unit)? = null,
) {
    val modifier =
        if (onLongClick != null || onClick != null) {
            Modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick ?: {})
        } else {
            Modifier
        }

    ListItem(
        modifier = modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text = text, color = textColor) },
        supportingContent = supportingText?.let { { Text(text = it, color = supportingTextColor) } },
        leadingContent = leadingIcon.icon(leadingIconTint),
        trailingContent = trailingContent,
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
    AppTheme {
        SettingsItem(
            text = "Text 1",
            leadingIcon = Icons.Rounded.Android,
            supportingText = "Text2",
            trailingIcon = null,
        )
    }
}
