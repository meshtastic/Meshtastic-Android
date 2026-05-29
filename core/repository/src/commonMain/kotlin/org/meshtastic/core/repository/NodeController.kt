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

/**
 * Node management operations — favorite, ignore, mute, and remove nodes.
 *
 * Mirrors the node management subset of the SDK's `AdminApi` (setFavorite, setIgnored, toggleMuted). When the SDK is
 * adopted, implementations delegate to `RadioClient.admin.setFavorite(NodeId, Boolean)` etc.
 *
 * @see RadioController which extends this interface for backward compatibility
 */
interface NodeController {

    /**
     * Sets the favorite status of a node on the radio.
     *
     * Idempotent: a no-op if the node is already in the requested state. Mirrors the SDK's `setFavorite(NodeId,
     * Boolean)` — an explicit target state rather than a toggle, so concurrent callers can't race a read-modify-write.
     */
    suspend fun setFavorite(nodeNum: Int, favorite: Boolean)

    /**
     * Sets the ignore status of a node on the radio.
     *
     * Idempotent, like [setFavorite]. Mirrors the SDK's `setIgnored(NodeId, Boolean)`.
     */
    suspend fun setIgnored(nodeNum: Int, ignored: Boolean)

    /** Toggles the mute status of a node on the radio. Mirrors the SDK's `toggleMuted(NodeId)`. */
    suspend fun toggleMuted(nodeNum: Int)

    /** Removes a node from the mesh by its node number. */
    suspend fun removeByNodenum(packetId: Int, nodeNum: Int)
}
