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

import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * A [SliderState] that shows a caller-owned [value]. Pass it with an `onValueChange`: the slider then reports drags
 * through that callback and never moves on its own, so [value] stays the single source of truth.
 */
@Composable
fun rememberHoistedSliderState(
    value: Float,
    steps: Int = 0,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
): SliderState {
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    state.value = value
    return state
}
