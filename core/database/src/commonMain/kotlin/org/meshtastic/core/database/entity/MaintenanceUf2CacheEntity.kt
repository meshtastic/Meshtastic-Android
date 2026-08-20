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

/**
 * Single-row cache of the maintenance-UF2 manifest (`/resource/maintenanceUf2`), storing the exact bytes served — not a
 * decoded/re-serialized model — so a cached row's own digest can be re-verified against the compile-time pin on read,
 * the same way a fresh fetch is. [id] is always [SINGLETON_ID]; this table only ever holds one row.
 */
@androidx.room3.Entity(tableName = "maintenance_uf2_cache")
data class MaintenanceUf2CacheEntity(
    @androidx.room3.PrimaryKey val id: Int = SINGLETON_ID,
    @androidx.room3.ColumnInfo(name = "manifest_bytes") val manifestBytes: ByteArray,
) {
    companion object {
        const val SINGLETON_ID = 0
    }

    // Room needs no equals/hashCode, but a data class with a ByteArray property gets a broken default one — avoid
    // anything relying on it (there is no such use today; this exists so a future misuse fails loudly, not subtly).
    override fun equals(other: Any?): Boolean = this === other ||
        (other is MaintenanceUf2CacheEntity && id == other.id && manifestBytes.contentEquals(other.manifestBytes))

    override fun hashCode(): Int = id * 31 + manifestBytes.contentHashCode()
}
