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
package org.meshtastic.core.prefs.discovery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
import org.meshtastic.core.repository.DiscoveryPrefs

@Single
class DiscoveryPrefsImpl(
    @Named("UiDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : DiscoveryPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val dwellMinutes: StateFlow<Int> =
        dataStore.data
            .map { it[KEY_DWELL_MINUTES] ?: DiscoveryPrefs.DEFAULT_DWELL_MINUTES }
            .stateIn(scope, SharingStarted.Eagerly, DiscoveryPrefs.DEFAULT_DWELL_MINUTES)

    override fun setDwellMinutes(minutes: Int) {
        scope.launch { dataStore.edit { it[KEY_DWELL_MINUTES] = minutes } }
    }

    override val selectedPresets: StateFlow<Set<String>> =
        dataStore.data
            .map { prefs ->
                val raw = prefs[KEY_SELECTED_PRESETS] ?: ""
                if (raw.isBlank()) emptySet() else raw.split(PRESET_DELIMITER).toSet()
            }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun setSelectedPresets(presets: Set<String>) {
        scope.launch { dataStore.edit { it[KEY_SELECTED_PRESETS] = presets.joinToString(PRESET_DELIMITER) } }
    }

    override val aiEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_AI_ENABLED] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setAiEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_AI_ENABLED] = enabled } }
    }

    override val topologyOverlayEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_TOPOLOGY_OVERLAY] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setTopologyOverlayEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_TOPOLOGY_OVERLAY] = enabled } }
    }

    companion object {
        private val KEY_DWELL_MINUTES = intPreferencesKey("discovery_dwell_minutes")
        private val KEY_SELECTED_PRESETS = stringPreferencesKey("discovery_selected_presets")
        private val KEY_AI_ENABLED = booleanPreferencesKey("discovery_ai_enabled")
        private val KEY_TOPOLOGY_OVERLAY = booleanPreferencesKey("discovery_topology_overlay")
        private const val PRESET_DELIMITER = ","
    }
}
