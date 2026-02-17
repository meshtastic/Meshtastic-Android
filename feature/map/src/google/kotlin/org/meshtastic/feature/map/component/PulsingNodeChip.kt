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
package org.meshtastic.feature.map.component

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
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.nowSeconds
import org.meshtastic.core.ui.component.NodeChip

@Composable
fun PulsingNodeChip(node: Node, modifier: Modifier = Modifier) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(node) {
        if ((nowSeconds - node.lastHeard) <= 5) {
            launch {
                animatedProgress.snapTo(0f)
                animatedProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
                )
            }
        }
    }

    Box(
        modifier =
        modifier.drawWithContent {
            drawContent()
            if (animatedProgress.value > 0 && animatedProgress.value < 1f) {
                val alpha = (1f - animatedProgress.value) * 0.3f
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
