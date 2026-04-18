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
import org.meshtastic.core.model.MqttProbeStatus
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.core.network.repository.resolveEndpoint
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.mqtt.ConnectionState
import org.meshtastic.mqtt.MqttClient
import org.meshtastic.mqtt.MqttException
import org.meshtastic.mqtt.ProbeResult
import org.meshtastic.mqtt.probe
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
            if (!active) MqttConnectionState.Inactive else libState.toAppState()
        }
            .stateIn(scope, SharingStarted.Eagerly, MqttConnectionState.Inactive)

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
        is ConnectionState.Connecting -> MqttConnectionState.Connecting
        is ConnectionState.Connected -> MqttConnectionState.Connected
        is ConnectionState.Reconnecting ->
            MqttConnectionState.Reconnecting(attempt = attempt, lastError = lastError?.message)
        is ConnectionState.Disconnected ->
            reason?.let { MqttConnectionState.Disconnected(reason = it.message) }
                ?: MqttConnectionState.Disconnected.Idle
    }

    override suspend fun probe(
        address: String,
        tlsEnabled: Boolean,
        username: String?,
        password: String?,
    ): MqttProbeStatus {
        val endpoint = resolveEndpoint(address, tlsEnabled)
        val result =
            MqttClient.probe(endpoint = endpoint) {
                val user = username?.takeUnless { it.isEmpty() }
                val pass = password?.takeUnless { it.isEmpty() }
                if (user != null) this.username = user
                if (pass != null) password(pass)
            }
        return result.toAppStatus()
    }

    private fun ProbeResult.toAppStatus(): MqttProbeStatus = when (this) {
        is ProbeResult.Success -> {
            val info = serverInfo
            val summary =
                buildList {
                    info.assignedClientIdentifier?.let { add("client=$it") }
                    info.maximumQosOrdinal?.let { add("maxQoS=$it") }
                    info.serverKeepAliveSeconds?.let { add("keepalive=${it}s") }
                }
                    .joinToString(", ")
                    .ifEmpty { null }
            MqttProbeStatus.Success(serverInfo = summary)
        }
        is ProbeResult.Rejected ->
            MqttProbeStatus.Rejected(
                reasonCode = reasonCode.value,
                reason = message,
                serverReference = serverReference,
            )
        is ProbeResult.DnsFailure -> MqttProbeStatus.DnsFailure(message = cause.message)
        is ProbeResult.TcpFailure -> MqttProbeStatus.TcpFailure(message = cause.message)
        is ProbeResult.TlsFailure -> MqttProbeStatus.TlsFailure(message = cause.message)
        is ProbeResult.Timeout -> MqttProbeStatus.Timeout(timeoutMs = durationMs)
        is ProbeResult.Other -> MqttProbeStatus.Other(message = cause.message)
    }
}
