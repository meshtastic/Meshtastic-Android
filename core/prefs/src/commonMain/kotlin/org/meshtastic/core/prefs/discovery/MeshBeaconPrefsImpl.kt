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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshBeaconPrefs

@Single
class MeshBeaconPrefsImpl(
    @Named("UiDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : MeshBeaconPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val storedBeacons: StateFlow<List<String>> =
        dataStore.data
            .map { prefs ->
                val raw = prefs[KEY_STORED_BEACONS] ?: ""
                if (raw.isBlank()) emptyList() else raw.split(RECORD_DELIMITER)
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Single conflated writer: rapid add()/dismiss() calls each publish the full latest list here, and one collector
    // serializes the DataStore writes (latest-wins). Avoids launch-per-call races that could persist a stale snapshot.
    // runCatching keeps a best-effort write failure (e.g. disk I/O) from escaping as an uncaught coroutine exception —
    // the next add/dismiss retries, and on restart we hydrate from the last successful write.
    private val pendingWrite = MutableStateFlow<List<String>?>(null)

    init {
        scope.launch {
            pendingWrite.filterNotNull().collectLatest { records ->
                runCatching { dataStore.edit { it[KEY_STORED_BEACONS] = records.joinToString(RECORD_DELIMITER) } }
            }
        }
    }

    override fun setStoredBeacons(records: List<String>) {
        pendingWrite.value = records
    }

    private companion object {
        val KEY_STORED_BEACONS = stringPreferencesKey("mesh_beacon_stored_offers")

        // Newline can never appear in a beacon record (fields are numeric + base64), so it is a safe row separator.
        const val RECORD_DELIMITER = "\n"
    }
}
