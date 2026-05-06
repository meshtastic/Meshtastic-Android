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
package org.meshtastic.feature.settings.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.MqttConnectionState
import org.meshtastic.core.model.MqttProbeStatus
import org.meshtastic.core.repository.MqttManager

/**
 * Encapsulates MQTT broker reachability probing logic.
 * Injected into [RadioConfigViewModel] to keep probe state and cancellation self-contained.
 */
class MqttProbeCoordinator(
    private val mqttManager: MqttManager,
    private val scope: CoroutineScope,
) {
    /** MQTT proxy connection state for the settings UI. */
    val mqttConnectionState: StateFlow<MqttConnectionState> = mqttManager.mqttConnectionState

    private val _probeStatus = MutableStateFlow<MqttProbeStatus?>(null)

    /** Latest result from a [probe] call, or `null` if no probe has been run. */
    val probeStatus: StateFlow<MqttProbeStatus?> = _probeStatus.asStateFlow()

    private var probeJob: Job? = null

    /**
     * Run a one-shot reachability/credentials probe against an MQTT broker.
     * Cancels any in-flight probe before starting a new one.
     */
    fun probe(address: String, tlsEnabled: Boolean, username: String?, password: String?) {
        probeJob?.cancel()
        _probeStatus.value = MqttProbeStatus.Probing
        probeJob = scope.launch {
            val result =
                safeCatching { mqttManager.probe(address, tlsEnabled, username, password) }
                    .getOrElse { e ->
                        Logger.w(e) { "MQTT probe threw" }
                        MqttProbeStatus.Other(message = e.message)
                    }
            _probeStatus.value = result
        }
    }

    /** Clear the latest probe result (e.g. when the user edits the address). */
    fun clearProbeStatus() {
        probeJob?.cancel()
        _probeStatus.value = null
    }
}
