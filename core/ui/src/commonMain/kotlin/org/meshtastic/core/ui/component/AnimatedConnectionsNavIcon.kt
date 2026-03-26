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
package org.meshtastic.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.ui.theme.StatusColors.StatusBlue
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen

/**
 * A wrapper around [ConnectionsNavIcon] that adds a blinking glow effect when there is mesh activity (Send/Receive).
 */
@Composable
fun AnimatedConnectionsNavIcon(
    connectionState: ConnectionState,
    deviceType: DeviceType?,
    meshActivityFlow: Flow<MeshActivity>,
    modifier: Modifier = Modifier,
) {
    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    var currentGlowColor by remember { mutableStateOf(Color.Transparent) }
    val animatedGlowAlpha = remember { Animatable(0f) }

    val sendColor = colorScheme.StatusGreen
    val receiveColor = colorScheme.StatusBlue

    LaunchedEffect(meshActivityFlow, colorScheme) {
        meshActivityFlow.conflate().collect { activity ->
            val newTargetColor =
                when (activity) {
                    is MeshActivity.Send -> sendColor
                    is MeshActivity.Receive -> receiveColor
                }

            currentGlowColor = newTargetColor

            // Suspend the collection until the animation finishes.
            // conflate() will drop any fast events that arrive during this 1-second animation.
            animatedGlowAlpha.stop()
            animatedGlowAlpha.snapTo(1.0f)
            animatedGlowAlpha.animateTo(
                targetValue = 0.0f,
                animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
            )
        }
    }

    Box(
        modifier =
        modifier.drawWithCache {
            val glowRadius = size.minDimension
            val glowBrush =
                Brush.radialGradient(
                    colors =
                    listOf(
                        currentGlowColor.copy(alpha = 0.8f),
                        currentGlowColor.copy(alpha = 0.4f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = glowRadius,
                )
            onDrawWithContent {
                drawContent()
                val alpha = animatedGlowAlpha.value
                if (alpha > 0f) {
                    drawCircle(brush = glowBrush, radius = glowRadius, alpha = alpha, blendMode = BlendMode.Screen)
                }
            }
        },
    ) {
        ConnectionsNavIcon(connectionState = connectionState, deviceType = deviceType)
    }
}
