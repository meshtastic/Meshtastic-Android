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
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.datastore.di.CorePreferencesDataStore
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery

/**
 * Persists a single [PendingFirmwareRecovery] record for a firmware update that may have left a device stranded in
 * bootloader mode. Only the most recent trigger is retained (updates are one-device-at-a-time in practice).
 */
@Single
open class FirmwareRecoveryDataSource(private val dataStore: CorePreferencesDataStore) {

    private object PreferencesKeys {
        val PENDING_RECOVERY = stringPreferencesKey("pending-firmware-recovery")
    }

    /** The pending recovery record, or `null` when no interrupted update is outstanding. */
    open val pending: Flow<PendingFirmwareRecovery?> =
        dataStore.data.map { preferences ->
            val jsonString = preferences[PreferencesKeys.PENDING_RECOVERY] ?: return@map null
            // The stored record holds the device address and name, so the log names only the exception type.
            safeCatching { DatastoreJson.decodeFromString<PendingFirmwareRecovery>(jsonString) }
                .onFailure { e ->
                    Logger.w { "Ignoring unreadable pending firmware recovery (${e::class.simpleName})" }
                }
                .getOrNull()
        }

    /** Records [recovery] as the outstanding interrupted update, replacing any previous record. */
    open suspend fun set(recovery: PendingFirmwareRecovery) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PENDING_RECOVERY] = DatastoreJson.encodeToString(recovery)
        }
    }

    /** Clears the outstanding recovery record (update finished, or the device returned on its own). */
    open suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(PreferencesKeys.PENDING_RECOVERY) }
    }
}
