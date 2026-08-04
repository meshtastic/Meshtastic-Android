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
package org.meshtastic.app.map.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MapTileProviderPrefs

interface CustomTileProviderRepository {
    fun getCustomTileProviders(): Flow<List<CustomTileProviderConfig>>

    suspend fun addCustomTileProvider(config: CustomTileProviderConfig)

    suspend fun updateCustomTileProvider(config: CustomTileProviderConfig)

    suspend fun deleteCustomTileProvider(configId: String)

    suspend fun getCustomTileProviderById(configId: String): CustomTileProviderConfig?
}

@Single
class CustomTileProviderRepositoryImpl(
    private val json: Json,
    private val dispatchers: CoroutineDispatchers,
    private val mapTileProviderPrefs: MapTileProviderPrefs,
) : CustomTileProviderRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    /**
     * `null` until the persisted list has actually been read back at least once.
     *
     * The distinction matters: this used to be seeded with an empty list, and every read-modify-write that landed
     * before the first disk read completed persisted a list built on that empty baseline — wiping the user's imported
     * providers instead of merely failing to show them.
     */
    private val cache = MutableStateFlow<List<CustomTileProviderConfig>?>(null)

    /** Serializes read-modify-write cycles so two concurrent edits cannot each overwrite the other. */
    private val writeLock = Mutex()

    init {
        scope.launch { mapTileProviderPrefs.customTileProviders.collect { cache.value = it.decodeConfigs() } }
    }

    override fun getCustomTileProviders(): Flow<List<CustomTileProviderConfig>> = cache.filterNotNull()

    override suspend fun addCustomTileProvider(config: CustomTileProviderConfig) = mutate { it + config }

    override suspend fun updateCustomTileProvider(config: CustomTileProviderConfig) = mutate { providers ->
        providers.map { if (it.id == config.id) config else it }
    }

    override suspend fun deleteCustomTileProvider(configId: String) = mutate { providers ->
        providers.filterNot { it.id == configId }
    }

    override suspend fun getCustomTileProviderById(configId: String): CustomTileProviderConfig? =
        loaded().find { it.id == configId }

    /** Suspends until the persisted list has been read, so no write is ever built on an unloaded baseline. */
    private suspend fun loaded(): List<CustomTileProviderConfig> = cache.filterNotNull().first()

    private suspend fun mutate(transform: (List<CustomTileProviderConfig>) -> List<CustomTileProviderConfig>) {
        writeLock.withLock {
            val updated = transform(loaded())
            val encoded =
                try {
                    json.encodeToString(updated)
                } catch (e: SerializationException) {
                    Logger.e(e) { "Error serializing tile providers" }
                    return
                }
            // Publish before the store round-trip so an immediately following edit reads this list, not the stale one.
            cache.value = updated
            withContext(dispatchers.io) { mapTileProviderPrefs.setCustomTileProviders(encoded) }
        }
    }

    private fun String?.decodeConfigs(): List<CustomTileProviderConfig> {
        if (this == null) return emptyList()
        return try {
            json.decodeFromString<List<CustomTileProviderConfig>>(this)
        } catch (e: SerializationException) {
            Logger.e(e) { "Error deserializing tile providers" }
            emptyList()
        }
    }
}
