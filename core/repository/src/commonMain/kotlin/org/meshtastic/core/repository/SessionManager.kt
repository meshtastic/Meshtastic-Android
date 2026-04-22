/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import kotlinx.coroutines.flow.SharedFlow
import okio.ByteString
import org.meshtastic.core.model.SessionStatus

/**
 * Owns per-node remote-administration session state — the session passkey the firmware embeds in every admin response
 * and the timestamp it was last refreshed at.
 *
 * Replaces the single global passkey atomic that previously lived in `CommandSenderImpl`, which silently invalidated
 * the session of node A as soon as node B responded with a different key (the multi-remote-admin bug).
 *
 * Lifecycle:
 * - [recordSession] is called by the admin packet handler whenever an inbound admin response carries a non-empty
 *   `session_passkey`.
 * - [getPasskey] is read on the send path to attach the appropriate per-destination key.
 * - [clearAll] is called on radio teardown to prevent stale keys from surviving a reconnect.
 */
interface SessionManager {
    /** Record an inbound session refresh from [srcNodeNum]. No-op for empty [passkey]. */
    fun recordSession(srcNodeNum: Int, passkey: ByteString)

    /** Returns the most recently observed passkey for [destNum], or [ByteString.EMPTY] if none. */
    fun getPasskey(destNum: Int): ByteString

    /** Clears all per-node session state. Call on radio disconnect / teardown. */
    fun clearAll()

    /**
     * Hot stream of `srcNodeNum` values, emitted exactly once per call to [recordSession] with a non-empty passkey.
     * Used by `EnsureRemoteAdminSessionUseCase` to await a session refresh from a specific node without polling.
     *
     * Backed by a `MutableSharedFlow` with no replay; subscribers must subscribe **before** dispatching the request
     * that triggers the refresh.
     */
    val sessionRefreshFlow: SharedFlow<Int>

    /**
     * Cold per-node [SessionStatus] flow. Emits the current status synchronously on subscription and re-emits whenever
     * the underlying state crosses the staleness threshold.
     */
    fun observeSessionStatus(destNum: Int): Flow<SessionStatus>
}
