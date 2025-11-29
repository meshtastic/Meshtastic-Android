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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootloaderWarningDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private object PreferencesKeys {
        val DISMISSED_BOOTLOADER_ADDRESSES = stringPreferencesKey("dismissed-bootloader-addresses")
    }

    private val dismissedAddressesFlow =
        dataStore.data.map { preferences ->
            val jsonString = preferences[PreferencesKeys.DISMISSED_BOOTLOADER_ADDRESSES] ?: return@map emptySet()

            runCatching { Json.decodeFromString<List<String>>(jsonString).toSet() }
                .onFailure { e ->
                    if (e is IllegalArgumentException || e is SerializationException) {
                        Timber.w(e, "Failed to parse dismissed bootloader warning addresses, resetting preference")
                    } else {
                        Timber.w(e, "Unexpected error while parsing dismissed bootloader warning addresses")
                    }
                }
                .getOrDefault(emptySet())
        }

    /** Returns true if the bootloader warning has been dismissed for the given [address]. */
    suspend fun isDismissed(address: String): Boolean = dismissedAddressesFlow.first().contains(address)

    /** Marks the bootloader warning as dismissed for the given [address]. */
    suspend fun dismiss(address: String) {
        val current = dismissedAddressesFlow.first()
        if (current.contains(address)) return

        val updated = (current + address).toList()
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISMISSED_BOOTLOADER_ADDRESSES] = Json.encodeToString(updated)
        }
    }
}
