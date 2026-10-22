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
import org.meshtastic.core.model.MqttRootsResponse

/** Lenient so decoding a cached column survives a model that gained fields since it was written (forward-compat). */
private val entityJson = Json { ignoreUnknownKeys = true }

/**
 * Single-row cache of the MQTT root directory (`/resource/mqttRoots`), pre-serialized as one JSON column like
 * [MaintenanceUf2CacheEntity]: its only reader wants the whole envelope. [id] is always [SINGLETON_ID].
 */
@Serializable
@Entity(tableName = "mqtt_roots_cache")
data class MqttRootsCacheEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "roots_json") val rootsJson: String,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

fun MqttRootsResponse.asEntity() = MqttRootsCacheEntity(rootsJson = entityJson.encodeToString(this))

// A malformed column value decodes to an empty directory rather than propagating the failure, like the seed path.
fun MqttRootsCacheEntity.asExternalModel(): MqttRootsResponse = runCatching {
    entityJson.decodeFromString<MqttRootsResponse>(rootsJson)
}
    .getOrDefault(MqttRootsResponse())
