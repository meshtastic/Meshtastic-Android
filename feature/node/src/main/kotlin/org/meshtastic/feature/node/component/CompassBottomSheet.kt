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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.compass_bearing
import org.meshtastic.core.strings.compass_bearing_na
import org.meshtastic.core.strings.compass_distance
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

private const val NORTH_STROKE_WIDTH = 6f
private const val TARGET_STROKE_WIDTH = 10f
private const val HEADING_STROKE_WIDTH = 4f
private const val NORTH_OFFSET = 24f
private const val TARGET_OFFSET = 28f
private const val TARGET_DOT_RADIUS = 12f
private const val HEADING_OFFSET = 12f
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
            targetColor = uiState.targetColor,
            modifier = Modifier.fillMaxWidth(DIAL_WIDTH_FRACTION).aspectRatio(1f),
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
private fun CompassDial(heading: Float?, bearing: Float?, targetColor: Color, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    // Black line shows target bearing; red line shows north so users can align by rotating the phone.
    val relativeBearing = if (heading != null && bearing != null) bearing - heading else bearing

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = center
        val labelRadius = radius - LABEL_RADIUS_OFFSET
        val quarterRadius = radius - QUARTER_RADIUS_OFFSET
        val textPaint = buildLabelPaint(outline)

        drawCircle(color = background, radius = radius)
        drawCircle(
            color = outline,
            radius = radius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(HEADING_STROKE_WIDTH),
        )

        // North indicator
        drawLine(
            color = Color.Red,
            start = center,
            end = Offset(center.x, center.y - radius + NORTH_OFFSET),
            strokeWidth = NORTH_STROKE_WIDTH,
            cap = StrokeCap.Round,
        )

        // Target direction
        if (relativeBearing != null) {
            val angleRad = Math.toRadians((relativeBearing - CANVAS_NORTH_OFFSET_DEGREES).toDouble())
            val end =
                Offset(
                    x = center.x + (radius - TARGET_OFFSET) * kotlin.math.cos(angleRad).toFloat(),
                    y = center.y + (radius - TARGET_OFFSET) * kotlin.math.sin(angleRad).toFloat(),
                )

            drawLine(
                color = targetColor,
                start = center,
                end = end,
                strokeWidth = TARGET_STROKE_WIDTH,
                cap = StrokeCap.Round,
            )
            drawCircle(color = targetColor, radius = TARGET_DOT_RADIUS, center = end)
        }

        // Heading indicator ring
        heading?.let {
            val angleRad = Math.toRadians((it - CANVAS_NORTH_OFFSET_DEGREES).toDouble())
            val end =
                Offset(
                    x = center.x + (radius - HEADING_OFFSET) * kotlin.math.cos(angleRad).toFloat(),
                    y = center.y + (radius - HEADING_OFFSET) * kotlin.math.sin(angleRad).toFloat(),
                )
            drawLine(
                color = outline,
                start = center,
                end = end,
                strokeWidth = HEADING_STROKE_WIDTH,
                cap = StrokeCap.Round,
            )
        }

        drawCardinalLabels(center, labelRadius, textPaint)
        drawQuarterTicks(center, quarterRadius, outline)
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawCardinalLabels(center: Offset, labelRadius: Float, textPaint: android.graphics.Paint) {
    val labels = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)

    drawIntoCanvas { canvas ->
        labels.forEach { (label, degrees) ->
            val angleRad = Math.toRadians(degrees - CANVAS_NORTH_OFFSET_DEGREES)
            val x = center.x + labelRadius * kotlin.math.cos(angleRad).toFloat()
            val y = center.y + labelRadius * kotlin.math.sin(angleRad).toFloat() + textPaint.textSize / 3f
            canvas.nativeCanvas.drawText(label, x, y, textPaint)
        }
    }
}

@Suppress("MagicNumber")
private fun DrawScope.drawQuarterTicks(center: Offset, quarterRadius: Float, outline: Color) {
    val tickAngles = listOf(45.0, 135.0, 225.0, 315.0)
    tickAngles.forEach { degrees ->
        val angleRad = Math.toRadians(degrees - CANVAS_NORTH_OFFSET_DEGREES)
        val start =
            Offset(
                x = center.x + (quarterRadius - QUARTER_TICK_INSET) * kotlin.math.cos(angleRad).toFloat(),
                y = center.y + (quarterRadius - QUARTER_TICK_INSET) * kotlin.math.sin(angleRad).toFloat(),
            )
        val end =
            Offset(
                x = center.x + quarterRadius * kotlin.math.cos(angleRad).toFloat(),
                y = center.y + quarterRadius * kotlin.math.sin(angleRad).toFloat(),
            )
        drawLine(color = outline, start = start, end = end, strokeWidth = QUARTER_TICK_STROKE, cap = StrokeCap.Round)
    }

    val eighthAngles = listOf(22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5)
    eighthAngles.forEach { degrees ->
        val angleRad = Math.toRadians(degrees - CANVAS_NORTH_OFFSET_DEGREES)
        val start =
            Offset(
                x = center.x + (quarterRadius - EIGHTH_TICK_INSET) * kotlin.math.cos(angleRad).toFloat(),
                y = center.y + (quarterRadius - EIGHTH_TICK_INSET) * kotlin.math.sin(angleRad).toFloat(),
            )
        val end =
            Offset(
                x = center.x + quarterRadius * kotlin.math.cos(angleRad).toFloat(),
                y = center.y + quarterRadius * kotlin.math.sin(angleRad).toFloat(),
            )
        drawLine(color = outline, start = start, end = end, strokeWidth = QUARTER_TICK_STROKE, cap = StrokeCap.Round)
    }
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
                isAligned = false,
            ),
            onRequestLocationPermission = {},
            onOpenLocationSettings = {},
            onRequestPosition = {},
        )
    }
}
