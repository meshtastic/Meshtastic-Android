/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.prefs.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.atomicfu.atomic
import kotlinx.collections.immutable.persistentMapOf
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
import org.meshtastic.core.prefs.cachedFlow
import org.meshtastic.core.repository.UiPrefs

@Single
@Suppress("TooManyFunctions")
class UiPrefsImpl(
    @Named("UiDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : UiPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    // Maps nodeNum to a flow for the for the "provide-location-nodeNum" pref
    private val provideNodeLocationFlows = atomic(persistentMapOf<Int, Lazy<StateFlow<Boolean>>>())

    override val appIntroCompleted: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_APP_INTRO_COMPLETED] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setAppIntroCompleted(completed: Boolean) {
        scope.launch { dataStore.edit { it[KEY_APP_INTRO_COMPLETED] = completed } }
    }

    override val theme: StateFlow<Int> =
        dataStore.data.map { it[KEY_THEME] ?: -1 }.stateIn(scope, SharingStarted.Lazily, -1)

    override fun setTheme(value: Int) {
        scope.launch { dataStore.edit { it[KEY_THEME] = value } }
    }

    override val contrastLevel: StateFlow<Int> =
        dataStore.data.map { it[KEY_CONTRAST_LEVEL] ?: 0 }.stateIn(scope, SharingStarted.Lazily, 0)

    override fun setContrastLevel(value: Int) {
        scope.launch { dataStore.edit { it[KEY_CONTRAST_LEVEL] = value } }
    }

    override val locale: StateFlow<String> =
        dataStore.data.map { it[KEY_LOCALE] ?: "" }.stateIn(scope, SharingStarted.Eagerly, "")

    override fun setLocale(languageTag: String) {
        scope.launch { dataStore.edit { it[KEY_LOCALE] = languageTag } }
    }

    override val nodeSort: StateFlow<Int> =
        dataStore.data.map { it[KEY_NODE_SORT] ?: -1 }.stateIn(scope, SharingStarted.Lazily, -1)

    override fun setNodeSort(value: Int) {
        scope.launch { dataStore.edit { it[KEY_NODE_SORT] = value } }
    }

    override val includeUnknown: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_INCLUDE_UNKNOWN] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setIncludeUnknown(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_INCLUDE_UNKNOWN] = value } }
    }

    override val excludeInfrastructure: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_EXCLUDE_INFRASTRUCTURE] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setExcludeInfrastructure(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_EXCLUDE_INFRASTRUCTURE] = value } }
    }

    override val onlyOnline: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_ONLY_ONLINE] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setOnlyOnline(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_ONLY_ONLINE] = value } }
    }

    override val onlyDirect: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_ONLY_DIRECT] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setOnlyDirect(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_ONLY_DIRECT] = value } }
    }

    override val showIgnored: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_IGNORED] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setShowIgnored(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_IGNORED] = value } }
    }

    override val excludeMqtt: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_EXCLUDE_MQTT] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setExcludeMqtt(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_EXCLUDE_MQTT] = value } }
    }

    override val hasShownNotPairedWarning: StateFlow<Boolean> =
        dataStore.data
            .map { it[KEY_HAS_SHOWN_NOT_PAIRED_WARNING_PREF] ?: false }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override fun setHasShownNotPairedWarning(shown: Boolean) {
        scope.launch { dataStore.edit { it[KEY_HAS_SHOWN_NOT_PAIRED_WARNING_PREF] = shown } }
    }

    override val showQuickChat: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_QUICK_CHAT_PREF] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setShowQuickChat(show: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_QUICK_CHAT_PREF] = show } }
    }

    override fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean> =
        cachedFlow(provideNodeLocationFlows, nodeNum) {
            val key = booleanPreferencesKey(provideLocationKey(nodeNum))
            dataStore.data.map { it[key] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)
        }

    override fun setShouldProvideNodeLocation(nodeNum: Int, provide: Boolean) {
        scope.launch { dataStore.edit { it[booleanPreferencesKey(provideLocationKey(nodeNum))] = provide } }
    }

    private fun provideLocationKey(nodeNum: Int) = "provide-location-$nodeNum"

    companion object {
        val KEY_HAS_SHOWN_NOT_PAIRED_WARNING_PREF = booleanPreferencesKey("has_shown_not_paired_warning")
        val KEY_SHOW_QUICK_CHAT_PREF = booleanPreferencesKey("show-quick-chat")

        val KEY_APP_INTRO_COMPLETED = booleanPreferencesKey("app_intro_completed")
        val KEY_THEME = intPreferencesKey("theme")
        val KEY_CONTRAST_LEVEL = intPreferencesKey("contrast-level")
        val KEY_LOCALE = stringPreferencesKey("locale")
        val KEY_NODE_SORT = intPreferencesKey("node-sort-option")
        val KEY_INCLUDE_UNKNOWN = booleanPreferencesKey("include-unknown")
        val KEY_EXCLUDE_INFRASTRUCTURE = booleanPreferencesKey("exclude-infrastructure")
        val KEY_ONLY_ONLINE = booleanPreferencesKey("only-online")
        val KEY_ONLY_DIRECT = booleanPreferencesKey("only-direct")
        val KEY_SHOW_IGNORED = booleanPreferencesKey("show-ignored")
        val KEY_EXCLUDE_MQTT = booleanPreferencesKey("exclude-mqtt")
    }
}
