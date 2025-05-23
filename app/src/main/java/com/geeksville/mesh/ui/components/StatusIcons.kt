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

package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NoCell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@Composable
fun StatusIcons(
    isFavorite: Boolean,
    unmessageable: Boolean
) {
    Row(
        modifier = Modifier.padding(5.dp)
    ) {
        if (isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.favorite),
                modifier = Modifier
                    .size(25.dp), // Smaller size for badge
                tint = Color(color = 0xFFFEC30A)
            )
        }
        if (unmessageable) {
            Icon(
                imageVector = Icons.Outlined.NoCell,
                contentDescription = stringResource(R.string.unmessageable),
                modifier = Modifier
                    .size(25.dp), // Smaller size for badge
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview
@Composable
fun StatusIconsPreview() {
    StatusIcons(isFavorite = true, unmessageable = true)
}
