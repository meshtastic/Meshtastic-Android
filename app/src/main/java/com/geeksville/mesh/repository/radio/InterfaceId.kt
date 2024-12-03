/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.repository.radio

/**
 * Address identifiers for all supported radio backend implementations.
 */
enum class InterfaceId(val id: Char) {
    BLUETOOTH('x'),
    MOCK('m'),
    NOP('n'),
    SERIAL('s'),
    TCP('t'),
    ;

    companion object {
        fun forIdChar(id: Char): InterfaceId? {
            return entries.firstOrNull { it.id == id }
        }
    }
}