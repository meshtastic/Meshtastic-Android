/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.model.MqttConnectionState
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttException
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.proto.ToRadio

@Single
class MqttManagerImpl(
    private val mqttRepository: MQTTRepository,
    private val packetHandler: PacketHandler,
    private val serviceRepository: ServiceRepository,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : MqttManager {
    private var mqttMessageFlow: Job? = null
    private val proxyActive = MutableStateFlow(false)

    override val mqttConnectionState: StateFlow<MqttConnectionState> =
        combine(proxyActive, mqttRepository.connectionState) { active, libState ->
            if (!active) MqttConnectionState.INACTIVE else libState.toAppState()
        }
            .stateIn(scope, SharingStarted.Eagerly, MqttConnectionState.INACTIVE)

    override fun startProxy(enabled: Boolean, proxyToClientEnabled: Boolean) {
        if (mqttMessageFlow?.isActive == true) return
        if (enabled && proxyToClientEnabled) {
            proxyActive.value = true
            mqttMessageFlow =
                mqttRepository.proxyMessageFlow
                    .onEach { message -> packetHandler.sendToRadio(ToRadio(mqttClientProxyMessage = message)) }
                    .catch { throwable ->
                        proxyActive.value = false
                        val message =
                            when (throwable) {
                                is MqttException.ConnectionRejected -> "MQTT: connection rejected (check credentials)"
                                is MqttException.ConnectionLost -> "MQTT: connection lost"
                                else -> "MQTT proxy failed: ${throwable.message}"
                            }
                        serviceRepository.setErrorMessage(text = message, severity = Severity.Warn)
                    }
                    .launchIn(scope)
        }
    }

    override fun stop() {
        if (mqttMessageFlow?.isActive == true) {
            Logger.i { "Stopping MqttClientProxy" }
            mqttMessageFlow?.cancel()
            mqttMessageFlow = null
        }
        proxyActive.value = false
    }

    override fun handleMqttProxyMessage(message: MqttClientProxyMessage) {
        val topic = message.topic
        Logger.d { "[mqttClientProxyMessage] $topic" }
        val retained = message.retained == true
        when {
            message.text != null -> {
                mqttRepository.publish(topic, message.text!!.encodeToByteArray(), retained)
            }
            message.data_ != null -> {
                mqttRepository.publish(topic, message.data_!!.toByteArray(), retained)
            }
            else -> {}
        }
    }

    private fun ConnectionState.toAppState(): MqttConnectionState = when (this) {
        ConnectionState.DISCONNECTED -> MqttConnectionState.DISCONNECTED
        ConnectionState.CONNECTING -> MqttConnectionState.CONNECTING
        ConnectionState.CONNECTED -> MqttConnectionState.CONNECTED
        ConnectionState.RECONNECTING -> MqttConnectionState.RECONNECTING
    }
}
