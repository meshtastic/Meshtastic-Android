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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.datastore.di.CorePreferencesDataStore

@Single
open class BootloaderWarningDataSource(private val dataStore: CorePreferencesDataStore) {

    private object PreferencesKeys {
        val DISMISSED_BOOTLOADER_ADDRESSES = stringPreferencesKey("dismissed-bootloader-addresses")
    }

    private val dismissedAddressesFlow =
        dataStore.data.map { preferences ->
            val jsonString = preferences[PreferencesKeys.DISMISSED_BOOTLOADER_ADDRESSES] ?: return@map emptySet()

            // The stored value is a list of device addresses, so the log names only the exception type.
            safeCatching { DatastoreJson.decodeFromString<List<String>>(jsonString).toSet() }
                .onFailure { e ->
                    Logger.w { "Ignoring unreadable dismissed bootloader warning addresses (${e::class.simpleName})" }
                }
                .getOrDefault(emptySet())
        }

    /** Returns true if the bootloader warning has been dismissed for the given [address]. */
    open suspend fun isDismissed(address: String): Boolean = dismissedAddressesFlow.first().contains(address)

    /** Marks the bootloader warning as dismissed for the given [address]. */
    open suspend fun dismiss(address: String) {
        val current = dismissedAddressesFlow.first()
        if (current.contains(address)) return

        val updated = (current + address).toList()
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISMISSED_BOOTLOADER_ADDRESSES] = DatastoreJson.encodeToString(updated)
        }
    }
}
