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
 * This interface defines the contract that all radio backend implementations must adhere to.
 */
interface InterfaceSpec<T : IRadioInterface> {
    fun createInterface(rest: String): T

    /** Return true if this address is still acceptable. For BLE that means, still bonded */
    fun addressValid(rest: String): Boolean = true
}