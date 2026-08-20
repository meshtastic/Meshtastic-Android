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
package org.meshtastic.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import org.meshtastic.core.database.entity.MaintenanceUf2CacheEntity

@Dao
interface MaintenanceUf2Dao {
    @Upsert suspend fun upsert(entity: MaintenanceUf2CacheEntity)

    @Query("SELECT * FROM maintenance_uf2_cache WHERE id = 0")
    suspend fun get(): MaintenanceUf2CacheEntity?

    @Query("SELECT COUNT(*) FROM maintenance_uf2_cache")
    suspend fun count(): Int
}
