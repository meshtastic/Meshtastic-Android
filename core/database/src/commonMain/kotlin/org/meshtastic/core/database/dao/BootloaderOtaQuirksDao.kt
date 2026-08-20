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
import org.meshtastic.core.database.entity.BootloaderOtaQuirksCacheEntity

@Dao
interface BootloaderOtaQuirksDao {
    @Upsert suspend fun upsert(entity: BootloaderOtaQuirksCacheEntity)

    @Query("SELECT * FROM bootloader_ota_quirks_cache WHERE id = 0")
    suspend fun get(): BootloaderOtaQuirksCacheEntity?

    @Query("SELECT COUNT(*) FROM bootloader_ota_quirks_cache")
    suspend fun count(): Int
}
