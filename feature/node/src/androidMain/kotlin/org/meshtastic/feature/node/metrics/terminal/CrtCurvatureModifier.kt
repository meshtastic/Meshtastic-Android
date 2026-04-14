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
package org.meshtastic.feature.node.metrics.terminal

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * AGSL barrel-distortion shader that simulates the curved screen of a CRT monitor.
 *
 * The UV coordinates are shifted from [0,1] into [-0.5, 0.5], squared, scaled by [strength], then used to warp the
 * sample position — producing the classic pincushion/barrel look.
 *
 * Only applied on Android 12+ (API 31+); older devices get the no-op pass-through.
 */
@Suppress("MagicNumber")
private val CRT_AGSL =
    """
    uniform shader image;
    uniform float2 resolution;
    uniform float strength;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        // Centre UV on (0,0)
        float2 c = uv - 0.5;
        // Barrel distortion — shift by c^2 * strength
        float2 distorted = uv + c * dot(c, c) * strength;
        // Clamp to avoid edge bleeding
        distorted = clamp(distorted, float2(0.0, 0.0), float2(1.0, 1.0));
        return image.eval(distorted * resolution);
    }
    """
        .trimIndent()

@Composable
actual fun Modifier.crtCurvature(strength: Float): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val shader = remember { RuntimeShader(CRT_AGSL) }
    graphicsLayer {
        val w = size.width
        val h = size.height
        if (w > 0f && h > 0f) {
            shader.setFloatUniform("resolution", w, h)
            shader.setFloatUniform("strength", strength * 4f) // scale for visible effect
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
        }
    }
} else {
    this
}
