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

package com.geeksville.mesh.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ui.common.icons.BatteryEmpty
import com.geeksville.mesh.ui.common.icons.BatteryUnknown
import com.geeksville.mesh.ui.common.icons.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed

private const val FORMAT = "%d%%"
private const val SIZE_ICON = 20

@Suppress("MagicNumber")
@Composable
fun MaterialBatteryInfo(modifier: Modifier = Modifier, level: Int) {
    val levelString = FORMAT.format(level)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (level > 100) {
            Icon(
                modifier = Modifier.size(SIZE_ICON.dp).rotate(90f),
                imageVector = Icons.Rounded.Power,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = null,
            )

            Text(text = "PWD", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
        } else if (level < 0) {
            Icon(
                modifier = Modifier.size(SIZE_ICON.dp),
                imageVector = MeshtasticIcons.BatteryUnknown,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = null,
            )
        } else {
            // Map battery percentage to color
            val fillColor =
                when (level) {
                    in 0..19 -> MaterialTheme.colorScheme.StatusRed
                    in 20..39 -> MaterialTheme.colorScheme.StatusOrange
                    else -> MaterialTheme.colorScheme.StatusGreen
                }

            Icon(
                modifier =
                Modifier.size(SIZE_ICON.dp).drawBehind {
                    val insetVertical = size.height * .28f
                    val insetLeft = size.width * .11f
                    val insetRight = size.width * .22f

                    val availableWidth = size.width - (insetLeft + insetRight)
                    val availableHeight = size.height - (insetVertical * 2)

                    // Fill (grow from left to right)
                    val fillWidth = availableWidth * (level / 100f)

                    drawRect(
                        color = fillColor,
                        topLeft = Offset(insetLeft, insetVertical),
                        size = Size(fillWidth, availableHeight),
                    )
                },
                imageVector = MeshtasticIcons.BatteryEmpty,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = null,
            )

            Text(
                text = levelString,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

class BatteryLevelProvider : PreviewParameterProvider<Int> {
    override val values: Sequence<Int> = sequenceOf(-1, 19, 39, 90, 101)
}

@PreviewLightDark
@Composable
fun MaterialBatteryInfoPreview(@PreviewParameter(BatteryLevelProvider::class) batteryLevel: Int) {
    AppTheme { MaterialBatteryInfo(level = batteryLevel) }
}
