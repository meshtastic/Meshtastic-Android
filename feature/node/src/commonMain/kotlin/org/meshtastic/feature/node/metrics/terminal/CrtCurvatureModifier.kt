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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Applies a barrel-distortion CRT curvature effect to the composable.
 *
 * On Android 12+ (API 31+) this uses [android.graphics.RenderEffect] with a [android.graphics.RuntimeShader] (AGSL) to
 * simulate the curved screen of a classic cathode-ray tube.
 *
 * On all other platforms (desktop, iOS, older Android) this is a no-op.
 *
 * @param strength Barrel distortion factor in the range [0, 1]. 0 = flat, 1 = heavy curve.
 */
@Composable expect fun Modifier.crtCurvature(strength: Float = 0.08f): Modifier
