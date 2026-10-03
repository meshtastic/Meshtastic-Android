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
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.ui.input.RemoteKey

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

// LVGL's next-focus key value, typed as a character like Backspace.
internal const val CHAR_TAB = 9

/** Scales a tap position on the scaled-up mirror image back to panel pixel coordinates. */
internal fun Offset.toDeviceX(boxWidthPx: Int, frame: MirrorFrame): Int =
    (x / boxWidthPx * frame.width).toInt().coerceIn(0, frame.width - 1)

internal fun Offset.toDeviceY(boxHeightPx: Int, frame: MirrorFrame): Int =
    (y / boxHeightPx * frame.height).toInt().coerceIn(0, frame.height - 1)

/** The device navigation event for a key the mirror forwards; null for keys a device UI has no use for. */
internal fun RemoteKey.toInputEvent(): Int? = when (this) {
    RemoteKey.UP -> INPUT_UP
    RemoteKey.DOWN -> INPUT_DOWN
    RemoteKey.LEFT -> INPUT_LEFT
    RemoteKey.RIGHT -> INPUT_RIGHT
    RemoteKey.ESCAPE -> INPUT_BACK
    else -> null
}

/** Calls [action] with each Unicode code point of [text], joining surrogate pairs so one character is one kb_char. */
internal fun forEachCodePoint(text: String, action: (Int) -> Unit) {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val next = text.getOrNull(i + 1)
        if (c.isHighSurrogate() && next != null && next.isLowSurrogate()) {
            action(SUPPLEMENTARY_BASE + ((c.code - HIGH_SURROGATE_BASE) shl SURROGATE_SHIFT) + (next.code - LOW_SURROGATE_BASE))
            i += 2
        } else {
            action(c.code)
            i++
        }
    }
}

private const val SUPPLEMENTARY_BASE = 0x10000
private const val HIGH_SURROGATE_BASE = 0xD800
private const val LOW_SURROGATE_BASE = 0xDC00
private const val SURROGATE_SHIFT = 10

@Composable
internal fun dpadContentColor(enabled: Boolean): Color =
    if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
