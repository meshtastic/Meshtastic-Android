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
package org.meshtastic.core.data.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.MqttRootsLocalDataSource
import org.meshtastic.core.data.datasource.decode
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.model.MqttRootsResponse
import org.meshtastic.core.network.MqttRootsRemoteDataSource
import org.meshtastic.core.repository.MqttRootsRepository

/**
 * Caches the MQTT root directory from the Meshtastic API (`/resource/mqttRoots`), seeded from the bundled
 * `mqtt_roots.json` snapshot, in [MaintenanceUf2RepositoryImpl]'s seed-then-refresh shape: [reconcile] carries no TTL
 * of its own, and each topic finder ViewModel calls it once when created.
 */
@Single
class MqttRootsRepositoryImpl(
    private val remoteDataSource: MqttRootsRemoteDataSource,
    private val localDataSource: MqttRootsLocalDataSource,
    private val assetReader: BundledAssetReader,
    private val json: Json,
) : MqttRootsRepository {

    /** Serializes seeding and network writes so concurrent callers don't duplicate/interleave them. */
    private val writeMutex = Mutex()

    override suspend fun getSnapshot(): MqttRootsResponse {
        ensureSeeded()
        return localDataSource.get()?.asExternalModel() ?: MqttRootsResponse()
    }

    override suspend fun reconcile() {
        safeCatching {
            val response = remoteDataSource.getMqttRoots()
            writeMutex.withLock { store(response) }
        }
            .onFailure { e -> Logger.w(e) { "MqttRootsRepository: refresh failed" } }
    }

    /** Seeds the cache from the bundled snapshot if empty (fresh install, data clear). */
    private suspend fun ensureSeeded() {
        if (localDataSource.count() > 0) return
        writeMutex.withLock {
            if (localDataSource.count() == 0) {
                safeCatching {
                    val asset = assetReader.decode<MqttRootsResponse>("mqtt_roots.json", json)
                    asset?.let { store(it) }
                }
                    .onFailure { e -> Logger.w(e) { "MqttRootsRepository: failed to seed from bundled JSON" } }
            }
        }
    }

    /**
     * Not locked itself; every caller already holds [writeMutex]. A response with no subdivision mappings is ignored,
     * whatever else it carries, so a degraded response never replaces the subdivision names the finder needs.
     */
    private suspend fun store(response: MqttRootsResponse) {
        if (response.subdivisionSegments.values.all { it.isEmpty() }) {
            Logger.w { "MqttRootsRepository: response has no subdivision mappings; leaving cache untouched" }
            return
        }
        localDataSource.upsert(response.asEntity())
    }
}
