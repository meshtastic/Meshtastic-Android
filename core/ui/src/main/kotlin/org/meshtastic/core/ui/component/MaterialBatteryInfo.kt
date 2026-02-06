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
package org.meshtastic.core.ui.component

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.unknown
import org.meshtastic.core.ui.icon.BatteryEmpty
import org.meshtastic.core.ui.icon.BatteryUnknown
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusOrange
import org.meshtastic.core.ui.theme.StatusColors.StatusRed

private const val FORMAT = "%d%%"
private const val SIZE_ICON = 16

@Suppress("MagicNumber", "LongMethod")
@Composable
fun MaterialBatteryInfo(
    modifier: Modifier = Modifier,
    level: Int?,
    voltage: Float? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val levelString = FORMAT.format(level)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (level == null || level < 0) {
            Icon(
                modifier = Modifier.size(SIZE_ICON.dp),
                imageVector = MeshtasticIcons.BatteryUnknown,
                tint = contentColor.copy(alpha = 0.65f),
                contentDescription = stringResource(Res.string.unknown),
            )
        } else if (level > 100) {
            Icon(
                modifier = Modifier.size(SIZE_ICON.dp).rotate(90f),
                imageVector = Icons.Rounded.Power,
                tint = contentColor.copy(alpha = 0.65f),
                contentDescription = levelString,
            )

            Text(
                text = "PWR",
                color = contentColor.copy(alpha = 0.95f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
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
                tint = contentColor.copy(alpha = 0.65f),
                contentDescription = levelString,
            )

            Text(
                text = levelString,
                color = contentColor.copy(alpha = 0.95f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
            )
        }
        voltage
            ?.takeIf { it > 0 }
            ?.let {
                Text(
                    text = "%.2fV".format(it),
                    color = contentColor.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                )
            }
    }
}

class BatteryInfoPreviewParameterProvider : PreviewParameterProvider<Pair<Int?, Float?>> {
    override val values: Sequence<Pair<Int?, Float?>>
        get() =
            sequenceOf(
                85 to 3.7F,
                2 to 3.7F,
                12 to 3.7F,
                28 to 3.7F,
                50 to 3.7F,
                101 to 4.9F,
                null to 4.5F,
                null to null,
            )
}

@PreviewLightDark
@Composable
fun MaterialBatteryInfoPreview(@PreviewParameter(BatteryInfoPreviewParameterProvider::class) info: Pair<Int?, Float?>) {
    AppTheme { MaterialBatteryInfo(level = info.first, voltage = info.second) }
}
