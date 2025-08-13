/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.repository.map

import android.content.Context
import androidx.core.content.edit
import com.geeksville.mesh.di.IoDispatcher
import com.geeksville.mesh.ui.map.CustomTileProviderConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY_CUSTOM_TILE_PROVIDERS = "custom_tile_providers"
private const val PREFS_NAME_TILE = "map_tile_provider_prefs"

@Singleton
class SharedPreferencesCustomTileProviderRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CustomTileProviderRepository {

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME_TILE, Context.MODE_PRIVATE)

    private val customTileProvidersStateFlow = MutableStateFlow<List<CustomTileProviderConfig>>(emptyList())

    init {
        loadDataFromPrefs()
    }

    private fun loadDataFromPrefs() {
        val jsonString = sharedPreferences.getString(KEY_CUSTOM_TILE_PROVIDERS, null)
        if (jsonString != null) {
            try {
                customTileProvidersStateFlow.value = json.decodeFromString<List<CustomTileProviderConfig>>(jsonString)
            } catch (e: SerializationException) {
                Timber.tag("TileRepo").e(e, "Error deserializing tile providers")
                customTileProvidersStateFlow.value = emptyList()
            }
        } else {
            customTileProvidersStateFlow.value = emptyList()
        }
    }

    private suspend fun saveDataToPrefs(providers: List<CustomTileProviderConfig>) {
        withContext(ioDispatcher) {
            try {
                val jsonString = json.encodeToString(providers)
                sharedPreferences.edit { putString(KEY_CUSTOM_TILE_PROVIDERS, jsonString) }
            } catch (e: SerializationException) {
                Timber.tag("TileRepo").e(e, "Error serializing tile providers")
            }
        }
    }

    override fun getCustomTileProviders(): Flow<List<CustomTileProviderConfig>> =
        customTileProvidersStateFlow.asStateFlow()

    override suspend fun addCustomTileProvider(config: CustomTileProviderConfig) {
        val newList = customTileProvidersStateFlow.value + config
        customTileProvidersStateFlow.value = newList
        saveDataToPrefs(newList)
    }

    override suspend fun updateCustomTileProvider(config: CustomTileProviderConfig) {
        val newList = customTileProvidersStateFlow.value.map { if (it.id == config.id) config else it }
        customTileProvidersStateFlow.value = newList
        saveDataToPrefs(newList)
    }

    override suspend fun deleteCustomTileProvider(configId: String) {
        val newList = customTileProvidersStateFlow.value.filterNot { it.id == configId }
        customTileProvidersStateFlow.value = newList
        saveDataToPrefs(newList)
    }

    override suspend fun getCustomTileProviderById(configId: String): CustomTileProviderConfig? =
        customTileProvidersStateFlow.value.find { it.id == configId }
}
