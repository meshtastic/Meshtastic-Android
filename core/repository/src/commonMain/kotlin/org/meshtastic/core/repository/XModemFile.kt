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

/** A file received via an XModem transfer from the connected device. */
data class XModemFile(
    /** Filename as set via [XModemManager.setTransferName] before the transfer started. */
    val name: String,
    /** Raw bytes of the received file (trailing CTRLZ padding stripped). */
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is XModemFile) return false
        return name == other.name && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * name.hashCode() + data.contentHashCode()
}
