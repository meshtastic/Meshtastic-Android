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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.meshtastic.core.datastore.model.RecentAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentAddressesDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private object PreferencesKeys {
        val RECENT_IP_ADDRESSES = stringPreferencesKey("recent-ip-addresses")
    }

    val recentAddresses: Flow<List<RecentAddress>> =
        dataStore.data.map { preferences ->
            val jsonString = preferences[PreferencesKeys.RECENT_IP_ADDRESSES]
            if (jsonString != null) {
                try {
                    Json.decodeFromString<List<RecentAddress>>(jsonString)
                } catch (e: IllegalArgumentException) {
                    Logger.w { "Could not parse recent addresses, falling back to legacy parsing: ${e.message}" }
                    // Fallback to legacy parsing
                    parseLegacyRecentAddresses(jsonString)
                } catch (e: SerializationException) {
                    Logger.w { "Could not parse recent addresses, falling back to legacy parsing: ${e.message}" }
                    // Fallback to legacy parsing
                    parseLegacyRecentAddresses(jsonString)
                }
            } else {
                emptyList()
            }
        }

    private fun parseLegacyRecentAddresses(jsonAddresses: String): List<RecentAddress> {
        val jsonArray = JSONArray(jsonAddresses)
        return (0 until jsonArray.length()).mapNotNull { i ->
            when (val item = jsonArray.get(i)) {
                is JSONObject -> {
                    // Modern format: JSONObject with address and name
                    RecentAddress(address = item.getString("address"), name = item.getString("name"))
                }
                is String -> {
                    // Old format: just the address string
                    RecentAddress(address = item, name = "Meshtastic")
                }
                else -> {
                    // Unknown format, log or handle as an error if necessary
                    Logger.w { "Unknown item type in recent IP addresses: $item" }
                    null
                }
            }
        }
    }

    suspend fun setRecentAddresses(addresses: List<RecentAddress>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECENT_IP_ADDRESSES] = Json.encodeToString(addresses)
        }
    }

    suspend fun add(address: RecentAddress) {
        val currentAddresses = recentAddresses.first()
        val updatedList = mutableListOf(address)
        currentAddresses.filterTo(updatedList) { it.address != address.address }
        setRecentAddresses(updatedList.take(CACHE_CAPACITY))
    }

    suspend fun remove(address: String) {
        val currentAddresses = recentAddresses.first()
        val updatedList = currentAddresses.filter { it.address != address }
        setRecentAddresses(updatedList)
    }
}

private const val CACHE_CAPACITY = 3
