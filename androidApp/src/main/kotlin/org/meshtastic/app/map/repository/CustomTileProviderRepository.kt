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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.koin.core.annotation.Single
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MapTileProviderPrefs

interface CustomTileProviderRepository {
    fun getCustomTileProviders(): Flow<List<CustomTileProviderConfig>>

    suspend fun awaitCustomTileProviders(): CustomTileProviderLoadResult

    suspend fun addCustomTileProvider(config: CustomTileProviderConfig): CustomTileProviderSaveResult

    suspend fun updateCustomTileProvider(config: CustomTileProviderConfig): CustomTileProviderSaveResult

    suspend fun deleteCustomTileProvider(configId: String)

    suspend fun getCustomTileProviderById(configId: String): CustomTileProviderConfig?
}

data class CustomTileProviderLoadResult(val providers: List<CustomTileProviderConfig>, val isSuccessful: Boolean)

enum class CustomTileProviderSaveResult {
    SAVED,
    DUPLICATE_NAME,
    NOT_FOUND,
}

@Single
class CustomTileProviderRepositoryImpl(
    private val json: Json,
    dispatchers: CoroutineDispatchers,
    private val mapTileProviderPrefs: MapTileProviderPrefs,
) : CustomTileProviderRepository {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val mutex = Mutex()
    private var isLoaded = false
    private var initialLoadSuccessful = true
    private val providers = MutableStateFlow<List<CustomTileProviderConfig>>(emptyList())

    init {
        scope.launch { mutex.withLock { loadIfNeeded() } }
    }

    override fun getCustomTileProviders(): Flow<List<CustomTileProviderConfig>> = providers.asStateFlow()

    override suspend fun awaitCustomTileProviders(): CustomTileProviderLoadResult = mutex.withLock {
        loadIfNeeded()
        CustomTileProviderLoadResult(providers = providers.value, isSuccessful = initialLoadSuccessful)
    }

    override suspend fun addCustomTileProvider(config: CustomTileProviderConfig): CustomTileProviderSaveResult =
        mutex.withLock {
            loadIfNeeded()
            val normalized = config.normalized()
            if (providers.value.any { it.name.trim().equals(normalized.name, ignoreCase = true) }) {
                return@withLock CustomTileProviderSaveResult.DUPLICATE_NAME
            }
            persist(providers.value + normalized)
            CustomTileProviderSaveResult.SAVED
        }

    override suspend fun updateCustomTileProvider(config: CustomTileProviderConfig): CustomTileProviderSaveResult =
        mutex.withLock {
            loadIfNeeded()
            val normalized = config.normalized()
            if (providers.value.none { it.id == normalized.id }) {
                return@withLock CustomTileProviderSaveResult.NOT_FOUND
            }
            if (
                providers.value.any {
                    it.id != normalized.id && it.name.trim().equals(normalized.name, ignoreCase = true)
                }
            ) {
                return@withLock CustomTileProviderSaveResult.DUPLICATE_NAME
            }
            persist(providers.value.map { if (it.id == normalized.id) normalized else it })
            CustomTileProviderSaveResult.SAVED
        }

    override suspend fun deleteCustomTileProvider(configId: String) {
        mutate { current -> current.filterNot { it.id == configId } }
    }

    override suspend fun getCustomTileProviderById(configId: String): CustomTileProviderConfig? = mutex.withLock {
        loadIfNeeded()
        providers.value.find { it.id == configId }
    }

    private suspend fun mutate(transform: (List<CustomTileProviderConfig>) -> List<CustomTileProviderConfig>) {
        mutex.withLock {
            loadIfNeeded()
            persist(transform(providers.value))
        }
    }

    private suspend fun persist(updated: List<CustomTileProviderConfig>) {
        mapTileProviderPrefs.setCustomTileProviders(json.encodeToString(updated))
        providers.value = updated
    }

    private suspend fun loadIfNeeded() {
        if (isLoaded) return
        val result = decode(mapTileProviderPrefs.awaitCustomTileProviders())
        if (result.mustPersistGeneratedIds) {
            persist(result.providers)
        } else {
            providers.value = result.providers
        }
        initialLoadSuccessful = result.isSuccessful
        isLoaded = true
    }

    private fun decode(serialized: String?): DecodedCustomTileProviders = if (serialized.isNullOrBlank()) {
        DecodedCustomTileProviders(providers = emptyList(), isSuccessful = true)
    } else {
        try {
            DecodedCustomTileProviders(
                providers = json.decodeFromString(serialized),
                isSuccessful = true,
                mustPersistGeneratedIds =
                json.parseToJsonElement(serialized).jsonArray.any { "id" !in it.jsonObject },
            )
        } catch (_: SerializationException) {
            Logger.w { "Ignoring malformed persisted tile providers" }
            DecodedCustomTileProviders(providers = emptyList(), isSuccessful = false)
        } catch (_: IllegalArgumentException) {
            Logger.w { "Ignoring malformed persisted tile providers" }
            DecodedCustomTileProviders(providers = emptyList(), isSuccessful = false)
        }
    }
}

private data class DecodedCustomTileProviders(
    val providers: List<CustomTileProviderConfig>,
    val isSuccessful: Boolean,
    val mustPersistGeneratedIds: Boolean = false,
)
