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

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Single
import org.meshtastic.core.datastore.di.CorePreferencesDataStore
import org.meshtastic.core.datastore.model.RecentAddress

/**
 * The stored addresses and names are user data, so no log line here carries the stored value or an exception message,
 * which kotlinx.serialization can fill with the input it failed on.
 */
@Single
open class RecentAddressesDataSource(private val dataStore: CorePreferencesDataStore) {
    private object PreferencesKeys {
        val RECENT_IP_ADDRESSES = stringPreferencesKey("recent-ip-addresses")
    }

    open val recentAddresses: Flow<List<RecentAddress>> =
        dataStore.data.map { preferences ->
            val jsonString = preferences[PreferencesKeys.RECENT_IP_ADDRESSES]
            if (jsonString != null) {
                try {
                    DatastoreJson.decodeFromString<List<RecentAddress>>(jsonString)
                } catch (e: IllegalArgumentException) {
                    // SerializationException is an IllegalArgumentException.
                    Logger.w { "Could not parse recent addresses (${e::class.simpleName}), trying legacy format" }
                    parseLegacyRecentAddresses(jsonString)
                }
            } else {
                emptyList()
            }
        }

    private fun parseLegacyRecentAddresses(jsonAddresses: String): List<RecentAddress> = try {
        DatastoreJson.parseToJsonElement(jsonAddresses).jsonArray.mapNotNull(::parseLegacyRecentAddress)
    } catch (e: IllegalArgumentException) {
        Logger.w { "Discarding unreadable recent addresses (${e::class.simpleName})" }
        emptyList()
    }

    private fun parseLegacyRecentAddress(item: kotlinx.serialization.json.JsonElement): RecentAddress? = when (item) {
        is JsonObject -> {
            val address = item["address"]?.jsonPrimitive?.contentOrNull
            val name = item["name"]?.jsonPrimitive?.contentOrNull
            if (address != null && name != null) {
                RecentAddress(address = address, name = name)
            } else {
                Logger.w { "Skipping malformed recent address object" }
                null
            }
        }

        is JsonPrimitive -> {
            val address = item.contentOrNull
            if (address != null) {
                RecentAddress(address = address, name = "Meshtastic")
            } else {
                Logger.w { "Skipping malformed recent address primitive" }
                null
            }
        }

        is JsonArray -> {
            Logger.w { "Skipping nested array in recent IP addresses" }
            null
        }
    }

    open suspend fun setRecentAddresses(addresses: List<RecentAddress>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECENT_IP_ADDRESSES] = DatastoreJson.encodeToString(addresses)
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
