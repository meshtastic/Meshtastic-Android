/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.map.mapcompose.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.NodeChip

/**
 * A [NodeChip] that flashes briefly when the node was heard within the last few seconds — the shared twin of the
 * google renderer's marker chip.
 */
@Composable
internal fun PulsingNodeChip(node: Node, modifier: Modifier = Modifier) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(node) {
        if ((nowSeconds - node.lastHeard) <= JUST_HEARD_WINDOW_SECONDS) {
            launch {
                animatedProgress.snapTo(0f)
                animatedProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = PULSE_DURATION_MS, easing = LinearEasing),
                )
            }
        }
    }

    Box(
        modifier =
        modifier.drawWithContent {
            drawContent()
            if (animatedProgress.value > 0 && animatedProgress.value < 1f) {
                val alpha = (1f - animatedProgress.value) * PULSE_MAX_ALPHA
                drawRoundRect(
                    size = size,
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    color = Color.White.copy(alpha = alpha),
                )
            }
        },
    ) {
        NodeChip(node = node)
    }
}

private const val JUST_HEARD_WINDOW_SECONDS = 5
private const val PULSE_DURATION_MS = 1000
private const val PULSE_MAX_ALPHA = 0.3f
