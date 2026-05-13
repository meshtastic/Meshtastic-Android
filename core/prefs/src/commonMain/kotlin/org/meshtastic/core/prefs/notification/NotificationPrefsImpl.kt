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
package org.meshtastic.core.prefs.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import org.meshtastic.core.repository.NotificationPrefs

@Single
class NotificationPrefsImpl(
    @Named("UiDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : NotificationPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val messagesEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_MESSAGES_ENABLED] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setMessagesEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_MESSAGES_ENABLED] = enabled } }
    }

    override val nodeEventsEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_NODE_EVENTS_ENABLED] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setNodeEventsEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_NODE_EVENTS_ENABLED] = enabled } }
    }

    override val nodeEventsAutoDisabledForEvent: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_NODE_EVENTS_AUTO_DISABLED] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setNodeEventsAutoDisabledForEvent(disabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_NODE_EVENTS_AUTO_DISABLED] = disabled } }
    }

    override val lowBatteryEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_LOW_BATTERY_ENABLED] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setLowBatteryEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_LOW_BATTERY_ENABLED] = enabled } }
    }

    private companion object {
        val KEY_MESSAGES_ENABLED = booleanPreferencesKey("notif_messages_enabled")
        val KEY_NODE_EVENTS_ENABLED = booleanPreferencesKey("notif_node_events_enabled")
        val KEY_NODE_EVENTS_AUTO_DISABLED = booleanPreferencesKey("notif_node_events_auto_disabled_event")
        val KEY_LOW_BATTERY_ENABLED = booleanPreferencesKey("notif_low_battery_enabled")
    }
}
