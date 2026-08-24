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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.meshtastic.core.ui.icon.Android
import org.meshtastic.core.ui.icon.ChevronRight
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.createClipEntry

/**
 * A list item with an optional [leadingIcon], headline [text], optional [supportingText], and optional [trailingIcon].
 */
@Composable
fun ListItem(
    text: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    textColor: Color = Color.Unspecified,
    supportingTextColor: Color = Color.Unspecified,
    copyable: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = Color.Unspecified,
    trailingIcon: ImageVector? = MeshtasticIcons.ChevronRight,
    trailingIconTint: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    val clipboard: Clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    BasicListItem(
        text = text,
        modifier = modifier,
        textColor = textColor,
        supportingText = supportingText,
        supportingTextColor = supportingTextColor,
        enabled = enabled,
        leadingIcon = leadingIcon,
        leadingIconTint = leadingIconTint,
        trailingContent = trailingIcon.icon(trailingIconTint),
        onClick = onClick,
        onLongClick =
        if (!supportingText.isNullOrBlank() && copyable) {
            { coroutineScope.launch { clipboard.setClipEntry(createClipEntry(supportingText)) } }
        } else {
            null
        },
    )
}

/** A toggleable switch list item. */
@Composable
fun SwitchListItem(
    checked: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = Color.Unspecified,
) {
    BasicListItem(
        text = text,
        modifier = modifier,
        textColor = textColor,
        enabled = enabled,
        leadingIcon = leadingIcon,
        leadingIconTint = leadingIconTint,
        trailingContent = { Switch(checked = checked, enabled = enabled, onCheckedChange = null) },
        onClick = onClick,
    )
}

/**
 * The foundational list item. It supports a [leadingIcon] (optional), headline [text] and [supportingText] (optional),
 * and a [trailingContent] composable (optional).
 *
 * This is a core component that should facilitate most list item use cases. Please carefully consider if modifying this
 * is really necessary before doing so.
 *
 * Uses the M3 Expressive interactive [ListItem] overload which provides built-in shape morphing on press/hover and
 * proper disabled styling.
 *
 * @see [LinkedCoordinatesItem] for example usage
 */
@Composable
fun BasicListItem(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    supportingText: String? = null,
    supportingTextColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconTint: Color = Color.Unspecified,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    if (onClick != null) {
        ListItem(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            onLongClick = onLongClick,
            shapes = ListItemDefaults.shapes(shape = RectangleShape),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = leadingIcon.icon(leadingIconTint),
            trailingContent = trailingContent,
            supportingContent = supportingText?.let { { Text(text = it, color = supportingTextColor) } },
            content = { Text(text = text, color = textColor) },
        )
    } else {
        ListItem(
            modifier = modifier,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(text = text, color = textColor) },
            supportingContent = supportingText?.let { { Text(text = it, color = supportingTextColor) } },
            leadingContent = leadingIcon.icon(leadingIconTint),
            trailingContent = trailingContent,
        )
    }
}

@Composable
fun ImageVector?.icon(tint: Color = Color.Unspecified): @Composable (() -> Unit)? = this?.let {
    {
        val resolvedTint = if (tint == Color.Unspecified) LocalContentColor.current else tint
        Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(24.dp), tint = resolvedTint)
    }
}

@Preview(showBackground = true)
@Composable
fun ListItemPreview() {
    AppTheme { ListItem(text = "Text", leadingIcon = MeshtasticIcons.Android, enabled = true) {} }
}

@Preview(showBackground = true)
@Composable
fun ListItemDisabledPreview() {
    AppTheme { ListItem(text = "Text", leadingIcon = MeshtasticIcons.Android, enabled = false) {} }
}

@Preview(showBackground = true)
@Composable
fun SwitchListItemPreview() {
    AppTheme { SwitchListItem(text = "Text", leadingIcon = MeshtasticIcons.Android, checked = true, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ListItemPreviewSupportingText() {
    AppTheme {
        ListItem(text = "Text 1", leadingIcon = MeshtasticIcons.Android, supportingText = "Text2", trailingIcon = null)
    }
}
