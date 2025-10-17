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

package org.meshtastic.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

internal const val KEY_APP_INTRO_COMPLETED = "app_intro_completed"
internal const val KEY_THEME = "theme"

// Node list filters/sort
internal const val KEY_NODE_SORT = "node-sort-option"
internal const val KEY_INCLUDE_UNKNOWN = "include-unknown"
internal const val KEY_ONLY_ONLINE = "only-online"
internal const val KEY_ONLY_DIRECT = "only-direct"
internal const val KEY_SHOW_IGNORED = "show-ignored"

@Singleton
class UiPreferencesDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Start this flow eagerly, so app intro doesn't flash (when disabled) on cold app start.
    val appIntroCompleted: StateFlow<Boolean> =
        dataStore.prefStateFlow(key = APP_INTRO_COMPLETED, default = false, started = SharingStarted.Eagerly)

    // Default value for AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    val theme: StateFlow<Int> = dataStore.prefStateFlow(key = THEME, default = -1)

    val nodeSort: StateFlow<Int> = dataStore.prefStateFlow(key = NODE_SORT, default = -1)
    val includeUnknown: StateFlow<Boolean> = dataStore.prefStateFlow(key = INCLUDE_UNKNOWN, default = false)
    val onlyOnline: StateFlow<Boolean> = dataStore.prefStateFlow(key = ONLY_ONLINE, default = false)
    val onlyDirect: StateFlow<Boolean> = dataStore.prefStateFlow(key = ONLY_DIRECT, default = false)
    val showIgnored: StateFlow<Boolean> = dataStore.prefStateFlow(key = SHOW_IGNORED, default = false)

    fun setAppIntroCompleted(completed: Boolean) {
        dataStore.setPref(key = APP_INTRO_COMPLETED, value = completed)
    }

    fun setTheme(value: Int) {
        dataStore.setPref(key = THEME, value = value)
    }

    fun setNodeSort(value: Int) {
        dataStore.setPref(key = NODE_SORT, value = value)
    }

    fun setIncludeUnknown(value: Boolean) {
        dataStore.setPref(key = INCLUDE_UNKNOWN, value = value)
    }

    fun setOnlyOnline(value: Boolean) {
        dataStore.setPref(key = ONLY_ONLINE, value = value)
    }

    fun setOnlyDirect(value: Boolean) {
        dataStore.setPref(key = ONLY_DIRECT, value = value)
    }

    fun setShowIgnored(value: Boolean) {
        dataStore.setPref(key = SHOW_IGNORED, value = value)
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
        val NODE_SORT = intPreferencesKey(KEY_NODE_SORT)
        val INCLUDE_UNKNOWN = booleanPreferencesKey(KEY_INCLUDE_UNKNOWN)
        val ONLY_ONLINE = booleanPreferencesKey(KEY_ONLY_ONLINE)
        val ONLY_DIRECT = booleanPreferencesKey(KEY_ONLY_DIRECT)
        val SHOW_IGNORED = booleanPreferencesKey(KEY_SHOW_IGNORED)
    }
}
