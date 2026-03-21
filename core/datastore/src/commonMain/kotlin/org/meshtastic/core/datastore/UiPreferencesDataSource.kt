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
package org.meshtastic.core.datastore

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
import org.meshtastic.core.common.UiPreferences
import org.meshtastic.core.common.util.ioDispatcher

const val KEY_APP_INTRO_COMPLETED = "app_intro_completed"
const val KEY_THEME = "theme"
const val KEY_LOCALE = "locale"

// Node list filters/sort
const val KEY_NODE_SORT = "node-sort-option"
const val KEY_INCLUDE_UNKNOWN = "include-unknown"
const val KEY_EXCLUDE_INFRASTRUCTURE = "exclude-infrastructure"
const val KEY_ONLY_ONLINE = "only-online"
const val KEY_ONLY_DIRECT = "only-direct"
const val KEY_SHOW_IGNORED = "show-ignored"
const val KEY_EXCLUDE_MQTT = "exclude-mqtt"

@Single
@Suppress("TooManyFunctions") // One setter per preference field — inherently grows with preferences.
open class UiPreferencesDataSource(@Named("CorePreferencesDataStore") private val dataStore: DataStore<Preferences>) :
    UiPreferences {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // Start this flow eagerly, so app intro doesn't flash (when disabled) on cold app start.
    override val appIntroCompleted: StateFlow<Boolean> =
        dataStore.prefStateFlow(key = APP_INTRO_COMPLETED, default = false, started = SharingStarted.Eagerly)

    // Default value for AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    override val theme: StateFlow<Int> = dataStore.prefStateFlow(key = THEME, default = -1)

    /** Persisted language tag (e.g. "de", "pt-BR"). Empty string means system default. */
    override val locale: StateFlow<String> =
        dataStore.prefStateFlow(key = LOCALE, default = "", started = SharingStarted.Eagerly)

    override fun setLocale(languageTag: String) {
        dataStore.setPref(key = LOCALE, value = languageTag)
    }

    override val nodeSort: StateFlow<Int> = dataStore.prefStateFlow(key = NODE_SORT, default = -1)
    override val includeUnknown: StateFlow<Boolean> = dataStore.prefStateFlow(key = INCLUDE_UNKNOWN, default = false)
    override val excludeInfrastructure: StateFlow<Boolean> =
        dataStore.prefStateFlow(key = EXCLUDE_INFRASTRUCTURE, default = false)
    override val onlyOnline: StateFlow<Boolean> = dataStore.prefStateFlow(key = ONLY_ONLINE, default = false)
    override val onlyDirect: StateFlow<Boolean> = dataStore.prefStateFlow(key = ONLY_DIRECT, default = false)
    override val showIgnored: StateFlow<Boolean> = dataStore.prefStateFlow(key = SHOW_IGNORED, default = false)
    override val excludeMqtt: StateFlow<Boolean> = dataStore.prefStateFlow(key = EXCLUDE_MQTT, default = false)

    override fun setAppIntroCompleted(completed: Boolean) {
        dataStore.setPref(key = APP_INTRO_COMPLETED, value = completed)
    }

    override fun setTheme(value: Int) {
        dataStore.setPref(key = THEME, value = value)
    }

    override fun setNodeSort(value: Int) {
        dataStore.setPref(key = NODE_SORT, value = value)
    }

    override fun setIncludeUnknown(value: Boolean) {
        dataStore.setPref(key = INCLUDE_UNKNOWN, value = value)
    }

    override fun setExcludeInfrastructure(value: Boolean) {
        dataStore.setPref(key = EXCLUDE_INFRASTRUCTURE, value = value)
    }

    override fun setOnlyOnline(value: Boolean) {
        dataStore.setPref(key = ONLY_ONLINE, value = value)
    }

    override fun setOnlyDirect(value: Boolean) {
        dataStore.setPref(key = ONLY_DIRECT, value = value)
    }

    override fun setShowIgnored(value: Boolean) {
        dataStore.setPref(key = SHOW_IGNORED, value = value)
    }

    override fun setExcludeMqtt(value: Boolean) {
        dataStore.setPref(key = EXCLUDE_MQTT, value = value)
    }

    override fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean> =
        dataStore.prefStateFlow(key = booleanPreferencesKey("provide-location-$nodeNum"), default = false)

    override fun setShouldProvideNodeLocation(nodeNum: Int, provide: Boolean) {
        dataStore.setPref(key = booleanPreferencesKey("provide-location-$nodeNum"), value = provide)
    }

    private fun <T : Any> DataStore<Preferences>.prefStateFlow(
        key: Preferences.Key<T>,
        default: T,
        started: SharingStarted = SharingStarted.Lazily,
    ): StateFlow<T> = data.map { it[key] ?: default }.stateIn(scope = scope, started = started, initialValue = default)

    private fun <T : Any> DataStore<Preferences>.setPref(key: Preferences.Key<T>, value: T) {
        scope.launch { edit { it[key] = value } }
    }

    private companion object {
        val APP_INTRO_COMPLETED = booleanPreferencesKey(KEY_APP_INTRO_COMPLETED)
        val THEME = intPreferencesKey(KEY_THEME)
        val LOCALE = stringPreferencesKey(KEY_LOCALE)
        val NODE_SORT = intPreferencesKey(KEY_NODE_SORT)
        val INCLUDE_UNKNOWN = booleanPreferencesKey(KEY_INCLUDE_UNKNOWN)
        val EXCLUDE_INFRASTRUCTURE = booleanPreferencesKey(KEY_EXCLUDE_INFRASTRUCTURE)
        val ONLY_ONLINE = booleanPreferencesKey(KEY_ONLY_ONLINE)
        val ONLY_DIRECT = booleanPreferencesKey(KEY_ONLY_DIRECT)
        val SHOW_IGNORED = booleanPreferencesKey(KEY_SHOW_IGNORED)
        val EXCLUDE_MQTT = booleanPreferencesKey(KEY_EXCLUDE_MQTT)
    }
}
