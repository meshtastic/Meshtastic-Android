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

package org.meshtastic.feature.node.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.compass_bearing
import org.meshtastic.core.strings.compass_bearing_na
import org.meshtastic.core.strings.compass_distance
import org.meshtastic.core.strings.compass_uncertainty
import org.meshtastic.core.strings.compass_uncertainty_unknown
import org.meshtastic.core.strings.compass_location_disabled
import org.meshtastic.core.strings.compass_no_location_fix
import org.meshtastic.core.strings.compass_no_location_permission
import org.meshtastic.core.strings.compass_no_magnetometer
import org.meshtastic.core.strings.compass_title
import org.meshtastic.core.strings.exchange_position
import org.meshtastic.core.strings.last_position_update
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.compass.CompassUiState
import org.meshtastic.feature.node.compass.CompassWarning
import kotlin.io.path.Path
import kotlin.math.cos
import kotlin.math.sin

private const val TARGET_STROKE_WIDTH = 8f
private const val TARGET_DOT_RADIUS = 12f
private const val TARGET_OUTER_OFFSET = 20f
private const val TARGET_INNER_INSET = 12f
private const val CANVAS_NORTH_OFFSET_DEGREES = 90.0
private const val DIAL_WIDTH_FRACTION = 0.66f
private const val LABEL_RADIUS_OFFSET = 36f
private const val QUARTER_RADIUS_OFFSET = 18f
private const val LABEL_TEXT_SIZE = 64f
private const val QUARTER_TICK_INSET = 24f
private const val EIGHTH_TICK_INSET = 12f
private const val QUARTER_TICK_STROKE = 3f

@Composable
fun CompassSheetContent(
    uiState: CompassUiState,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRequestPosition: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(uiState.isAligned) {
        if (uiState.isAligned) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(Res.string.compass_title), style = MaterialTheme.typography.headlineSmall)
        Text(text = uiState.targetName, style = MaterialTheme.typography.titleMedium, color = uiState.targetColor)

        CompassDial(
            heading = uiState.heading,
            bearing = uiState.bearing,
            angularErrorDeg = uiState.angularErrorDeg,
            modifier = Modifier.fillMaxWidth(DIAL_WIDTH_FRACTION).aspectRatio(1f),
            markerColor = uiState.targetColor
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.compass_distance, uiState.distanceText ?: "--"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text =
                    uiState.bearingText?.let { stringResource(Res.string.compass_bearing, it) }
                        ?: stringResource(Res.string.compass_bearing_na),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text =
                    uiState.errorRadiusText?.let { radius ->
                        val angle = uiState.angularErrorDeg?.let { "%.0f°".format(it) } ?: "?"
                        stringResource(Res.string.compass_uncertainty, radius, angle)
                    } ?: stringResource(Res.string.compass_uncertainty_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        uiState.lastUpdateText?.let {
            Text(
                text = stringResource(Res.string.last_position_update) + ": $it",
                style = MaterialTheme.typography.bodyMedium,
            )
            // Quick way to re-request a fresh fix without leaving the compass sheet
            Button(onClick = onRequestPosition, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(Res.string.exchange_position))
            }
        }

        if (uiState.warnings.isNotEmpty()) {
            WarningList(
                warnings = uiState.warnings,
                onRequestPermission = onRequestLocationPermission,
                onOpenLocationSettings = onOpenLocationSettings,
            )
        }
    }
}

@Composable
private fun WarningList(
    warnings: List<CompassWarning>,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        warnings.forEach { warning ->
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = warningText(warning),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (warnings.contains(CompassWarning.NO_LOCATION_PERMISSION)) {
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(Res.string.compass_no_location_permission))
            }
        } else if (warnings.contains(CompassWarning.LOCATION_DISABLED)) {
            Button(onClick = onOpenLocationSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(Res.string.compass_location_disabled))
            }
        }
    }
}

@Composable
private fun warningText(warning: CompassWarning): String = when (warning) {
    CompassWarning.NO_MAGNETOMETER -> stringResource(Res.string.compass_no_magnetometer)
    CompassWarning.NO_LOCATION_PERMISSION -> stringResource(Res.string.compass_no_location_permission)
    CompassWarning.LOCATION_DISABLED -> stringResource(Res.string.compass_location_disabled)
    CompassWarning.NO_LOCATION_FIX -> stringResource(Res.string.compass_no_location_fix)
}
@Composable
private fun CompassDial(
    heading: Float?,
    bearing: Float?,
    angularErrorDeg: Float?,
    modifier: Modifier = Modifier,
    markerColor: Color = Color(0xFF2196F3),
) {
    val compassRoseColor = MaterialTheme.colorScheme.primary
    val tickColor = MaterialTheme.colorScheme.onSurface
    val cardinalColor = MaterialTheme.colorScheme.primary
    val degreeTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val northPointerColor = Color.Red
    val headingIndicatorColor = MaterialTheme.colorScheme.secondary

    val textMeasurer = rememberTextMeasurer()
    val cardinalStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold)
    val degreeStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.88f
        val ringStroke = 3.dp.toPx()

        val currentHeading = heading ?: 0f
        val currentBearing = bearing

        rotate(-currentHeading, center) {
            // Compass circles
            drawCircle(
                color = compassRoseColor,
                radius = radius,
                center = center,
                style = Stroke(width = ringStroke)
            )
            drawCircle(
                color = compassRoseColor.copy(alpha = 0.35f),
                radius = radius * 0.85f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Tick marks
            for (deg in 0 until 360 step 5) {
                val isCardinal = deg % 90 == 0
                val isMajor = deg % 30 == 0
                val tickLength = when {
                    isCardinal -> radius * 0.14f
                    isMajor -> radius * 0.09f
                    else -> radius * 0.045f
                }
                val tickWidth = when {
                    isCardinal -> 3.dp.toPx()
                    isMajor -> 2.dp.toPx()
                    else -> 1.dp.toPx()
                }

                val angle = Math.toRadians(deg.toDouble())
                val outer = Offset(
                    center.x + radius * sin(angle).toFloat(),
                    center.y - radius * cos(angle).toFloat()
                )
                val inner = Offset(
                    center.x + (radius - tickLength) * sin(angle).toFloat(),
                    center.y - (radius - tickLength) * cos(angle).toFloat()
                )

                drawLine(
                    color = if (deg == 0) northPointerColor else tickColor,
                    start = inner,
                    end = outer,
                    strokeWidth = tickWidth,
                    cap = StrokeCap.Round
                )
            }

            // Compass rose center
            drawCompassRoseCenter(
                center = center,
                size = radius * 0.13f,
                color = compassRoseColor,
                northColor = northPointerColor
            )

            // Cardinal labels (moved closer to center)
            val cardinalRadius = radius * 0.48f
            val cardinals = listOf(
                Triple("N", 0, northPointerColor),
                Triple("E", 90, cardinalColor),
                Triple("S", 180, cardinalColor),
                Triple("W", 270, cardinalColor)
            )

            for ((label, deg, color) in cardinals) {
                val angle = Math.toRadians(deg.toDouble())
                val x = center.x + cardinalRadius * sin(angle).toFloat()
                val y = center.y - cardinalRadius * cos(angle).toFloat()

                val layout = textMeasurer.measure(label, style = cardinalStyle.copy(color = color))

                withTransform({ rotate(currentHeading, Offset(x, y)) }) {
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x - layout.size.width / 2f,
                            y - layout.size.height / 2f
                        )
                    )
                }
            }

            // Degree labels
            val degRadius = radius * 0.72f
            for (d in 0 until 360 step 30) {
                val angle = Math.toRadians(d.toDouble())
                val x = center.x + degRadius * sin(angle).toFloat()
                val y = center.y - degRadius * cos(angle).toFloat()

                val layout = textMeasurer.measure(
                    d.toString(),
                    style = degreeStyle.copy(color = degreeTextColor)
                )

                withTransform({ rotate(currentHeading, Offset(x, y)) }) {
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x - layout.size.width / 2f,
                            y - layout.size.height / 2f
                        )
                    )
                }
            }

            // Bearing marker (adjust bearing by current heading because the canvas is rotated)
            val bearingForDraw = currentBearing
            if (bearingForDraw != null && angularErrorDeg != null && angularErrorDeg > 0f) {
                val arcRadius = radius * 0.82f
                val startAngleNorth = bearingForDraw - angularErrorDeg
                val sweep = angularErrorDeg * 2f
                val faint = markerColor.copy(alpha = 0.18f)
                // Canvas drawArc: 0deg = 3 o'clock, +clockwise. Convert north=0° to that space.
                val startAngleCanvas = (startAngleNorth - 90f).normalizeDegrees()

                // Filled wedge for the cone shading.
                drawArc(
                    color = faint,
                    startAngle = startAngleCanvas,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2, arcRadius * 2)
                )

                // Cone edge lines for clarity
                val edgeRadius = arcRadius
                val startRad = Math.toRadians(startAngleNorth.toDouble())
                val endRad = Math.toRadians((startAngleNorth + sweep).toDouble())
                val startEnd =
                    Offset(
                        center.x + edgeRadius * sin(startRad).toFloat(),
                        center.y - edgeRadius * cos(startRad).toFloat()
                    )
                val endEnd =
                    Offset(
                        center.x + edgeRadius * sin(endRad).toFloat(),
                        center.y - edgeRadius * cos(endRad).toFloat()
                    )
                drawLine(color = faint, start = center, end = startEnd, strokeWidth = 6f, cap = StrokeCap.Round)
                drawLine(color = faint, start = center, end = endEnd, strokeWidth = 6f, cap = StrokeCap.Round)
            }
            if (bearingForDraw != null) {
                val angle = Math.toRadians(bearingForDraw.toDouble())
                val dot = Offset(
                    center.x + (radius * 0.95f) * sin(angle).toFloat(),
                    center.y - (radius * 0.95f) * cos(angle).toFloat()
                )
                drawCircle(color = markerColor, radius = 10.dp.toPx(), center = dot)
            }
        }

        // Heading indicator as a simple line
        if (heading != null) {
            val headingEnd = Offset(center.x, center.y - radius * 0.78f)
            drawLine(
                color = headingIndicatorColor,
                start = center,
                end = headingEnd,
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawCompassRoseCenter(center: Offset, size: Float, color: Color, northColor: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        lineTo(center.x + size * 0.35f, center.y)
        lineTo(center.x, center.y + size * 0.35f)
        lineTo(center.x - size * 0.35f, center.y)
        close()
    }

    drawPath(path, color.copy(alpha = 0.5f))
    drawCircle(color = color, radius = size * 0.25f, center = center)
}

private fun Float.normalizeDegrees(): Float {
    val normalized = this % 360f
    return if (normalized < 0f) normalized + 360f else normalized
}


private fun buildLabelPaint(outline: Color): android.graphics.Paint = android.graphics.Paint().apply {
    color = outline.toArgb()
    textSize = LABEL_TEXT_SIZE
    textAlign = android.graphics.Paint.Align.CENTER
    isAntiAlias = true
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
}

@Preview(showBackground = true)
@Composable
private fun CompassSheetPreview() {
    AppTheme {
        CompassSheetContent(
            uiState =
            CompassUiState(
                targetName = "Sample Node",
                heading = 45f,
                bearing = 90f,
                distanceText = "1.2 km",
                bearingText = "90°",
                lastUpdateText = "0h 3m 10s ago",
                errorRadiusText = "150 m",
                angularErrorDeg = 12f,
                isAligned = false,
            ),
            onRequestLocationPermission = {},
            onOpenLocationSettings = {},
            onRequestPosition = {},
        )
    }
}
