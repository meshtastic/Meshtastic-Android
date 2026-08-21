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

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.meshtastic.core.model.MaintenanceUf2Manifest

/** Lenient so decoding a cached column survives a model that gained fields since it was written (forward-compat). */
private val entityJson = Json { ignoreUnknownKeys = true }

/**
 * Single-row cache of the maintenance-UF2 manifest (`/resource/maintenanceUf2`), pre-serialized as one JSON column —
 * the only reader ([org.meshtastic.core.data.repository.MaintenanceUf2RepositoryImpl]) always wants the whole envelope,
 * so a granular per-field schema would add migration surface for no query anyone runs. [id] is always [SINGLETON_ID];
 * this table only ever holds one row.
 */
@Serializable
@Entity(tableName = "maintenance_uf2_cache")
data class MaintenanceUf2CacheEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "manifest_json") val manifestJson: String,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

fun MaintenanceUf2Manifest.asEntity() = MaintenanceUf2CacheEntity(manifestJson = entityJson.encodeToString(this))

// A malformed column value decodes to an empty (all-defaults) manifest rather than propagating the failure —
// consistent with the bundled-asset seed path.
fun MaintenanceUf2CacheEntity.asExternalModel(): MaintenanceUf2Manifest =
    runCatching { entityJson.decodeFromString<MaintenanceUf2Manifest>(manifestJson) }
        .getOrDefault(MaintenanceUf2Manifest())
