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
package org.meshtastic.core.database

import kotlinx.coroutines.flow.StateFlow

/**
 * Provides multiplatform access to the current [MeshtasticDatabase] and a safe transactional helper. Platform
 * implementations manage the concrete lifecycle (Room on Android, etc.).
 *
 * **Write policy:** every one-shot DAO *write* (insert/upsert/update/delete/clear) must go through [withDb], never
 * `currentDb.value` directly. [withDb] registers the write with the cross-transport merge drain barrier (see
 * [DatabaseManager.associateDevice]) so a merge can't snapshot a database while the write is still in flight and lose
 * it when that database is retired. The callback is never replayed automatically after it starts: callers that need
 * retries must make that policy explicit at a higher layer where idempotency is known.
 *
 * One-shot DAO *reads* use [withReadDb]. They stay out of writer admission and the serialized containment lane; the
 * logical-retirement guarantee in [DatabaseManager] keeps a captured published pool alive for the process lifetime.
 * [currentDb] itself remains the right latch for Flow/Paging factories, which must re-latch on database switches.
 */
interface DatabaseProvider {
    /** Reactive stream of the currently active database instance. */
    val currentDb: StateFlow<MeshtasticDatabase>

    /**
     * Execute one bounded read against the synchronously published current database without writer admission. The read
     * is tracked through orderly shutdown and fails with [IllegalStateException] if admission starts after shutdown.
     */
    suspend fun <T> withReadDb(block: suspend (MeshtasticDatabase) -> T): T

    /** Execute [block] against the current database, returning `null` if no database is available. */
    suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T?
}
