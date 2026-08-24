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
package org.meshtastic.feature.connections.ui.components

/** Visual style for [ConnectionActionButton]. Maps to the four canonical M3 button variants. */
enum class ConnectionActionButtonStyle {
    /** Solid-fill button for the primary action in a group (e.g. "Start scan"). */
    Filled,

    /** Tonal (filled-tonal) button for secondary prominence (e.g. "Add device manually"). */
    Tonal,

    /** Outlined button for neutral or tertiary actions (e.g. "Disconnect"). */
    Outlined,

    /** Text-only button for the least prominent action (e.g. inline toggles). */
    Text,
}
