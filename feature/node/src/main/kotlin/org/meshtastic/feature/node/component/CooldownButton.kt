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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.twotone.Mediation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.request_neighbor_info
import org.meshtastic.core.strings.traceroute
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.core.ui.theme.AppTheme

private const val COOL_DOWN_TIME_MS = 30000L
private const val REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS = 180000L // 3 minutes

@Composable
fun TracerouteButton(
    text: String = stringResource(Res.string.traceroute),
    lastTracerouteTime: Long?,
    onClick: () -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(lastTracerouteTime) {
        if (lastTracerouteTime == null) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val timeSinceLast = System.currentTimeMillis() - lastTracerouteTime
        if (timeSinceLast < COOL_DOWN_TIME_MS) {
            val remainingTime = COOL_DOWN_TIME_MS - timeSinceLast
            progress.snapTo(remainingTime / COOL_DOWN_TIME_MS.toFloat())
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = remainingTime.toInt(), easing = { it }),
            )
        } else {
            progress.snapTo(0f)
        }
    }

    CooldownButton(text = text, leadingIcon = Icons.Default.Route, progress = progress.value, onClick = onClick)
}

@Composable
fun TracerouteChip(lastTracerouteTime: Long?, onClick: () -> Unit) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(lastTracerouteTime) {
        if (lastTracerouteTime == null) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val timeSinceLast = System.currentTimeMillis() - lastTracerouteTime
        if (timeSinceLast < COOL_DOWN_TIME_MS) {
            val remainingTime = COOL_DOWN_TIME_MS - timeSinceLast
            progress.snapTo(remainingTime / COOL_DOWN_TIME_MS.toFloat())
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = remainingTime.toInt(), easing = { it }),
            )
        } else {
            progress.snapTo(0f)
        }
    }

    CooldownChip(
        text = stringResource(Res.string.traceroute),
        leadingIcon = Icons.Default.Route,
        progress = progress.value,
        onClick = onClick,
    )
}

@Composable
fun RequestNeighborsButton(
    text: String = stringResource(Res.string.request_neighbor_info),
    lastRequestNeighborsTime: Long?,
    onClick: () -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(lastRequestNeighborsTime) {
        if (lastRequestNeighborsTime == null) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val timeSinceLast = System.currentTimeMillis() - lastRequestNeighborsTime
        if (timeSinceLast < REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS) {
            val remainingTime = REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS - timeSinceLast
            progress.snapTo(remainingTime / REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS.toFloat())
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = remainingTime.toInt(), easing = { it }),
            )
        } else {
            progress.snapTo(0f)
        }
    }

    CooldownButton(text = text, leadingIcon = Icons.TwoTone.Mediation, progress = progress.value, onClick = onClick)
}

@Composable
fun RequestNeighborsChip(lastRequestNeighborsTime: Long?, onClick: () -> Unit) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(lastRequestNeighborsTime) {
        if (lastRequestNeighborsTime == null) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val timeSinceLast = System.currentTimeMillis() - lastRequestNeighborsTime
        if (timeSinceLast < REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS) {
            val remainingTime = REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS - timeSinceLast
            progress.snapTo(remainingTime / REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS.toFloat())
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = remainingTime.toInt(), easing = { it }),
            )
        } else {
            progress.snapTo(0f)
        }
    }

    CooldownChip(
        text = stringResource(Res.string.request_neighbor_info),
        leadingIcon = Icons.TwoTone.Mediation,
        progress = progress.value,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CooldownButton(text: String, leadingIcon: ImageVector, progress: Float, onClick: () -> Unit) {
    val isCoolingDown = progress > 0f
    val stroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() }, cap = StrokeCap.Round)

    BasicListItem(
        text = text,
        enabled = !isCoolingDown,
        leadingIcon = leadingIcon,
        trailingContent = {
            if (isCoolingDown) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(24.dp),
                    stroke = stroke,
                    trackStroke = stroke,
                    wavelength = 8.dp,
                )
            }
        },
        onClick = {
            if (!isCoolingDown) {
                onClick()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CooldownChip(text: String, leadingIcon: ImageVector, progress: Float, onClick: () -> Unit) {
    val isCoolingDown = progress > 0f
    val stroke = Stroke(width = with(LocalDensity.current) { 1.dp.toPx() }, cap = StrokeCap.Round)

    AssistChip(
        onClick = { if (!isCoolingDown) onClick() },
        label = { Text(text) },
        enabled = !isCoolingDown,
        leadingIcon = {
            if (isCoolingDown) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(18.dp),
                    stroke = stroke,
                    trackStroke = stroke,
                    wavelength = 6.dp,
                )
            } else {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun TracerouteButtonPreview() {
    AppTheme { CooldownButton(text = "Traceroute", leadingIcon = Icons.Default.Route, progress = .6f, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun TracerouteChipPreview() {
    AppTheme { CooldownChip(text = "Traceroute", leadingIcon = Icons.Default.Route, progress = .6f, onClick = {}) }
}
