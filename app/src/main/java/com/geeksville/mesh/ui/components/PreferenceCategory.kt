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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceCategory(
    text: String,
    modifier: Modifier = Modifier,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Text(
        text,
        modifier = modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp, end = 16.dp),
        style = MaterialTheme.typography.h6,
    )
    if (content != null) {
        Surface(
            modifier = modifier.padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = 1.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProvideTextStyle(MaterialTheme.typography.body1) {
                    content()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceCategoryPreview() {
    PreferenceCategory(
        text = "Advanced settings"
    )
}