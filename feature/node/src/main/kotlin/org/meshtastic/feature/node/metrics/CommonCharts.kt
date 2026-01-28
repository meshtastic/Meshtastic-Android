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
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.close
import org.meshtastic.core.strings.info
import org.meshtastic.core.strings.logs
import org.meshtastic.core.strings.rssi
import org.meshtastic.core.strings.snr
import org.meshtastic.feature.node.model.TimeFrame
import java.text.DateFormat

object CommonCharts {
    val DATE_TIME_FORMAT: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    val TIME_MINUTE_FORMAT: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    val DATE_TIME_MINUTE_FORMAT: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    const val MS_PER_SEC = 1000L
    const val MAX_PERCENT_VALUE = 100f

    /**
     * Gets the Material 3 primary color with optional opacity adjustment.
     *
     * @param alpha The alpha/opacity value (0f-1f). Defaults to 1f (fully opaque).
     * @return Color based on current theme's primary color.
     */
    @Composable
    fun getMaterial3PrimaryColor(alpha: Float = 1f): Color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)

    /**
     * Gets the Material 3 secondary color with optional opacity adjustment.
     *
     * @param alpha The alpha/opacity value (0f-1f). Defaults to 1f (fully opaque).
     * @return Color based on current theme's secondary color.
     */
    @Composable
    fun getMaterial3SecondaryColor(alpha: Float = 1f): Color = MaterialTheme.colorScheme.secondary.copy(alpha = alpha)

    /**
     * Gets the Material 3 tertiary color with optional opacity adjustment.
     *
     * @param alpha The alpha/opacity value (0f-1f). Defaults to 1f (fully opaque).
     * @return Color based on current theme's tertiary color.
     */
    @Composable
    fun getMaterial3TertiaryColor(alpha: Float = 1f): Color = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha)

    /**
     * Gets the Material 3 error color with optional opacity adjustment.
     *
     * @param alpha The alpha/opacity value (0f-1f). Defaults to 1f (fully opaque).
     * @return Color based on current theme's error color.
     */
    @Composable
    fun getMaterial3ErrorColor(alpha: Float = 1f): Color = MaterialTheme.colorScheme.error.copy(alpha = alpha)

    /**
     * Gets the appropriate time formatter based on the selected TimeFrame for better chart readability.
     * - 1 Hour: Just time (HH:MM)
     * - 24 Hours: Time (HH:MM)
     * - 1 Week: Date + Time
     * - 1 Month: Date only
     * - All: Date only
     *
     * @param timeFrame The selected TimeFrame
     * @return DateFormat appropriate for the timeframe
     */
    fun getTimeFormatterForTimeFrame(timeFrame: TimeFrame): DateFormat = when (timeFrame) {
        TimeFrame.ONE_HOUR,
        TimeFrame.TWENTY_FOUR_HOURS,
        -> TIME_MINUTE_FORMAT
        TimeFrame.FORTY_EIGHT_HOURS,
        TimeFrame.ONE_WEEK,
        TimeFrame.TWO_WEEKS,
        TimeFrame.FOUR_WEEKS,
        -> DATE_TIME_MINUTE_FORMAT
        TimeFrame.MAX -> DateFormat.getDateInstance(DateFormat.SHORT)
    }
}

data class LegendData(
    val nameRes: StringResource,
    val color: Color,
    val isLine: Boolean = false,
    val environmentMetric: Environment? = null,
)

@Composable
fun ChartHeader(amount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$amount ${stringResource(Res.string.logs)}",
            modifier = Modifier.wrapContentWidth(),
            style = TextStyle(fontWeight = FontWeight.Bold),
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
        )
    }
}

/**
 * Creates the legend that identifies the colors used for the graph.
 *
 * @param legendData A list containing the `LegendData` to build the labels.
 * @param promptInfoDialog Executes when the user presses the info icon.
 */
@Composable
fun Legend(legendData: List<LegendData>, displayInfoIcon: Boolean = true, promptInfoDialog: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.weight(1f))
        legendData.forEachIndexed { index, data ->
            LegendLabel(text = stringResource(data.nameRes), color = data.color, isLine = data.isLine)

            if (index != legendData.lastIndex) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        if (displayInfoIcon) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.Info,
                modifier = Modifier.clickable { promptInfoDialog() },
                contentDescription = stringResource(Res.string.info),
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Displays a dialog with information about the legend items.
 *
 * @param pairedRes A list of `Pair`s containing (term, definition).
 * @param onDismiss Executes when the user presses the close button.
 */
@Composable
fun LegendInfoDialog(pairedRes: List<Pair<StringResource, StringResource>>, onDismiss: () -> Unit) {
    AlertDialog(
        title = {
            Text(
                text = stringResource(Res.string.info),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column {
                for (pair in pairedRes) {
                    Text(
                        text = stringResource(pair.first),
                        style = TextStyle(fontWeight = FontWeight.Bold),
                        textDecoration = TextDecoration.Underline,
                    )
                    Text(text = stringResource(pair.second), style = TextStyle.Default)

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun LegendLabel(text: String, color: Color, isLine: Boolean = false) {
    Canvas(modifier = Modifier.size(4.dp)) {
        if (isLine) {
            drawLine(
                color = color,
                start = Offset(x = 0f, y = size.height / 2f),
                end = Offset(x = 16f, y = size.height / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        } else {
            drawCircle(color = color)
        }
    }
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = MaterialTheme.typography.labelLarge.fontSize,
    )
}

@Preview
@Composable
private fun LegendPreview() {
    val data =
        listOf(
            LegendData(nameRes = Res.string.rssi, color = Color.Red),
            LegendData(nameRes = Res.string.snr, color = Color.Green),
        )
    Legend(legendData = data, promptInfoDialog = {})
}
