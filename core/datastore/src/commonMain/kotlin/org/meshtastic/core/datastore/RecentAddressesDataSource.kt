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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.model.RecentAddress

@Single
open class RecentAddressesDataSource(@Named("CorePreferencesDataStore") private val dataStore: DataStore<Preferences>) {
    private object PreferencesKeys {
        val RECENT_IP_ADDRESSES = stringPreferencesKey("recent-ip-addresses")
    }

    open val recentAddresses: Flow<List<RecentAddress>> =
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
        val jsonArray = Json.parseToJsonElement(jsonAddresses).jsonArray
        return jsonArray.mapNotNull(::parseLegacyRecentAddress)
    }

    private fun parseLegacyRecentAddress(item: kotlinx.serialization.json.JsonElement): RecentAddress? = when (item) {
        is JsonObject -> {
            val address = item["address"]?.jsonPrimitive?.contentOrNull
            val name = item["name"]?.jsonPrimitive?.contentOrNull
            if (address != null && name != null) {
                RecentAddress(address = address, name = name)
            } else {
                Logger.w { "Skipping malformed recent address object: $item" }
                null
            }
        }

        is JsonPrimitive -> {
            val address = item.contentOrNull
            if (address != null) {
                RecentAddress(address = address, name = "Meshtastic")
            } else {
                Logger.w { "Skipping malformed recent address primitive: $item" }
                null
            }
        }

        is JsonArray -> {
            Logger.w { "Skipping nested array in recent IP addresses: $item" }
            null
        }
    }

    open suspend fun setRecentAddresses(addresses: List<RecentAddress>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECENT_IP_ADDRESSES] = Json.encodeToString(addresses)
        }
    }

    open suspend fun add(address: RecentAddress) {
        val currentAddresses = recentAddresses.first()
        val updatedList = mutableListOf(address)
        currentAddresses.filterTo(updatedList) { it.address != address.address }
        setRecentAddresses(updatedList.take(CACHE_CAPACITY))
    }

    open suspend fun remove(address: String) {
        val currentAddresses = recentAddresses.first()
        val updatedList = currentAddresses.filter { it.address != address }
        setRecentAddresses(updatedList)
    }
}

private const val CACHE_CAPACITY = 3
