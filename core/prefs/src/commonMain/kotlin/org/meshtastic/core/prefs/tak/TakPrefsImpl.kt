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
package org.meshtastic.core.prefs.tak

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import org.meshtastic.core.repository.TakPrefs

@Single(binds = [TakPrefs::class])
class TakPrefsImpl(
    @Named("UiDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : TakPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val isTakServerEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_TAK_SERVER_ENABLED] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setTakServerEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { prefs -> prefs[KEY_TAK_SERVER_ENABLED] = enabled } }
    }

    companion object {
        val KEY_TAK_SERVER_ENABLED = booleanPreferencesKey("tak_server_enabled")
    }
}
