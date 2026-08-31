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

private const val ROOM_POOL_ACQUIRE_TIMEOUT_PHRASE = "timed out attempting to acquire"
private const val ROOM_READER_CONNECTION_PHRASE = "reader connection"
private const val ROOM_WRITER_CONNECTION_PHRASE = "writer connection"

/**
 * Room KMP currently exposes pool-acquire timeouts as exception message text instead of a stable common typed signal.
 * Keep this fallback narrow so BLE/GATT/transport connection errors do not trigger DB reopen recovery.
 */
internal fun isRoomPoolAcquireTimeoutMessage(message: String): Boolean = ROOM_POOL_ACQUIRE_TIMEOUT_PHRASE in message &&
    (ROOM_READER_CONNECTION_PHRASE in message || ROOM_WRITER_CONNECTION_PHRASE in message)

/** Detects Room pool-acquire timeout exceptions by inspecting the exception and its cause chain. */
internal fun isDbPoolAcquireTimeoutException(e: Exception): Boolean = generateSequence<Throwable>(e) { it.cause }
    .any { throwable ->
        val msg = throwable.message?.lowercase() ?: return@any false
        isRoomPoolAcquireTimeoutMessage(msg)
    }
