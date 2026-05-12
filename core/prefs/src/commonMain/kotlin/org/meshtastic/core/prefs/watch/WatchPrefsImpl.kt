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
package org.meshtastic.core.prefs.watch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.WatchPrefs

@Single
class WatchPrefsImpl(
    @Named("WatchDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : WatchPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val pushToWatchEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_PUSH_TO_WATCH] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setPushToWatchEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { prefs -> prefs[KEY_PUSH_TO_WATCH] = enabled } }
    }

    override val syncNodesEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SYNC_NODES] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setSyncNodesEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { prefs -> prefs[KEY_SYNC_NODES] = enabled } }
    }

    override val syncMessagesEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SYNC_MESSAGES] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setSyncMessagesEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { prefs -> prefs[KEY_SYNC_MESSAGES] = enabled } }
    }

    override val mirrorNotificationsEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_MIRROR_NOTIFICATIONS] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setMirrorNotificationsEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { prefs -> prefs[KEY_MIRROR_NOTIFICATIONS] = enabled } }
    }

    override val highContrastModeEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_HIGH_CONTRAST_MODE] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setHighContrastModeEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { prefs -> prefs[KEY_HIGH_CONTRAST_MODE] = enabled } }
    }

    override val syncRequest: StateFlow<Long> =
        dataStore.data.map { it[KEY_SYNC_REQUEST] ?: 0L }.stateIn(scope, SharingStarted.Eagerly, 0L)

    override fun requestSync() {
        scope.launch { dataStore.edit { prefs -> prefs[KEY_SYNC_REQUEST] = nowMillis } }
    }

    companion object {
        val KEY_PUSH_TO_WATCH = booleanPreferencesKey("watch_push_enabled")
        val KEY_SYNC_NODES = booleanPreferencesKey("watch_sync_nodes")
        val KEY_SYNC_MESSAGES = booleanPreferencesKey("watch_sync_messages")
        val KEY_MIRROR_NOTIFICATIONS = booleanPreferencesKey("watch_mirror_notifications")
        val KEY_HIGH_CONTRAST_MODE = booleanPreferencesKey("watch_high_contrast_mode")
        val KEY_SYNC_REQUEST = longPreferencesKey("watch_sync_request")
    }
}
