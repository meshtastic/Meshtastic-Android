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

import okio.ByteString

/** Immutable identity of the transport session that admitted an inbound radio frame. */
data class RadioSessionContext(val generation: Long, val address: String) {
    /** Prevent accidental disclosure of the real transport address in diagnostic interpolation. */
    override fun toString(): String = "RadioSessionContext(generation=$generation, address=...)"
}

/**
 * An inbound radio payload bound to the transport session that admitted it.
 *
 * [payload] is an immutable [ByteString], so queued work cannot observe later mutation of a transport-owned byte array.
 * [session] retains the real internal address required for database association; callers must anonymize that address
 * before logging it.
 */
data class ReceivedRadioFrame(val payload: ByteString, val session: RadioSessionContext) {
    /** Prevent accidental disclosure of raw radio payloads in diagnostic interpolation. */
    override fun toString(): String = "ReceivedRadioFrame(payloadSize=${payload.size}, session=$session)"
}
