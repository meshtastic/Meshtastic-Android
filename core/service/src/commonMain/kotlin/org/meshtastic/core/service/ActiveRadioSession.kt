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
package org.meshtastic.core.service

import org.meshtastic.core.repository.RadioSessionContext

/**
 * Immutable per-session identity. [generation] is bumped for every app session, one per SDK session of the selected
 * radio, so consumers can discard state retained from a previous session even when the selected address is unchanged.
 */
data class ActiveRadioSession(val generation: Long, val address: String) {
    /** Immutable context carried with every frame admitted by this session. */
    val context = RadioSessionContext(generation = generation, address = address)

    /** Prevent accidental disclosure of the raw transport address in diagnostic interpolation. */
    override fun toString(): String = "ActiveRadioSession(generation=$generation, address=...)"
}
