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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.Flow

/**
 * App-local node metadata that persists independently of the SDK's node database.
 *
 * This covers user annotations (favorites, notes, mute, ignore) that are NOT synced to the radio.
 * VMs and feature modules inject this instead of the full [NodeRepository] when they only need
 * metadata operations.
 */
interface AppMetadataRepository {

    /** Flow of all node metadata, keyed by node number. */
    val metadataByNum: Flow<Map<Int, NodeMetadata>>

    suspend fun setFavorite(nodeNum: Int, isFavorite: Boolean)
    suspend fun setIgnored(nodeNum: Int, isIgnored: Boolean)
    suspend fun setMuted(nodeNum: Int, isMuted: Boolean)
    suspend fun setNotes(nodeNum: Int, notes: String)
    suspend fun setManuallyVerified(nodeNum: Int, verified: Boolean)
    suspend fun delete(nodeNum: Int)
}

/** Lightweight metadata value object exposed to feature modules. */
data class NodeMetadata(
    val num: Int,
    val isFavorite: Boolean = false,
    val isIgnored: Boolean = false,
    val isMuted: Boolean = false,
    val notes: String? = null,
    val manuallyVerified: Boolean = false,
)
