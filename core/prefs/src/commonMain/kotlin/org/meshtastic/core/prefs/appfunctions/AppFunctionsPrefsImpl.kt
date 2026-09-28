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
package org.meshtastic.core.prefs.appfunctions

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
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.AppDataStore
import org.meshtastic.core.repository.AppFunctionsPrefs
import org.meshtastic.core.repository.AppFunctionsSetting

@Single
@Suppress("TooManyFunctions")
class AppFunctionsPrefsImpl(private val dataStore: AppDataStore, dispatchers: CoroutineDispatchers) :
    AppFunctionsPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val masterEnabled: StateFlow<Boolean> = booleanPref(KEY_MASTER)
    override val sendMessageEnabled: StateFlow<Boolean> = booleanPref(KEY_SEND_MESSAGE)
    override val getMeshStatusEnabled: StateFlow<Boolean> = booleanPref(KEY_GET_MESH_STATUS)
    override val getNodeListEnabled: StateFlow<Boolean> = booleanPref(KEY_GET_NODE_LIST)
    override val getChannelInfoEnabled: StateFlow<Boolean> = booleanPref(KEY_GET_CHANNEL_INFO)
    override val getDeviceStatusEnabled: StateFlow<Boolean> = booleanPref(KEY_GET_DEVICE_STATUS)
    override val getNodeDetailsEnabled: StateFlow<Boolean> = booleanPref(KEY_GET_NODE_DETAILS)
    override val getMeshMetricsEnabled: StateFlow<Boolean> = booleanPref(KEY_GET_MESH_METRICS)
    override val getRecentMessagesEnabled: StateFlow<Boolean> = booleanPref(KEY_GET_RECENT_MESSAGES)
    override val getUnreadSummaryEnabled: StateFlow<Boolean> = booleanPref(KEY_GET_UNREAD_SUMMARY)

    override fun setMasterEnabled(enabled: Boolean) = set(KEY_MASTER, enabled)

    override fun setSendMessageEnabled(enabled: Boolean) = set(KEY_SEND_MESSAGE, enabled)

    override fun setGetMeshStatusEnabled(enabled: Boolean) = set(KEY_GET_MESH_STATUS, enabled)

    override fun setGetNodeListEnabled(enabled: Boolean) = set(KEY_GET_NODE_LIST, enabled)

    override fun setGetChannelInfoEnabled(enabled: Boolean) = set(KEY_GET_CHANNEL_INFO, enabled)

    override fun setGetDeviceStatusEnabled(enabled: Boolean) = set(KEY_GET_DEVICE_STATUS, enabled)

    override fun setGetNodeDetailsEnabled(enabled: Boolean) = set(KEY_GET_NODE_DETAILS, enabled)

    override fun setGetMeshMetricsEnabled(enabled: Boolean) = set(KEY_GET_MESH_METRICS, enabled)

    override fun setGetRecentMessagesEnabled(enabled: Boolean) = set(KEY_GET_RECENT_MESSAGES, enabled)

    override fun setGetUnreadSummaryEnabled(enabled: Boolean) = set(KEY_GET_UNREAD_SUMMARY, enabled)

    override fun toggle(setting: AppFunctionsSetting) {
        val key = setting.key
        scope.launch { dataStore.edit { prefs -> prefs[key] = !(prefs[key] ?: ENABLED_BY_DEFAULT) } }
    }

    private fun booleanPref(key: Preferences.Key<Boolean>): StateFlow<Boolean> =
        dataStore.data.map { it[key] ?: ENABLED_BY_DEFAULT }.stateIn(scope, SharingStarted.Eagerly, ENABLED_BY_DEFAULT)

    private fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        scope.launch { dataStore.edit { prefs -> prefs[key] = value } }
    }

    companion object {
        private const val ENABLED_BY_DEFAULT = true

        private val KEY_MASTER = booleanPreferencesKey("appfn_master_enabled")
        private val KEY_SEND_MESSAGE = booleanPreferencesKey("appfn_send_message")
        private val KEY_GET_MESH_STATUS = booleanPreferencesKey("appfn_get_mesh_status")
        private val KEY_GET_NODE_LIST = booleanPreferencesKey("appfn_get_node_list")
        private val KEY_GET_CHANNEL_INFO = booleanPreferencesKey("appfn_get_channel_info")
        private val KEY_GET_DEVICE_STATUS = booleanPreferencesKey("appfn_get_device_status")
        private val KEY_GET_NODE_DETAILS = booleanPreferencesKey("appfn_get_node_details")
        private val KEY_GET_MESH_METRICS = booleanPreferencesKey("appfn_get_mesh_metrics")
        private val KEY_GET_RECENT_MESSAGES = booleanPreferencesKey("appfn_get_recent_messages")
        private val KEY_GET_UNREAD_SUMMARY = booleanPreferencesKey("appfn_get_unread_summary")

        private val AppFunctionsSetting.key: Preferences.Key<Boolean>
            get() =
                when (this) {
                    AppFunctionsSetting.MASTER -> KEY_MASTER
                    AppFunctionsSetting.SEND_MESSAGE -> KEY_SEND_MESSAGE
                    AppFunctionsSetting.GET_MESH_STATUS -> KEY_GET_MESH_STATUS
                    AppFunctionsSetting.GET_NODE_LIST -> KEY_GET_NODE_LIST
                    AppFunctionsSetting.GET_CHANNEL_INFO -> KEY_GET_CHANNEL_INFO
                    AppFunctionsSetting.GET_DEVICE_STATUS -> KEY_GET_DEVICE_STATUS
                    AppFunctionsSetting.GET_NODE_DETAILS -> KEY_GET_NODE_DETAILS
                    AppFunctionsSetting.GET_MESH_METRICS -> KEY_GET_MESH_METRICS
                    AppFunctionsSetting.GET_RECENT_MESSAGES -> KEY_GET_RECENT_MESSAGES
                    AppFunctionsSetting.GET_UNREAD_SUMMARY -> KEY_GET_UNREAD_SUMMARY
                }
    }
}
