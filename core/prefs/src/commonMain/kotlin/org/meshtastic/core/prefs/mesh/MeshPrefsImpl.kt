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
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshPrefs
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Single
class MeshPrefsImpl(
    @Named("MeshDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : MeshPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val locationFlows = ConcurrentHashMap<Int?, StateFlow<Boolean>>()
    private val storeForwardFlows = ConcurrentHashMap<String?, StateFlow<Int>>()

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

    override fun shouldProvideNodeLocation(nodeNum: Int?): StateFlow<Boolean> = locationFlows.getOrPut(nodeNum) {
        val key = booleanPreferencesKey(provideLocationKey(nodeNum))
        dataStore.data.map { it[key] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)
    }

    override fun setShouldProvideNodeLocation(nodeNum: Int?, value: Boolean) {
        scope.launch { dataStore.edit { prefs -> prefs[booleanPreferencesKey(provideLocationKey(nodeNum))] = value } }
    }

    override fun getStoreForwardLastRequest(address: String?): StateFlow<Int> = storeForwardFlows.getOrPut(address) {
        val key = intPreferencesKey(storeForwardKey(address))
        dataStore.data.map { it[key] ?: 0 }.stateIn(scope, SharingStarted.Eagerly, 0)
    }

    override fun setStoreForwardLastRequest(address: String?, value: Int) {
        scope.launch {
            dataStore.edit { prefs ->
                val key = intPreferencesKey(storeForwardKey(address))
                if (value <= 0) {
                    prefs.remove(key)
                } else {
                    prefs[key] = value
                }
            }
        }
    }

    private fun provideLocationKey(nodeNum: Int?) = "provide-location-$nodeNum"

    private fun storeForwardKey(address: String?): String = "store-forward-last-request-${normalizeAddress(address)}"

    private fun normalizeAddress(address: String?): String {
        val raw = address?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            raw == null -> "DEFAULT"
            raw.equals(NO_DEVICE_SELECTED, ignoreCase = true) -> "DEFAULT"
            else -> raw.uppercase(Locale.US).replace(":", "")
        }
    }

    companion object {
        val KEY_DEVICE_ADDRESS_PREF = stringPreferencesKey("device_address")
    }
}

private const val NO_DEVICE_SELECTED = "n"
