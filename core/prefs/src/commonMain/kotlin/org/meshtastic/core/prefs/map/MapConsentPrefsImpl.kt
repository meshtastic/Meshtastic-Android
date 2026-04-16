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
package org.meshtastic.core.prefs.map

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import org.meshtastic.core.repository.MapConsentPrefs

@Single
class MapConsentPrefsImpl(
    @Named("MapConsentDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : MapConsentPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val consentFlows = atomic(persistentMapOf<Int?, Lazy<StateFlow<Boolean>>>())

    override fun shouldReportLocation(nodeNum: Int?): StateFlow<Boolean> = cachedFlow(consentFlows, nodeNum) {
        val key = booleanPreferencesKey(nodeNum.toString())
        dataStore.data.map { it[key] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)
    }

    override fun setShouldReportLocation(nodeNum: Int?, report: Boolean) {
        scope.launch { dataStore.edit { prefs -> prefs[booleanPreferencesKey(nodeNum.toString())] = report } }
    }
}
