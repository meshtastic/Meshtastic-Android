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
package org.meshtastic.core.prefs.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import org.meshtastic.core.repository.AnalyticsPrefs
import kotlin.uuid.Uuid

@Single
class AnalyticsPrefsImpl(
    @Named("AnalyticsDataStore") private val analyticsDataStore: DataStore<Preferences>,
    @Named("AppDataStore") private val appDataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : AnalyticsPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val analyticsAllowed: StateFlow<Boolean> =
        analyticsDataStore.data
            .map { it[KEY_ANALYTICS_ALLOWED_PREF] ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    override fun setAnalyticsAllowed(allowed: Boolean) {
        scope.launch { analyticsDataStore.edit { prefs -> prefs[KEY_ANALYTICS_ALLOWED_PREF] = allowed } }
    }

    override val installId: StateFlow<String> =
        appDataStore.data.map { it[KEY_INSTALL_ID_PREF] ?: "" }.stateIn(scope, SharingStarted.Eagerly, "")

    init {
        scope.launch {
            appDataStore.edit { prefs ->
                if (prefs[KEY_INSTALL_ID_PREF] == null) {
                    prefs[KEY_INSTALL_ID_PREF] = Uuid.random().toString()
                }
            }
        }
    }

    companion object {
        const val KEY_ANALYTICS_ALLOWED = "allowed"
        const val KEY_INSTALL_ID = "appPrefs_install_id"

        val KEY_ANALYTICS_ALLOWED_PREF = booleanPreferencesKey(KEY_ANALYTICS_ALLOWED)
        val KEY_INSTALL_ID_PREF = stringPreferencesKey(KEY_INSTALL_ID)
    }
}
