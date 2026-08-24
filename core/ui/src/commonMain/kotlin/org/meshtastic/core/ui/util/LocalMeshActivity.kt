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

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.model.MeshActivity

/**
 * The connected device's mesh Send/Receive activity stream, provided once at the app root (see MeshtasticAppShell).
 *
 * The provided value is the **Flow itself**, not a collected value — its reference is stable, so providing it costs no
 * recomposition. Collection happens only at the leaf that needs it (the local node's connection badge / nav icon),
 * which animates in the draw phase. Providing a per-packet collected value here instead would recompose every reader.
 * Null — the default before a device connects, and in previews/tests — falls back to the non-animated icon.
 */
@Suppress("CompositionLocalAllowlist")
val LocalMeshActivity = compositionLocalOf<Flow<MeshActivity>?> { null }
