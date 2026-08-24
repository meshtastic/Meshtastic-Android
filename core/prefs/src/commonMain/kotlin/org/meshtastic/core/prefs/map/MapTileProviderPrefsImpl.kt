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
package org.meshtastic.core.prefs.map

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.MapTileProviderDataStore
import org.meshtastic.core.repository.MapTileProviderPrefs

@Single
class MapTileProviderPrefsImpl(private val dataStore: MapTileProviderDataStore, dispatchers: CoroutineDispatchers) :
    MapTileProviderPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val customTileProviders: StateFlow<String?> =
        dataStore.data.map { it[KEY_CUSTOM_PROVIDERS_PREF] }.stateIn(scope, SharingStarted.Eagerly, null)

    override val selectedCustomTileProviderId: StateFlow<String?> =
        dataStore.data.map { it[KEY_SELECTED_CUSTOM_PROVIDER_ID_PREF] }.stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun awaitCustomTileProviders(): String? = dataStore.data.first()[KEY_CUSTOM_PROVIDERS_PREF]

    override suspend fun awaitSelectedCustomTileProviderId(): String? =
        dataStore.data.first()[KEY_SELECTED_CUSTOM_PROVIDER_ID_PREF]

    override suspend fun setCustomTileProviders(providers: String?) {
        dataStore.edit { prefs ->
            if (providers == null) {
                prefs.remove(KEY_CUSTOM_PROVIDERS_PREF)
            } else {
                prefs[KEY_CUSTOM_PROVIDERS_PREF] = providers
            }
        }
    }

    override suspend fun setSelectedCustomTileProviderId(providerId: String?) {
        dataStore.edit { prefs ->
            if (providerId == null) {
                prefs.remove(KEY_SELECTED_CUSTOM_PROVIDER_ID_PREF)
            } else {
                prefs[KEY_SELECTED_CUSTOM_PROVIDER_ID_PREF] = providerId
            }
        }
    }

    companion object {
        const val KEY_CUSTOM_PROVIDERS = "custom_tile_providers"
        const val KEY_SELECTED_CUSTOM_PROVIDER_ID = "selected_custom_tile_provider_id"
        val KEY_CUSTOM_PROVIDERS_PREF = stringPreferencesKey(KEY_CUSTOM_PROVIDERS)
        val KEY_SELECTED_CUSTOM_PROVIDER_ID_PREF = stringPreferencesKey(KEY_SELECTED_CUSTOM_PROVIDER_ID)
    }
}
