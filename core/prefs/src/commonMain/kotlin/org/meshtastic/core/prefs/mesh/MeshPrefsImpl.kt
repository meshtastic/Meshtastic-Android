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
package org.meshtastic.core.prefs.mesh

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
import org.meshtastic.core.common.util.normalizeAddress
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.cachedFlow
import org.meshtastic.core.repository.MeshPrefs

@Single
class MeshPrefsImpl(
    @Named("MeshDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : MeshPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val storeForwardFlows = atomic(persistentMapOf<String?, Lazy<StateFlow<Int>>>())

    override val deviceAddress: StateFlow<String?> =
        dataStore.data
            .map { it[KEY_DEVICE_ADDRESS_PREF] ?: NO_DEVICE_SELECTED }
            .stateIn(scope, SharingStarted.Eagerly, NO_DEVICE_SELECTED)

    override fun setDeviceAddress(address: String?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (address == null) {
                    prefs.remove(KEY_DEVICE_ADDRESS_PREF)
                } else {
                    prefs[KEY_DEVICE_ADDRESS_PREF] = address
                }
            }
        }
    }

    override fun getStoreForwardLastRequest(address: String?): StateFlow<Int> = cachedFlow(storeForwardFlows, address) {
        val key = intPreferencesKey(storeForwardKey(address))
        dataStore.data.map { it[key] ?: 0 }.stateIn(scope, SharingStarted.Eagerly, 0)
    }

    override fun setStoreForwardLastRequest(address: String?, timestamp: Int) {
        scope.launch {
            dataStore.edit { prefs ->
                val key = intPreferencesKey(storeForwardKey(address))
                if (timestamp <= 0) {
                    prefs.remove(key)
                } else {
                    prefs[key] = timestamp
                }
            }
        }
    }

    private fun storeForwardKey(address: String?): String = "store-forward-last-request-${normalizeAddress(address)}"

    companion object {
        val KEY_DEVICE_ADDRESS_PREF = stringPreferencesKey("device_address")
    }
}

private const val NO_DEVICE_SELECTED = "n"
