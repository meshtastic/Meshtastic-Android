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
package org.meshtastic.core.prefs.radio

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
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.RadioDataStore
import org.meshtastic.core.repository.RadioPrefs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadioPrefsImpl
@Inject
constructor(
    @RadioDataStore private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : RadioPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val devAddr: StateFlow<String?> =
        dataStore.data
            .map { it[KEY_DEV_ADDR_PREF] }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override fun setDevAddr(address: String?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (address == null) prefs.remove(KEY_DEV_ADDR_PREF)
                else prefs[KEY_DEV_ADDR_PREF] = address
            }
        }
    }

    companion object {
        val KEY_DEV_ADDR_PREF = stringPreferencesKey("devAddr2")
    }
}
