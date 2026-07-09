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
package org.meshtastic.core.prefs.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.atomicfu.atomic
import kotlinx.collections.immutable.persistentMapOf
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
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.prefs.cachedFlow
import org.meshtastic.core.repository.UiPrefs

@Single
@Suppress("TooManyFunctions")
class UiPrefsImpl(
    @Named("UiDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : UiPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    // Maps nodeNum to a flow for the for the "provide-location-nodeNum" pref
    private val provideNodeLocationFlows = atomic(persistentMapOf<Int, Lazy<StateFlow<Boolean>>>())

    override val appIntroCompleted: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_APP_INTRO_COMPLETED] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setAppIntroCompleted(completed: Boolean) {
        scope.launch { dataStore.edit { it[KEY_APP_INTRO_COMPLETED] = completed } }
    }

    override val theme: StateFlow<Int> =
        dataStore.data.map { it[KEY_THEME] ?: -1 }.stateIn(scope, SharingStarted.Lazily, -1)

    override fun setTheme(value: Int) {
        scope.launch { dataStore.edit { it[KEY_THEME] = value } }
    }

    override val locale: StateFlow<String> =
        dataStore.data.map { it[KEY_LOCALE] ?: "" }.stateIn(scope, SharingStarted.Eagerly, "")

    override fun setLocale(languageTag: String) {
        scope.launch { dataStore.edit { it[KEY_LOCALE] = languageTag } }
    }

    override val nodeSort: StateFlow<Int> =
        dataStore.data.map { it[KEY_NODE_SORT] ?: -1 }.stateIn(scope, SharingStarted.Lazily, -1)

    override fun setNodeSort(value: Int) {
        scope.launch { dataStore.edit { it[KEY_NODE_SORT] = value } }
    }

    override val includeUnknown: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_INCLUDE_UNKNOWN] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setIncludeUnknown(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_INCLUDE_UNKNOWN] = value } }
    }

    override val excludeInfrastructure: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_EXCLUDE_INFRASTRUCTURE] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setExcludeInfrastructure(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_EXCLUDE_INFRASTRUCTURE] = value } }
    }

    override val onlyOnline: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_ONLY_ONLINE] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setOnlyOnline(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_ONLY_ONLINE] = value } }
    }

    override val onlyDirect: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_ONLY_DIRECT] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setOnlyDirect(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_ONLY_DIRECT] = value } }
    }

    override val showIgnored: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_IGNORED] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setShowIgnored(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_IGNORED] = value } }
    }

    override val excludeMqtt: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_EXCLUDE_MQTT] ?: false }.stateIn(scope, SharingStarted.Lazily, false)

    override fun setExcludeMqtt(value: Boolean) {
        scope.launch { dataStore.edit { it[KEY_EXCLUDE_MQTT] = value } }
    }

    override val hasShownNotPairedWarning: StateFlow<Boolean> =
        dataStore.data
            .map { it[KEY_HAS_SHOWN_NOT_PAIRED_WARNING_PREF] ?: false }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override fun setHasShownNotPairedWarning(shown: Boolean) {
        scope.launch { dataStore.edit { it[KEY_HAS_SHOWN_NOT_PAIRED_WARNING_PREF] = shown } }
    }

    override val showQuickChat: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_QUICK_CHAT_PREF] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setShowQuickChat(show: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_QUICK_CHAT_PREF] = show } }
    }

    override val eventThemeEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_EVENT_THEME_ENABLED] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setEventThemeEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_EVENT_THEME_ENABLED] = enabled } }
    }

    override val bleAutoScan: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_BLE_AUTO_SCAN] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setBleAutoScan(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_BLE_AUTO_SCAN] = enabled } }
    }

    override val networkAutoScan: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_NETWORK_AUTO_SCAN] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setNetworkAutoScan(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_NETWORK_AUTO_SCAN] = enabled } }
    }

    override val selectedConnectionTransport: StateFlow<DeviceType?> =
        dataStore.data
            .map { preferences ->
                preferences[KEY_SELECTED_CONNECTION_TRANSPORT]?.let(::parseDeviceType)
                    ?: legacySelectedConnectionTransport(preferences)
            }
            .stateIn(scope, SharingStarted.Eagerly, null)

    override fun setSelectedConnectionTransport(type: DeviceType) {
        scope.launch { dataStore.edit { it[KEY_SELECTED_CONNECTION_TRANSPORT] = type.name } }
    }

    override fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean> =
        cachedFlow(provideNodeLocationFlows, nodeNum) {
            val key = booleanPreferencesKey(provideLocationKey(nodeNum))
            dataStore.data.map { it[key] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)
        }

    override fun setShouldProvideNodeLocation(nodeNum: Int, provide: Boolean) {
        scope.launch { dataStore.edit { it[booleanPreferencesKey(provideLocationKey(nodeNum))] = provide } }
    }

    private fun provideLocationKey(nodeNum: Int) = "provide-location-$nodeNum"

    // Node list layout preferences

    override val nodeListDensity: StateFlow<String> =
        dataStore.data
            .map { it[NodeListLayoutPreferences.KEY_DENSITY] ?: NodeListLayoutPreferences.DEFAULT_DENSITY }
            .stateIn(scope, SharingStarted.Eagerly, NodeListLayoutPreferences.DEFAULT_DENSITY)

    override fun setNodeListDensity(value: String) {
        scope.launch { dataStore.edit { it[NodeListLayoutPreferences.KEY_DENSITY] = value } }
    }

    override val shouldShowPower: StateFlow<Boolean> =
        dataStore.data
            .map { it[NodeListLayoutPreferences.KEY_SHOW_POWER] ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShouldShowPower(value: Boolean) {
        scope.launch { dataStore.edit { it[NodeListLayoutPreferences.KEY_SHOW_POWER] = value } }
    }

    override val shouldShowLastHeard: StateFlow<Boolean> =
        dataStore.data
            .map { it[NodeListLayoutPreferences.KEY_SHOW_LAST_HEARD] ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShouldShowLastHeard(value: Boolean) {
        scope.launch { dataStore.edit { it[NodeListLayoutPreferences.KEY_SHOW_LAST_HEARD] = value } }
    }

    override val lastHeardIsRelative: StateFlow<Boolean> =
        dataStore.data
            .map { it[NodeListLayoutPreferences.KEY_LAST_HEARD_RELATIVE] ?: false }
            .stateIn(scope, SharingStarted.Eagerly, false)

    override fun setLastHeardIsRelative(value: Boolean) {
        scope.launch { dataStore.edit { it[NodeListLayoutPreferences.KEY_LAST_HEARD_RELATIVE] = value } }
    }

    override val shouldShowLocation: StateFlow<Boolean> =
        dataStore.data
            .map { it[NodeListLayoutPreferences.KEY_SHOW_LOCATION] ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShouldShowLocation(value: Boolean) {
        scope.launch { dataStore.edit { it[NodeListLayoutPreferences.KEY_SHOW_LOCATION] = value } }
    }

    override val shouldShowHops: StateFlow<Boolean> =
        dataStore.data
            .map { it[NodeListLayoutPreferences.KEY_SHOW_HOPS] ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShouldShowHops(value: Boolean) {
        scope.launch { dataStore.edit { it[NodeListLayoutPreferences.KEY_SHOW_HOPS] = value } }
    }

    override val shouldShowSignal: StateFlow<Boolean> =
        dataStore.data
            .map { it[NodeListLayoutPreferences.KEY_SHOW_SIGNAL] ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShouldShowSignal(value: Boolean) {
        scope.launch { dataStore.edit { it[NodeListLayoutPreferences.KEY_SHOW_SIGNAL] = value } }
    }

    override val shouldShowChannel: StateFlow<Boolean> =
        dataStore.data
            .map { it[NodeListLayoutPreferences.KEY_SHOW_CHANNEL] ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShouldShowChannel(value: Boolean) {
        scope.launch { dataStore.edit { it[NodeListLayoutPreferences.KEY_SHOW_CHANNEL] = value } }
    }

    override val shouldShowRole: StateFlow<Boolean> =
        dataStore.data
            .map { it[NodeListLayoutPreferences.KEY_SHOW_ROLE] ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShouldShowRole(value: Boolean) {
        scope.launch { dataStore.edit { it[NodeListLayoutPreferences.KEY_SHOW_ROLE] = value } }
    }

    override val shouldShowTelemetry: StateFlow<Boolean> =
        dataStore.data
            .map { it[NodeListLayoutPreferences.KEY_SHOW_TELEMETRY] ?: true }
            .stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShouldShowTelemetry(value: Boolean) {
        scope.launch { dataStore.edit { it[NodeListLayoutPreferences.KEY_SHOW_TELEMETRY] = value } }
    }

    companion object {
        val KEY_HAS_SHOWN_NOT_PAIRED_WARNING_PREF = booleanPreferencesKey("has_shown_not_paired_warning")
        val KEY_SHOW_QUICK_CHAT_PREF = booleanPreferencesKey("show-quick-chat")
        val KEY_EVENT_THEME_ENABLED = booleanPreferencesKey("event-theme-enabled")

        val KEY_APP_INTRO_COMPLETED = booleanPreferencesKey("app_intro_completed")
        val KEY_THEME = intPreferencesKey("theme")
        val KEY_LOCALE = stringPreferencesKey("locale")
        val KEY_NODE_SORT = intPreferencesKey("node-sort-option")
        val KEY_INCLUDE_UNKNOWN = booleanPreferencesKey("include-unknown")
        val KEY_EXCLUDE_INFRASTRUCTURE = booleanPreferencesKey("exclude-infrastructure")
        val KEY_ONLY_ONLINE = booleanPreferencesKey("only-online")
        val KEY_ONLY_DIRECT = booleanPreferencesKey("only-direct")
        val KEY_SHOW_IGNORED = booleanPreferencesKey("show-ignored")
        val KEY_EXCLUDE_MQTT = booleanPreferencesKey("exclude-mqtt")
        val KEY_BLE_AUTO_SCAN = booleanPreferencesKey("ble-auto-scan")
        val KEY_NETWORK_AUTO_SCAN = booleanPreferencesKey("network-auto-scan")
        val KEY_SELECTED_CONNECTION_TRANSPORT = stringPreferencesKey("selected-connection-transport")
        val KEY_SHOW_BLE_TRANSPORT = booleanPreferencesKey("show-ble-transport")
        val KEY_SHOW_NETWORK_TRANSPORT = booleanPreferencesKey("show-network-transport")
        val KEY_SHOW_USB_TRANSPORT = booleanPreferencesKey("show-usb-transport")

        private fun parseDeviceType(name: String): DeviceType? = DeviceType.entries.firstOrNull { it.name == name }

        private fun legacySelectedConnectionTransport(preferences: Preferences): DeviceType? {
            val hasLegacyTransportPreference =
                KEY_SHOW_BLE_TRANSPORT in preferences ||
                    KEY_SHOW_NETWORK_TRANSPORT in preferences ||
                    KEY_SHOW_USB_TRANSPORT in preferences
            if (!hasLegacyTransportPreference) return null

            val selected =
                listOfNotNull(
                    DeviceType.BLE.takeIf { preferences[KEY_SHOW_BLE_TRANSPORT] ?: true },
                    DeviceType.TCP.takeIf { preferences[KEY_SHOW_NETWORK_TRANSPORT] ?: true },
                    DeviceType.USB.takeIf { preferences[KEY_SHOW_USB_TRANSPORT] ?: true },
                )
            return selected.firstOrNull()
        }
    }
}
