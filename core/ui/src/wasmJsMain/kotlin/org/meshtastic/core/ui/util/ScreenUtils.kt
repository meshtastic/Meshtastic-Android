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

import androidx.compose.runtime.Composable

/**
 * Web no-op — unlike Screen Wake Lock, there is no browser API to read or set the physical display's brightness at all
 * (a page can only fake it by dimming its own content with an overlay, which is a UI concern, not a platform primitive
 * this function should reach for). Matches the JVM/Desktop actual.
 */
@Composable
actual fun SetScreenBrightness(brightness: Float) {
    // No-op on Web — no browser API for physical display brightness exists.
}
