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
package org.meshtastic.core.model

import kotlin.time.Instant

/**
 * Durable per-node remote-administration session status, derived from the time of the last admin response that carried
 * a `session_passkey` from the target node.
 *
 * The Meshtastic firmware enforces a 300 s session TTL and rotates the passkey at the 150 s mark when sending any admin
 * response (see `firmware/src/modules/AdminModule.cpp:1460-1481`). To leave headroom for in-flight packets and clock
 * skew, the Android client treats sessions older than 240 s as [Stale] — still potentially usable for a single ping but
 * the UI should refresh before navigating the user into a screen that fires more admin requests.
 */
sealed interface SessionStatus {
    /** No admin response with a session passkey has ever been observed for this node since connect. */
    data object NoSession : SessionStatus

    /** A fresh session passkey is on file and is well within the firmware TTL. */
    data class Active(val refreshedAt: Instant) : SessionStatus

    /**
     * A session passkey is on file but the firmware may have already rotated it or be about to expire it; refresh
     * before sending further admin traffic.
     */
    data class Stale(val refreshedAt: Instant) : SessionStatus
}
