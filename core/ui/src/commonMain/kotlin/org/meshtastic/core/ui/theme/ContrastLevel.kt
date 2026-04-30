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
package org.meshtastic.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Application-wide contrast level for accessibility.
 *
 * [STANDARD] keeps the default Material 3 color scheme. [MEDIUM] uses Material 3 medium-contrast color tokens and
 * increases message bubble opacity. [HIGH] uses Material 3 high-contrast color tokens, forces `onSurface` text in
 * message bubbles, and replaces translucent node-color fills with opaque theme surfaces plus accent borders.
 */
enum class ContrastLevel(val value: Int) {
    STANDARD(0),
    MEDIUM(1),
    HIGH(2),
    ;

    companion object {
        fun fromValue(value: Int): ContrastLevel = entries.firstOrNull { it.value == value } ?: STANDARD
    }
}

/**
 * Composition local providing the current [ContrastLevel].
 *
 * Read by components that need to adapt their rendering for accessibility (e.g. message bubbles, signal indicators).
 */
val LocalContrastLevel = staticCompositionLocalOf { ContrastLevel.STANDARD }
