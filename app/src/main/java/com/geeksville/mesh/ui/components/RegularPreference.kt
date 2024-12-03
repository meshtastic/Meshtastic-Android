/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun RegularPreference(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
    trailingIcon: ImageVector? = null,
) {
    RegularPreference(
        title = title,
        subtitle = AnnotatedString(text = subtitle),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        summary = summary,
        trailingIcon = trailingIcon,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegularPreference(
    title: String,
    subtitle: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
    trailingIcon: ImageVector? = null,
) {
    val color = if (enabled) {
        MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(all = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.body1,
                    color = if (enabled) {
                        Color.Unspecified
                    } else {
                        MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                    },
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.body1,
                    color = color,
                )
            }
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = "trailingIcon",
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .wrapContentWidth(Alignment.End),
                    tint = color,
                )
            }
        }
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.body2,
                color = color,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegularPreferencePreview() {
    RegularPreference(
        title = "Advanced settings",
        subtitle = "Text2",
        onClick = { },
    )
}