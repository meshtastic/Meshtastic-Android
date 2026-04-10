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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import org.meshtastic.core.ui.component.PlaceholderScreen
import org.meshtastic.proto.Position

/**
 * Provides an embeddable position-track map composable that renders a polyline with markers for the given [positions].
 * Unlike [LocalNodeMapScreenProvider], this does **not** include a Scaffold or AppBar — it is designed to be embedded
 * inside another screen layout (e.g. the position-log adaptive layout).
 *
 * On Desktop/JVM targets where native maps are not yet available, it falls back to a [PlaceholderScreen].
 */
@Suppress("Wrapping")
val LocalNodeTrackMapProvider =
    compositionLocalOf<@Composable (destNum: Int, positions: List<Position>, modifier: Modifier) -> Unit> {
        { _, _, _ -> PlaceholderScreen("Position Track Map") }
    }
