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

import androidx.datastore.preferences.core.Preferences
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
        val LAST_BLE_NODE = stringPreferencesKey("last-ble-node-recovery-candidate")
    }

    /** The pending recovery record, or `null` when no interrupted update is outstanding. */
    open val pending: Flow<PendingFirmwareRecovery?> = decoded(PreferencesKeys.PENDING_RECOVERY)

    /**
     * The last node this app completed a BLE handshake with, kept so that node can be recovered when it turns up in its
     * bootloader without the app having started the update that stranded it. Not shown by itself; a bootloader seen at
     * its address promotes it to [pending] via [promoteLastBleNode].
     */
    open val lastBleNode: Flow<PendingFirmwareRecovery?> = decoded(PreferencesKeys.LAST_BLE_NODE)

    private fun decoded(key: Preferences.Key<String>): Flow<PendingFirmwareRecovery?> =
        dataStore.data.map { preferences ->
            val jsonString = preferences[key] ?: return@map null
            // The stored record holds the device address and name, so the log names only the exception type.
            safeCatching { DatastoreJson.decodeFromString<PendingFirmwareRecovery>(jsonString) }
                .onFailure { e -> Logger.w { "Ignoring unreadable ${key.name} (${e::class.simpleName})" } }
                .getOrNull()
        }

    /** Records [node] as the last BLE node, replacing any previous one. */
    open suspend fun setLastBleNode(node: PendingFirmwareRecovery) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_BLE_NODE] = DatastoreJson.encodeToString(node)
        }
    }

    /** Makes [lastBleNode] the pending recovery, unless one is already pending. */
    open suspend fun promoteLastBleNode() {
        dataStore.edit { preferences ->
            val last = preferences[PreferencesKeys.LAST_BLE_NODE] ?: return@edit
            if (preferences[PreferencesKeys.PENDING_RECOVERY] == null) {
                preferences[PreferencesKeys.PENDING_RECOVERY] = last
            }
        }
    }

    /**
     * Clears both records. Clearing only [pending] would let the next bootloader sighting promote [lastBleNode]
     * straight back; it returns on the next successful handshake.
     */
    open suspend fun dismiss() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.PENDING_RECOVERY)
            preferences.remove(PreferencesKeys.LAST_BLE_NODE)
        }
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
