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
package org.meshtastic.core.database.entity

/** Persisted discovery-session states shared by database queries, scan execution, and recovery. */
object DiscoverySessionStatus {
    const val IN_PROGRESS = "in_progress"
    const val INTERRUPTED = "interrupted"
    const val COMPLETE = "complete"
    const val FAILED = "failed"
    const val STOPPED = "stopped"
    const val RESTORED = "restored"
    const val UNRESTORABLE = "unrestorable"
    const val RESTORE_PENDING_COMPLETE = "restore_pending_complete"
    const val RESTORE_PENDING_FAILED = "restore_pending_failed"
    const val RESTORE_PENDING_STOPPED = "restore_pending_stopped"

    /** SQL literal list required by Room's compile-time query parser for sessions that reconnect can still recover. */
    const val RECOVERABLE_SQL_LIST =
        "'$IN_PROGRESS', '$INTERRUPTED', '$RESTORE_PENDING_COMPLETE', " +
            "'$RESTORE_PENDING_FAILED', '$RESTORE_PENDING_STOPPED'"

    /** In-memory counterpart of [RECOVERABLE_SQL_LIST]. A unit test keeps the two declarations synchronized. */
    val RECOVERABLE: Set<String> =
        linkedSetOf(IN_PROGRESS, INTERRUPTED, RESTORE_PENDING_COMPLETE, RESTORE_PENDING_FAILED, RESTORE_PENDING_STOPPED)
}
