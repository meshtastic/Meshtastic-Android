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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.LocalReduceMotion

/**
 * Creates a [Modifier] that adds an expressive animated focus ring for keyboard/D-pad navigation.
 *
 * Draws a visible border using [MaterialTheme.colorScheme.primary] when the element receives focus. Animates border
 * width with spring physics when reduced-motion is disabled; uses instant snap otherwise.
 *
 * Apply this to interactive composables (buttons, list items, FABs) that need visible focus indicators for
 * accessibility with external input devices.
 *
 * Must be called from a `@Composable` context so it can read the theme and animate state.
 */
@Composable
fun Modifier.expressiveFocusIndication(): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current

    val borderWidth by
        animateDpAsState(
            targetValue = if (isFocused) 2.dp else 0.dp,
            animationSpec =
            if (reduceMotion) {
                spring(stiffness = Float.MAX_VALUE)
            } else {
                spring(stiffness = 600f, dampingRatio = 0.7f)
            },
            label = "focusBorder",
        )

    val primaryColor = MaterialTheme.colorScheme.primary

    return this.onFocusChanged { isFocused = it.isFocused }
        .focusable()
        .then(
            if (borderWidth > 0.dp) {
                Modifier.border(width = borderWidth, color = primaryColor, shape = RoundedCornerShape(12.dp))
            } else {
                Modifier
            },
        )
}
