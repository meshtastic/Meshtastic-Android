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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun NodeActionButton(
    modifier: Modifier = Modifier,
    title: String,
    enabled: Boolean,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val mediumHeight = ButtonDefaults.MediumContainerHeight
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    Button(
        onClick = { onClick() },
        shapes = ButtonDefaults.shapesFor(mediumHeight),
        enabled = enabled,
        modifier = modifier.then(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(mediumHeight)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint ?: LocalContentColor.current,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = title, style = ButtonDefaults.textStyleFor(mediumHeight), modifier = Modifier.weight(1f))
        }
    }
}
