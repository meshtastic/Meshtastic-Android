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
package org.meshtastic.core.ui.util

import android.view.KeyCharacterMap
import androidx.compose.ui.input.key.KeyEvent
import android.view.KeyEvent as AndroidKeyEvent

/**
 * IMEs synthesise their key events against the virtual device and flag them; AOSP's own soft keyboard sets both, so
 * either alone identifies one.
 */
actual fun KeyEvent.isFromSoftKeyboard(): Boolean = nativeKeyEvent.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD ||
    nativeKeyEvent.flags and AndroidKeyEvent.FLAG_SOFT_KEYBOARD != 0
