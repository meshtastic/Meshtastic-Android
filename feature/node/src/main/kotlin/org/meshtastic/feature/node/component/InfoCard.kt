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

import android.content.ClipData
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.copy

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun InfoCard(
    text: String,
    value: String,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
    modifier: Modifier = Modifier,
    rotateIcon: Float = 0f,
) {
    val clipboard: Clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val shape = MaterialTheme.shapes.medium
    val copyLabel = stringResource(Res.string.copy)

    Card(
        modifier =
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .combinedClickable(
                onLongClick = {
                    coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(text, value))) }
                },
                onLongClickLabel = copyLabel,
                onClick = {},
                role = Role.Button,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$text: $value" },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconModifier = Modifier.size(20.dp).thenIf(rotateIcon != 0f) { rotate(rotateIcon) }
            val iconTint = MaterialTheme.colorScheme.primary
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = iconModifier, tint = iconTint)
            }
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = iconModifier,
                    tint = iconTint,
                )
            }
            Column {
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
