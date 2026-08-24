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
package org.meshtastic.app.ai.appfunctions

import android.content.Context
import androidx.appfunctions.AppFunctionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.AppFunctionsPrefs

/**
 * Observes [AppFunctionsPrefs] and synchronizes the enabled/disabled state of each AppFunction with the system via
 * [AppFunctionManager].
 *
 * When the master toggle is off, all functions are disabled regardless of individual toggles.
 */
class AppFunctionStateSync(
    private val context: Context,
    private val prefs: AppFunctionsPrefs,
    dispatchers: CoroutineDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    init {
        observeAndSync()
    }

    private fun observeAndSync() {
        data class FunctionToggle(val id: String, val enabled: StateFlow<Boolean>)

        val functions =
            listOf(
                FunctionToggle(SEND_MESSAGE_ID, prefs.sendMessageEnabled),
                FunctionToggle(GET_MESH_STATUS_ID, prefs.getMeshStatusEnabled),
                FunctionToggle(GET_NODE_LIST_ID, prefs.getNodeListEnabled),
                FunctionToggle(GET_CHANNEL_INFO_ID, prefs.getChannelInfoEnabled),
                FunctionToggle(GET_DEVICE_STATUS_ID, prefs.getDeviceStatusEnabled),
                FunctionToggle(GET_NODE_DETAILS_ID, prefs.getNodeDetailsEnabled),
                FunctionToggle(GET_MESH_METRICS_ID, prefs.getMeshMetricsEnabled),
                FunctionToggle(GET_RECENT_MESSAGES_ID, prefs.getRecentMessagesEnabled),
                FunctionToggle(GET_UNREAD_SUMMARY_ID, prefs.getUnreadSummaryEnabled),
            )

        // Combine master toggle with each individual toggle
        combine(prefs.masterEnabled, combine(functions.map { it.enabled }) { it.toList() }) { master, toggles ->
            functions.mapIndexed { index, fn -> fn.id to (master && toggles[index]) }
        }
            .onEach { states -> syncStates(states) }
            .launchIn(scope)
    }

    private suspend fun syncStates(states: List<Pair<String, Boolean>>) {
        val manager = AppFunctionManager.getInstance(context) ?: return

        for ((functionId, enabled) in states) {
            val state =
                if (enabled) {
                    AppFunctionManager.APP_FUNCTION_STATE_ENABLED
                } else {
                    AppFunctionManager.APP_FUNCTION_STATE_DISABLED
                }
            try {
                manager.setAppFunctionEnabled(functionId, state)
            } catch (_: Exception) {
                // Function may not be indexed yet (first launch)
            }
        }
    }

    companion object {
        private const val CLASS_PREFIX = "org.meshtastic.app.ai.appfunctions.MeshtasticAppFunctions#"

        const val SEND_MESSAGE_ID = "${CLASS_PREFIX}sendMessage"
        const val GET_MESH_STATUS_ID = "${CLASS_PREFIX}getMeshStatus"
        const val GET_NODE_LIST_ID = "${CLASS_PREFIX}getNodeList"
        const val GET_CHANNEL_INFO_ID = "${CLASS_PREFIX}getChannelInfo"
        const val GET_DEVICE_STATUS_ID = "${CLASS_PREFIX}getDeviceStatus"
        const val GET_NODE_DETAILS_ID = "${CLASS_PREFIX}getNodeDetails"
        const val GET_MESH_METRICS_ID = "${CLASS_PREFIX}getMeshMetrics"
        const val GET_RECENT_MESSAGES_ID = "${CLASS_PREFIX}getRecentMessages"
        const val GET_UNREAD_SUMMARY_ID = "${CLASS_PREFIX}getUnreadSummary"
    }
}
