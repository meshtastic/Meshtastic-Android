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
package com.geeksville.mesh.repository.network

import co.touchlab.kermit.Logger
import com.geeksville.mesh.util.ignoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.toByteString
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttAsyncClient.generateClientId
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.model.util.subscribeList
import org.meshtastic.proto.MqttClientProxyMessage
import java.net.URI
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

@Singleton
class MQTTRepository
@Inject
constructor(
    private val radioConfigRepository: RadioConfigRepository,
    private val nodeRepository: NodeRepository,
) {

    companion object {
        /**
         * Quality of Service (QoS) levels in MQTT:
         * - QoS 0: "at most once". Packets are sent once without validation if it has been received.
         * - QoS 1: "at least once". Packets are sent and stored until the client receives confirmation from the server.
         *   MQTT ensures delivery, but duplicates may occur.
         * - QoS 2: "exactly once". Similar to QoS 1, but with no duplicates.
         */
        private const val DEFAULT_QOS = 1
        private const val DEFAULT_TOPIC_ROOT = "msh"
        private const val DEFAULT_TOPIC_LEVEL = "/2/e/"
        private const val JSON_TOPIC_LEVEL = "/2/json/"
        private const val DEFAULT_SERVER_ADDRESS = "mqtt.meshtastic.org"
    }

    private var mqttClient: MqttAsyncClient? = null

    fun disconnect() {
        Logger.i { "MQTT Disconnected" }
        mqttClient?.apply {
            ignoreException { disconnect() }
            close(true)
            mqttClient = null
        }
    }

    val proxyMessageFlow: Flow<MqttClientProxyMessage> = callbackFlow {
        val ownerId = "MeshtasticAndroidMqttProxy-${nodeRepository.myId.value ?: generateClientId()}"
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val mqttConfig = radioConfigRepository.moduleConfigFlow.first().mqtt

        val sslContext = SSLContext.getInstance("TLS")
        // Create a custom SSLContext that trusts all certificates
        sslContext.init(null, arrayOf<TrustManager>(TrustAllX509TrustManager()), SecureRandom())

        val rootTopic = mqttConfig?.root?.ifEmpty { DEFAULT_TOPIC_ROOT }

        val connectOptions =
            MqttConnectOptions().apply {
                userName = mqttConfig?.username
                password = mqttConfig?.password?.toCharArray()
                isAutomaticReconnect = true
                if (mqttConfig?.tls_enabled == true) {
                    socketFactory = sslContext.socketFactory
                }
            }

        val bufferOptions =
            DisconnectedBufferOptions().apply {
                isBufferEnabled = true
                bufferSize = 512
                isPersistBuffer = false
                isDeleteOldestMessages = true
            }

        val callback =
            object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    Logger.i { "MQTT connectComplete: $serverURI reconnect: $reconnect" }
                    channelSet.subscribeList
                        .ifEmpty {
                            return
                        }
                        .forEach { globalId ->
                            subscribe("$rootTopic$DEFAULT_TOPIC_LEVEL$globalId/+")
                            if (mqttConfig?.json_enabled == true) subscribe("$rootTopic$JSON_TOPIC_LEVEL$globalId/+")
                        }
                    subscribe("$rootTopic${DEFAULT_TOPIC_LEVEL}PKI/+")
                }

                override fun connectionLost(cause: Throwable) {
                    Logger.i { "MQTT connectionLost cause: $cause" }
                    if (cause is IllegalArgumentException) close(cause)
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    trySend(
                        MqttClientProxyMessage(
                            topic = topic,
                            data_ = message.payload.toByteString(),
                            retained = message.isRetained,
                        ),
                    )
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Logger.i { "MQTT deliveryComplete messageId: ${token?.messageId}" }
                }
            }

        val scheme = if (mqttConfig?.tls_enabled == true) "ssl" else "tcp"
        val (host, port) =
            (mqttConfig?.address ?: DEFAULT_SERVER_ADDRESS).split(":", limit = 2).let {
                it[0] to (it.getOrNull(1)?.toIntOrNull() ?: -1)
            }

        mqttClient =
            MqttAsyncClient(URI(scheme, null, host, port, "", "", "").toString(), ownerId, MemoryPersistence()).apply {
                setCallback(callback)
                setBufferOpts(bufferOptions)
                connect(connectOptions)
            }

        awaitClose { disconnect() }
    }

    private fun subscribe(topic: String) {
        mqttClient?.subscribe(topic, DEFAULT_QOS)
        Logger.i { "MQTT Subscribed to topic: $topic" }
    }

    fun publish(topic: String, data: ByteArray, retained: Boolean) {
        try {
            val token = mqttClient?.publish(topic, data, DEFAULT_QOS, retained)
            Logger.i { "MQTT Publish messageId: ${token?.messageId}" }
        } catch (ex: Exception) {
            Logger.e { "MQTT Publish error: ${ex.message}" }
        }
    }
}
