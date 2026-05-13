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
package org.meshtastic.core.prefs.meshlog

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
import org.meshtastic.core.repository.MeshLogPrefs

@Single
class MeshLogPrefsImpl(
    @Named("MeshLogDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : MeshLogPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val retentionDays: StateFlow<Int> =
        dataStore.data
            .map { it[KEY_RETENTION_DAYS_PREF] ?: DEFAULT_RETENTION_DAYS }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_RETENTION_DAYS)

    override fun setRetentionDays(days: Int) {
        scope.launch { dataStore.edit { it[KEY_RETENTION_DAYS_PREF] = days } }
    }

    override val loggingEnabled: StateFlow<Boolean> =
        dataStore.data
            .map { it[KEY_LOGGING_ENABLED_PREF] ?: DEFAULT_LOGGING_ENABLED }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_LOGGING_ENABLED)

    override fun setLoggingEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_LOGGING_ENABLED_PREF] = enabled } }
    }

    companion object {
        const val RETENTION_DAYS_KEY = "meshlog_retention_days"
        const val LOGGING_ENABLED_KEY = "meshlog_logging_enabled"
        const val DEFAULT_RETENTION_DAYS = 30
        const val DEFAULT_LOGGING_ENABLED = true

        val KEY_RETENTION_DAYS_PREF = intPreferencesKey(RETENTION_DAYS_KEY)
        val KEY_LOGGING_ENABLED_PREF = booleanPreferencesKey(LOGGING_ENABLED_KEY)
    }
}
