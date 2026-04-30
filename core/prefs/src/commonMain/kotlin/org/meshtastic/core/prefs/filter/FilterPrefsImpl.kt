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
package org.meshtastic.core.prefs.filter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import org.meshtastic.core.repository.FilterPrefs

@Single
class FilterPrefsImpl(
    @Named("FilterDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : FilterPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val filterEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_FILTER_ENABLED_PREF] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setFilterEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { prefs -> prefs[KEY_FILTER_ENABLED_PREF] = enabled } }
    }

    override val filterWords: StateFlow<Set<String>> =
        dataStore.data
            .map { it[KEY_FILTER_WORDS_PREF] ?: emptySet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun setFilterWords(words: Set<String>) {
        scope.launch { dataStore.edit { prefs -> prefs[KEY_FILTER_WORDS_PREF] = words } }
    }

    companion object {
        const val KEY_FILTER_ENABLED = "filter_enabled"
        const val KEY_FILTER_WORDS = "filter_words"
        const val FILTER_PREFS_NAME = "filter-prefs"

        val KEY_FILTER_ENABLED_PREF = booleanPreferencesKey(KEY_FILTER_ENABLED)
        val KEY_FILTER_WORDS_PREF = stringSetPreferencesKey(KEY_FILTER_WORDS)
    }
}
