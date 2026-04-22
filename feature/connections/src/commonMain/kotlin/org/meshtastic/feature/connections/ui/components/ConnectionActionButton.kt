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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Shared "icon + label" button used throughout the Connections screen. Centralises M3 sizing conventions
 * ([ButtonDefaults.IconSize], [ButtonDefaults.IconSpacing], and the variant-appropriate `*WithIconContentPadding`) so
 * every scan / add / empty-state affordance renders identically.
 *
 * @param iconContentDescription accessibility label for the leading icon. Defaults to `null` because the visible [text]
 *   usually describes the action; override when the icon carries information the text does not.
 */
@Composable
fun ConnectionActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    style: ConnectionActionButtonStyle = ConnectionActionButtonStyle.Filled,
    enabled: Boolean = true,
    iconContentDescription: String? = null,
) {
    val content: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = iconContentDescription,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
    when (style) {
        ConnectionActionButtonStyle.Filled ->
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                content()
            }
        ConnectionActionButtonStyle.Tonal ->
            FilledTonalButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                content()
            }
        ConnectionActionButtonStyle.Outlined ->
            OutlinedButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                content()
            }
        ConnectionActionButtonStyle.Text ->
            TextButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
            ) {
                content()
            }
    }
}
