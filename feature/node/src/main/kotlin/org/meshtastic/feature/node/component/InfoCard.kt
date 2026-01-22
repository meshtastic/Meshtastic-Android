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
package org.meshtastic.feature.node.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InfoCard(
    text: String,
    value: String,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
    modifier: Modifier = Modifier,
    rotateIcon: Float = 0f,
) {
    Card(modifier = modifier, shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    modifier = Modifier.size(24.dp).thenIf(rotateIcon != 0f) { rotate(rotateIcon) },
                )
            }
            iconRes?.let {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = text,
                    modifier = Modifier.size(24.dp).thenIf(rotateIcon != 0f) { rotate(rotateIcon) },
                )
            }
            Column {
                Text(text = text, style = MaterialTheme.typography.labelSmall)
                Text(text = value, style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

@Composable
internal fun DrawableInfoCard(@DrawableRes iconRes: Int, text: String, value: String, rotateIcon: Float = 0f) {
    InfoCard(iconRes = iconRes, text = text, value = value, rotateIcon = rotateIcon)
}

inline fun Modifier.thenIf(precondition: Boolean, action: Modifier.() -> Modifier): Modifier =
    if (precondition) action() else this
