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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.BasicSettingsItem
import org.meshtastic.core.ui.theme.AppTheme

private const val COOL_DOWN_TIME_MS = 30000L

@Composable
fun TracerouteButton(
    text: String = stringResource(id = R.string.traceroute),
    lastTracerouteTime: Long?,
    onClick: () -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(lastTracerouteTime) {
        val timeSinceLast = System.currentTimeMillis() - (lastTracerouteTime ?: 0)
        if (timeSinceLast < COOL_DOWN_TIME_MS) {
            val remainingTime = COOL_DOWN_TIME_MS - timeSinceLast
            progress.snapTo(remainingTime / COOL_DOWN_TIME_MS.toFloat())
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = remainingTime.toInt(), easing = { it }),
            )
        }
    }

    TracerouteButton(text = text, progress = progress.value, onClick = onClick)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TracerouteButton(text: String, progress: Float, onClick: () -> Unit) {
    val isCoolingDown = progress > 0f

    val stroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() }, cap = StrokeCap.Round)

    BasicSettingsItem(
        text = text,
        enabled = !isCoolingDown,
        leadingIcon = Icons.Default.Route,
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

@Preview(showBackground = true)
@Composable
private fun TracerouteButtonPreview() {
    AppTheme { TracerouteButton(text = "Traceroute", progress = .6f, onClick = {}) }
}
