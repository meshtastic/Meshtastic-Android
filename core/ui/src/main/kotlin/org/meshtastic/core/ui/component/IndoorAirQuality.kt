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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.air_quality_icon
import org.meshtastic.core.strings.close
import org.meshtastic.core.strings.indoor_air_quality_iaq
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.ThumbUp
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.theme.IAQColors.IAQDangerouslyPolluted
import org.meshtastic.core.ui.theme.IAQColors.IAQExcellent
import org.meshtastic.core.ui.theme.IAQColors.IAQExtremelyPolluted
import org.meshtastic.core.ui.theme.IAQColors.IAQGood
import org.meshtastic.core.ui.theme.IAQColors.IAQHeavilyPolluted
import org.meshtastic.core.ui.theme.IAQColors.IAQLightlyPolluted
import org.meshtastic.core.ui.theme.IAQColors.IAQModeratelyPolluted
import org.meshtastic.core.ui.theme.IAQColors.IAQSeverelyPolluted

@Suppress("MagicNumber")
enum class Iaq(val color: Color, val description: String, val range: IntRange) {
    Excellent(IAQExcellent, "Excellent", 0..50),
    Good(IAQGood, "Good", 51..100),
    LightlyPolluted(IAQLightlyPolluted, "Lightly Polluted", 101..150),
    ModeratelyPolluted(IAQModeratelyPolluted, "Moderately Polluted", 151..200),
    HeavilyPolluted(IAQHeavilyPolluted, "Heavily Polluted", 201..300),
    SeverelyPolluted(IAQSeverelyPolluted, "Severely Polluted", 301..400),
    ExtremelyPolluted(IAQExtremelyPolluted, "Extremely Polluted", 401..500),
    DangerouslyPolluted(IAQDangerouslyPolluted, "Dangerously Polluted", 501..Int.MAX_VALUE),
}

fun getIaq(iaq: Int): Iaq? = when {
    iaq == Int.MIN_VALUE -> null
    iaq in Iaq.Excellent.range -> Iaq.Excellent
    iaq in Iaq.Good.range -> Iaq.Good
    iaq in Iaq.LightlyPolluted.range -> Iaq.LightlyPolluted
    iaq in Iaq.ModeratelyPolluted.range -> Iaq.ModeratelyPolluted
    iaq in Iaq.HeavilyPolluted.range -> Iaq.HeavilyPolluted
    iaq in Iaq.SeverelyPolluted.range -> Iaq.SeverelyPolluted
    iaq in Iaq.ExtremelyPolluted.range -> Iaq.ExtremelyPolluted
    else -> Iaq.DangerouslyPolluted
}

private fun getIaqDescriptionWithRange(iaqEnum: Iaq): String = if (iaqEnum.range.last == Int.MAX_VALUE) {
    "${iaqEnum.description} (${iaqEnum.range.first}+)"
} else {
    "${iaqEnum.description} (${iaqEnum.range.first}-${iaqEnum.range.last})"
}

enum class IaqDisplayMode {
    Pill,
    Dot,
    Text,
    Gauge,
    Gradient,
}

@Suppress("LongMethod", "UnusedPrivateProperty")
@Composable
fun IndoorAirQuality(iaq: Int?, displayMode: IaqDisplayMode = IaqDisplayMode.Pill) {
    if (iaq == null || iaq == Int.MIN_VALUE) {
        return
    }
    var isLegendOpen by remember { mutableStateOf(false) }
    val iaqEnum = getIaq(iaq)
    val gradient = Brush.linearGradient(colors = Iaq.entries.map { it.color })

    if (iaqEnum != null) {
        Column {
            when (displayMode) {
                IaqDisplayMode.Pill -> {
                    Box(
                        modifier =
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(iaqEnum.color)
                            .width(125.dp)
                            .height(30.dp)
                            .clickable { isLegendOpen = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp).align(Alignment.CenterStart),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "IAQ $iaq", color = Color.White, fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector =
                                if (iaqEnum.range.first < 100) MeshtasticIcons.ThumbUp else MeshtasticIcons.Warning,
                                contentDescription = stringResource(Res.string.air_quality_icon),
                                tint = Color.White,
                            )
                        }
                    }
                }

                IaqDisplayMode.Dot -> {
                    Column(modifier = Modifier.clickable { isLegendOpen = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "$iaq")
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(modifier = Modifier.size(10.dp).background(iaqEnum.color, shape = CircleShape))
                        }
                    }
                }

                IaqDisplayMode.Text -> {
                    Text(
                        text = getIaqDescriptionWithRange(iaqEnum),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { isLegendOpen = true },
                    )
                }

                IaqDisplayMode.Gauge -> {
                    CircularProgressIndicator(
                        progress = { iaq / 500f },
                        modifier = Modifier.size(60.dp).clickable { isLegendOpen = true },
                        strokeWidth = 8.dp,
                        color = iaqEnum.color,
                    )
                    Text(text = "${iaqEnum.description}")
                }

                IaqDisplayMode.Gradient -> {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.clickable { isLegendOpen = true },
                    ) {
                        LinearProgressIndicator(
                            progress = { iaq / 500f },
                            modifier = Modifier.fillMaxWidth().height(20.dp),
                            color = iaqEnum.color,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = iaqEnum.description, fontSize = 12.sp)
                    }
                }
            }
            if (isLegendOpen) {
                MeshtasticDialog(
                    onDismiss = { isLegendOpen = false },
                    dismissText = stringResource(Res.string.close),
                    title = stringResource(Res.string.indoor_air_quality_iaq),
                    text = { IAQScale() },
                )
            }
        }
    }
}

// Assuming Iaq is an enum class with color and description properties
// and that it conforms to CaseIterable.
// Replace with your actual implementation

@Composable
fun IAQScale(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
        Spacer(modifier = Modifier.height(16.dp))
        for (iaq in Iaq.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(20.dp, 15.dp).clip(RoundedCornerShape(5.dp)).background(iaq.color))
                Spacer(modifier = Modifier.width(8.dp))
                Text(getIaqDescriptionWithRange(iaq), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IAQScalePreview() {
    IAQScale()
}

@Suppress("LongMethod")
@Preview(showBackground = true)
@Composable
private fun IndoorAirQualityPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pill", style = MaterialTheme.typography.titleLarge)
        Row {
            IndoorAirQuality(iaq = 6)
            IndoorAirQuality(iaq = 51)
        }
        Row {
            IndoorAirQuality(iaq = 101)
            IndoorAirQuality(iaq = 201)
        }
        Row {
            IndoorAirQuality(iaq = 350)
            IndoorAirQuality(iaq = 351)
        }

        Text("Dot", style = MaterialTheme.typography.titleLarge)
        Row {
            IndoorAirQuality(iaq = 6, displayMode = IaqDisplayMode.Dot)
            IndoorAirQuality(iaq = 51, displayMode = IaqDisplayMode.Dot)
            IndoorAirQuality(iaq = 101, displayMode = IaqDisplayMode.Dot)
            IndoorAirQuality(iaq = 201, displayMode = IaqDisplayMode.Dot)
            IndoorAirQuality(iaq = 350, displayMode = IaqDisplayMode.Dot)
            IndoorAirQuality(iaq = 351, displayMode = IaqDisplayMode.Dot)
        }

        Text("Text", style = MaterialTheme.typography.titleLarge)
        Row {
            IndoorAirQuality(iaq = 6, displayMode = IaqDisplayMode.Text)
            IndoorAirQuality(iaq = 51, displayMode = IaqDisplayMode.Text)
            IndoorAirQuality(iaq = 101, displayMode = IaqDisplayMode.Text)
        }
        Row {
            IndoorAirQuality(iaq = 201, displayMode = IaqDisplayMode.Text)
            IndoorAirQuality(iaq = 350, displayMode = IaqDisplayMode.Text)
            IndoorAirQuality(iaq = 500, displayMode = IaqDisplayMode.Text)
        }

        Text("Gauge", style = MaterialTheme.typography.titleLarge)
        Row {
            IndoorAirQuality(iaq = 6, displayMode = IaqDisplayMode.Gauge)
            IndoorAirQuality(iaq = 51, displayMode = IaqDisplayMode.Gauge)
            IndoorAirQuality(iaq = 101, displayMode = IaqDisplayMode.Gauge)
            IndoorAirQuality(iaq = 151, displayMode = IaqDisplayMode.Gauge)
        }
        Row {
            IndoorAirQuality(iaq = 201, displayMode = IaqDisplayMode.Gauge)
            IndoorAirQuality(iaq = 251, displayMode = IaqDisplayMode.Gauge)
            IndoorAirQuality(iaq = 301, displayMode = IaqDisplayMode.Gauge)
            IndoorAirQuality(iaq = 351, displayMode = IaqDisplayMode.Gauge)
        }
        Row {
            IndoorAirQuality(iaq = 401, displayMode = IaqDisplayMode.Gauge)
            IndoorAirQuality(iaq = 500, displayMode = IaqDisplayMode.Gauge)
        }

        Text("Gradient", style = MaterialTheme.typography.titleLarge)
        IndoorAirQuality(iaq = 6, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 51, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 101, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 201, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 351, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 401, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 500, displayMode = IaqDisplayMode.Gradient)
    }
}
