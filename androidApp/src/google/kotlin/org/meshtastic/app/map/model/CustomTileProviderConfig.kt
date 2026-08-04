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
package org.meshtastic.app.map.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class CustomTileProviderConfig(
    val id: String = Uuid.random().toString(),
    val name: String,
    val urlTemplate: String,
    val localUri: String? = null,
) {
    val isLocal: Boolean
        get() = localUri != null

    /**
     * The value persisted to mark this provider as the active map selection. Local (MBTiles) providers are identified
     * by their file URI because [urlTemplate] is empty for them.
     */
    val selectionKey: String
        get() = localUri ?: urlTemplate

    /**
     * True when [selection] — a value previously produced by [selectionKey] — refers to this provider.
     *
     * Both the renderer and the start-up restore path must resolve a persisted selection the same way. Keeping the rule
     * here stops them drifting apart: a restore path that only compared [urlTemplate] silently dropped every local
     * provider on restart.
     */
    fun matchesSelection(selection: String): Boolean = localUri == selection || urlTemplate == selection
}
