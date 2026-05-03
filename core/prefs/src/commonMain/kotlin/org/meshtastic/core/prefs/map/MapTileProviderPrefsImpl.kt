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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MapTileProviderPrefs

@Single
class MapTileProviderPrefsImpl(
    @Named("MapTileProviderDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : MapTileProviderPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val customTileProviders: StateFlow<String?> =
        dataStore.data.map { it[KEY_CUSTOM_PROVIDERS_PREF] }.stateIn(scope, SharingStarted.Eagerly, null)

    override fun setCustomTileProviders(providers: String?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (providers == null) {
                    prefs.remove(KEY_CUSTOM_PROVIDERS_PREF)
                } else {
                    prefs[KEY_CUSTOM_PROVIDERS_PREF] = providers
                }
            }
        }
    }

    companion object {
        const val KEY_CUSTOM_PROVIDERS = "custom_tile_providers"
        val KEY_CUSTOM_PROVIDERS_PREF = stringPreferencesKey(KEY_CUSTOM_PROVIDERS)
    }
}
