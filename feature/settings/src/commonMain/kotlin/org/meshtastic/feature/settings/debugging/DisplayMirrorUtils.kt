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
package org.meshtastic.feature.settings.debugging

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import org.meshtastic.core.repository.MirrorFrame

// Firmware input_broker_event codes (src/input/InputBroker.h). USER_PRESS is
// what physical touch drivers emit for a tap, with touch coordinates attached.
internal const val INPUT_SELECT = 10
internal const val INPUT_SELECT_LONG = 11
internal const val INPUT_UP = 17
internal const val INPUT_DOWN = 18
internal const val INPUT_LEFT = 19
internal const val INPUT_RIGHT = 20
internal const val INPUT_BACK = 27
internal const val INPUT_USER_PRESS = 28

// ANYKEY carries a character in kb_char rather than a navigation code.
internal const val INPUT_ANYKEY = 0xFF

// LVGL's backspace key value; typed as a character so text fields delete
// rather than navigating back (which is what Esc is for).
internal const val CHAR_BACKSPACE = 8

// Below this, key events are control codes rather than typable text.
internal const val FIRST_PRINTABLE_CHAR = 32

/** Scales a tap position on the scaled-up mirror image back to panel pixel coordinates. */
internal fun Offset.toDeviceX(boxWidthPx: Int, frame: MirrorFrame): Int =
    (x / boxWidthPx * frame.width).toInt().coerceIn(0, frame.width - 1)

internal fun Offset.toDeviceY(boxHeightPx: Int, frame: MirrorFrame): Int =
    (y / boxHeightPx * frame.height).toInt().coerceIn(0, frame.height - 1)

internal fun keyToInputEvent(key: Key): Int? = when (key) {
    Key.DirectionUp -> INPUT_UP

    Key.DirectionDown -> INPUT_DOWN

    Key.DirectionLeft -> INPUT_LEFT

    Key.DirectionRight -> INPUT_RIGHT

    Key.Enter,
    Key.NumPadEnter,
    Key.Spacebar,
    -> INPUT_SELECT

    Key.Escape -> INPUT_BACK

    else -> null
}

@Composable
internal fun dpadContentColor(enabled: Boolean): Color =
    if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
