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

import androidx.compose.ui.input.key.KeyEvent

/**
 * True when this event came from an on-screen keyboard rather than a physical one.
 *
 * An on-screen keyboard has no Shift to reach a newline with, so a shortcut that consumes Enter must exempt it or that
 * field can never hold more than one line.
 */
expect fun KeyEvent.isFromSoftKeyboard(): Boolean
