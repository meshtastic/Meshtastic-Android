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
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.util.nowMillis
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import org.meshtastic.core.ui.theme.AppTheme

internal const val COOL_DOWN_TIME_MS = 30000L
internal const val REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS = 180000L // 3 minutes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CooldownIconButton(
    onClick: () -> Unit,
    cooldownTimestamp: Long?,
    cooldownDuration: Long = COOL_DOWN_TIME_MS,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(cooldownTimestamp) {
        if (cooldownTimestamp == null) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val timeSinceLast = nowMillis - cooldownTimestamp
        if (timeSinceLast < cooldownDuration) {
            val remainingTime = cooldownDuration - timeSinceLast
            progress.snapTo(remainingTime / cooldownDuration.toFloat())
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = remainingTime.toInt(), easing = { it }),
            )
        } else {
            progress.snapTo(0f)
        }
    }

    val isCoolingDown = progress.value > 0f
    val stroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() }, cap = StrokeCap.Round)

    IconButton(
        onClick = { if (!isCoolingDown) onClick() },
        enabled = !isCoolingDown,
        colors = IconButtonDefaults.iconButtonColors(),
    ) {
        if (isCoolingDown) {
            CircularWavyProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.size(24.dp),
                stroke = stroke,
                trackStroke = stroke,
                wavelength = 8.dp,
            )
        } else {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CooldownOutlinedIconButton(
    onClick: () -> Unit,
    cooldownTimestamp: Long?,
    cooldownDuration: Long = COOL_DOWN_TIME_MS,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(cooldownTimestamp) {
        if (cooldownTimestamp == null) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        val timeSinceLast = nowMillis - cooldownTimestamp
        if (timeSinceLast < cooldownDuration) {
            val remainingTime = cooldownDuration - timeSinceLast
            progress.snapTo(remainingTime / cooldownDuration.toFloat())
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = remainingTime.toInt(), easing = { it }),
            )
        } else {
            progress.snapTo(0f)
        }
    }

    val isCoolingDown = progress.value > 0f
    val stroke = Stroke(width = with(LocalDensity.current) { 2.dp.toPx() }, cap = StrokeCap.Round)

    OutlinedIconButton(
        onClick = { if (!isCoolingDown) onClick() },
        enabled = !isCoolingDown,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.outlinedIconButtonColors(),
    ) {
        if (isCoolingDown) {
            CircularWavyProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.size(24.dp),
                stroke = stroke,
                trackStroke = stroke,
                wavelength = 8.dp,
            )
        } else {
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CooldownOutlinedIconButtonPreview() {
    AppTheme {
        CooldownOutlinedIconButton(onClick = {}, cooldownTimestamp = nowMillis - 15000L) {
            Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
        }
    }
}
