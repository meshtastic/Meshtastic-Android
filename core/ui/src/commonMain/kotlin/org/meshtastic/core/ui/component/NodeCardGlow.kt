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
@file:Suppress("MagicNumber")

package org.meshtastic.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val GLOW_MAX_ELEVATION_DP = 8

/**
 * A modifier that applies a glow animation to a node card when a packet is received.
 *
 * The glow blooms quickly using [MaterialTheme.motionScheme.fastSpatialSpec] and decays slowly using
 * [MaterialTheme.motionScheme.slowSpatialSpec], creating an M3 Expressive motion effect.
 *
 * @param lastHeard The node's lastHeard timestamp. Changes trigger the glow animation.
 * @param nodeColor The color used for the glow shadow (typically the node's assigned color).
 */
@Composable
fun Modifier.nodeCardGlow(lastHeard: Int, nodeColor: Color): Modifier {
    val glowAlpha = remember { Animatable(0f) }
    val bloomSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val decaySpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val cardShape = CardDefaults.shape

    // Track previous value to distinguish initial composition from actual changes
    val previousLastHeard = remember { mutableIntStateOf(lastHeard) }
    LaunchedEffect(lastHeard) {
        if (lastHeard > 0 && lastHeard != previousLastHeard.intValue) {
            glowAlpha.animateTo(targetValue = 1f, animationSpec = bloomSpec)
            glowAlpha.animateTo(targetValue = 0f, animationSpec = decaySpec)
        }
        previousLastHeard.intValue = lastHeard
    }

    val alpha = glowAlpha.value
    return if (alpha > 0f) {
        this.shadow(
            elevation = GLOW_MAX_ELEVATION_DP.dp * alpha,
            shape = cardShape,
            ambientColor = nodeColor.copy(alpha = alpha),
            spotColor = nodeColor.copy(alpha = alpha),
        )
    } else {
        this
    }
}
