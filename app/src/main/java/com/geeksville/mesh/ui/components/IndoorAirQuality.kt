package com.geeksville.mesh.ui.components

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
import androidx.compose.material.AlertDialog
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geeksville.mesh.R

enum class Iaq(val color: Color, val description: String) {
    Excellent(Color.Green, "Excellent"),
    Good(Color.Green, "Good"),
    LightlyPolluted(Color.Yellow, "Lightly Polluted"),
    ModeratelyPolluted(Color.Orange, "Moderately Polluted"),
    HeavilyPolluted(Color.Red, "Heavily Polluted"),
    SeverelyPolluted(Color.Purple, "Severely Polluted"),
    ExtremelyPolluted(Color.Purple, "Extremely Polluted"),
    DangerouslyPolluted(Color.Brown, "Dangerously Polluted")
}

val Color.Companion.Mint: Color
    get() = Color(0xFF98FB98)
val Color.Companion.Purple: Color
    get() = Color(0xFF800080)
val Color.Companion.Brown: Color
    get() = Color(0xFFA52A2A)
val Color.Companion.Orange: Color
    get() = Color(0xFFFFA500)

@Suppress("MagicNumber")
fun getIaq(iaq: Int): Iaq {
    return when {
        iaq <= 50 -> Iaq.Excellent
        iaq <= 100 -> Iaq.Good
        iaq <= 150 -> Iaq.LightlyPolluted
        iaq <= 200 -> Iaq.ModeratelyPolluted
        iaq <= 300 -> Iaq.HeavilyPolluted
        iaq <= 400 -> Iaq.SeverelyPolluted
        iaq <= 500 -> Iaq.ExtremelyPolluted
        else -> Iaq.DangerouslyPolluted
    }
}

enum class IaqDisplayMode {
    Pill, Dot, Text, Gauge, Gradient
}

@Suppress("LongMethod", "UnusedPrivateProperty")
@Composable
fun IndoorAirQuality(iaq: Int, displayMode: IaqDisplayMode = IaqDisplayMode.Pill) {
    var isLegendOpen by remember { mutableStateOf(false) }
    val iaqEnum = getIaq(iaq)
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color.Green, Color.Mint, Color.Yellow, Color.Orange, Color.Red,
            Color.Purple, Color.Purple, Color.Brown, Color.Brown, Color.Brown, Color.Brown
        )
    )

    Column {
        when (displayMode) {
            IaqDisplayMode.Pill -> {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(iaqEnum.color)
                        .width(125.dp)
                        .height(30.dp)
                        .clickable { isLegendOpen = true }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "IAQ $iaq",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (iaq < 100) Icons.Default.ThumbUp else Icons.Filled.Warning,
                            contentDescription = "AQI Icon",
                            tint = Color.White
                        )
                    }
                }
            }

            IaqDisplayMode.Dot -> {
                Column(modifier = Modifier.clickable { isLegendOpen = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "$iaq")
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(iaqEnum.color, shape = CircleShape)
                        )
                    }
                }
            }

            IaqDisplayMode.Text -> {
                Text(
                    text = iaqEnum.description,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { isLegendOpen = true }
                )
            }

            IaqDisplayMode.Gauge -> {
                CircularProgressIndicator(
                    progress = iaq / 500f,
                    modifier = Modifier
                        .size(60.dp)
                        .clickable { isLegendOpen = true },
                    strokeWidth = 8.dp,
                    color = iaqEnum.color
                )
                Text(text = "$iaq")
            }

            IaqDisplayMode.Gradient -> {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.clickable { isLegendOpen = true }
                ) {
                    LinearProgressIndicator(
                        progress = iaq / 500f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp),
                        color = iaqEnum.color,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = iaqEnum.description, fontSize = 12.sp)
                }
            }
        }
        if (isLegendOpen) {
            AlertDialog(
                onDismissRequest = { isLegendOpen = false },
                shape = RoundedCornerShape(16.dp),
                backgroundColor = MaterialTheme.colors.background,
                text = {
                    IAQScale()
                },
                confirmButton = {
                    TextButton(onClick = { isLegendOpen = false }) {
                        Text(text = stringResource(id = R.string.close))
                    }
                }
            )
        }
    }
}

// Assuming Iaq is an enum class with color and description properties
// and that it conforms to CaseIterable.
// Replace with your actual implementation

@Composable
fun IAQScale(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Indoor Air Quality (IAQ)",
            style = MaterialTheme.typography.h6.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        for (iaq in Iaq.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp, 15.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(iaq.color)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(iaq.description, style = MaterialTheme.typography.body2)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
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
        Text("Pill", style = MaterialTheme.typography.h6)
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

        Text("Dot", style = MaterialTheme.typography.h6)
        Row {
            IndoorAirQuality(iaq = 6, displayMode = IaqDisplayMode.Dot)
            IndoorAirQuality(iaq = 51, displayMode = IaqDisplayMode.Dot)
            IndoorAirQuality(iaq = 101, displayMode = IaqDisplayMode.Dot)
            IndoorAirQuality(iaq = 201, displayMode = IaqDisplayMode.Dot)
            IndoorAirQuality(iaq = 350, displayMode = IaqDisplayMode.Dot)
            IndoorAirQuality(iaq = 351, displayMode = IaqDisplayMode.Dot)
        }

        Text("Text", style = MaterialTheme.typography.h6)
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

        Text("Gauge", style = MaterialTheme.typography.h6)
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

        Text("Gradient", style = MaterialTheme.typography.h6)
        IndoorAirQuality(iaq = 6, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 51, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 101, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 201, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 351, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 401, displayMode = IaqDisplayMode.Gradient)
        IndoorAirQuality(iaq = 500, displayMode = IaqDisplayMode.Gradient)
    }
}
