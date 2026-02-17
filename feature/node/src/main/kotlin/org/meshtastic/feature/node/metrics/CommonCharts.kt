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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.model.util.toDate
import org.meshtastic.core.model.util.toInstant
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.close
import org.meshtastic.core.strings.delete
import org.meshtastic.core.strings.info
import org.meshtastic.core.strings.rssi
import org.meshtastic.core.strings.snr
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import java.text.DateFormat
import kotlin.time.Duration.Companion.days

object CommonCharts {
    val DATE_TIME_FORMAT: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    val TIME_MINUTE_FORMAT: DateFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    val TIME_SECONDS_FORMAT: DateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM)
    val DATE_FORMAT: DateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
    const val MS_PER_SEC = 1000L
    const val MAX_PERCENT_VALUE = 100f
    const val SCROLL_BIAS = 0.5f

    /** Gets the Material 3 primary color with optional opacity adjustment. */
    @Composable
    fun getMaterial3PrimaryColor(alpha: Float = 1f): Color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)

    /** Gets the Material 3 secondary color with optional opacity adjustment. */
    @Composable
    fun getMaterial3SecondaryColor(alpha: Float = 1f): Color = MaterialTheme.colorScheme.secondary.copy(alpha = alpha)

    /** Gets the Material 3 tertiary color with optional opacity adjustment. */
    @Composable
    fun getMaterial3TertiaryColor(alpha: Float = 1f): Color = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha)

    /** Gets the Material 3 error color with optional opacity adjustment. */
    @Composable
    fun getMaterial3ErrorColor(alpha: Float = 1f): Color = MaterialTheme.colorScheme.error.copy(alpha = alpha)

    /** A dynamic [CartesianValueFormatter] that adjusts the time format based on the visible X range. */
    val dynamicTimeFormatter = CartesianValueFormatter { context, value, _ ->
        val date = (value * MS_PER_SEC.toDouble()).toLong().toInstant().toDate()
        val xLength = context.ranges.xLength
        val zoom = if (context is CartesianDrawingContext) context.zoom else 1f
        val visibleSpan = xLength / zoom

        when {
            visibleSpan <= TimeConstants.ONE_HOUR.inWholeSeconds -> TIME_SECONDS_FORMAT.format(date) // < 1 hour visible
            visibleSpan <= 2.days.inWholeSeconds -> TIME_MINUTE_FORMAT.format(date) // < 2 days visible
            visibleSpan <= 14.days.inWholeSeconds -> {
                // < 2 weeks visible: separate date and time with a newline
                val dateStr = DATE_FORMAT.format(date)
                val timeStr = TIME_MINUTE_FORMAT.format(date)
                "$dateStr\n$timeStr"
            }
            else -> DATE_FORMAT.format(date)
        }
    }
}

data class LegendData(
    val nameRes: StringResource,
    val color: Color,
    val isLine: Boolean = false,
    val environmentMetric: Environment? = null,
)

data class InfoDialogData(val titleRes: StringResource, val definitionRes: StringResource, val color: Color)

/** Creates the legend that identifies the colors used for the graph. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(legendData: List<LegendData>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        legendData.forEach { data ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                LegendLabel(text = stringResource(data.nameRes), color = data.color, isLine = data.isLine)
            }
        }
    }
}

/** Displays a dialog with information about the legend items. */
@Composable
fun LegendInfoDialog(infoData: List<InfoDialogData>, onDismiss: () -> Unit) {
    AlertDialog(
        icon = { Icon(imageVector = Icons.Rounded.Info, contentDescription = null) },
        title = {
            Text(
                text = stringResource(Res.string.info),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (item in infoData) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MetricIndicator(item.color)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(item.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = item.color,
                            )
                        }
                        Text(
                            text = stringResource(item.definitionRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.close), fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun LegendLabel(text: String, color: Color, isLine: Boolean = false) {
    Canvas(modifier = Modifier.size(height = 4.dp, width = if (isLine) 16.dp else 4.dp)) {
        if (isLine) {
            drawLine(
                color = color,
                start = Offset(x = 0f, y = size.height / 2f),
                end = Offset(x = size.width, y = size.height / 2f),
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
        fontSize = MaterialTheme.typography.labelSmall.fontSize,
    )
}

@Composable
fun MetricIndicator(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun DeleteItem(onClick: () -> Unit) {
    DropdownMenuItem(
        onClick = onClick,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MeshtasticIcons.Delete,
                    contentDescription = stringResource(Res.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MetricLogItem(icon: ImageVector, text: String, contentDescription: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 4.dp, horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun LegendPreview() {
    val data =
        listOf(
            LegendData(nameRes = Res.string.rssi, color = Color.Red),
            LegendData(nameRes = Res.string.snr, color = Color.Green),
        )
    Legend(legendData = data)
}
