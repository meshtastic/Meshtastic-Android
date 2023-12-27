package com.geeksville.mesh.repository.network

import com.geeksville.mesh.MeshProtos.MqttClientProxyMessage
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.subscribeList
import com.geeksville.mesh.mqttClientProxyMessage
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.util.ignoreException
import com.google.protobuf.ByteString
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttAsyncClient.generateClientId
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.URI
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

@Singleton
class MQTTRepository @Inject constructor(
    private val radioConfigRepository: RadioConfigRepository,
) : Logging {

    companion object {
        /**
         * Quality of Service (QoS) levels in MQTT:
         * - QoS 0: "at most once". Packets are sent once without validation if it has been received.
         * - QoS 1: "at least once". Packets are sent and stored until the client receives confirmation from the server. MQTT ensures delivery, but duplicates may occur.
         * - QoS 2: "exactly once". Similar to QoS 1, but with no duplicates.
         */
        private const val DEFAULT_QOS = 1
        private const val DEFAULT_TOPIC_ROOT = "msh"
        private const val STAT_TOPIC_LEVEL = "/2/stat/"
        private const val DEFAULT_TOPIC_LEVEL = "/2/c/"
        private const val JSON_TOPIC_LEVEL = "/2/json/"
        private const val DEFAULT_SERVER_ADDRESS = "mqtt.meshtastic.org"
    }

    private var mqttClient: MqttAsyncClient? = null

    suspend fun connect(callback: MqttCallbackExtended) {
        val ownerId = radioConfigRepository.nodeDB.myId.value ?: generateClientId()
        val channelSet = radioConfigRepository.channelSetFlow.first()
        val mqttConfig = radioConfigRepository.moduleConfigFlow.first().mqtt

        val sslContext = SSLContext.getInstance("TLS")
        // Create a custom SSLContext that trusts all certificates
        sslContext.init(null, arrayOf<TrustManager>(TrustAllX509TrustManager()), SecureRandom())

        val stat = mqttConfig.root.ifEmpty { DEFAULT_TOPIC_ROOT } + STAT_TOPIC_LEVEL + ownerId
        val connectOptions = MqttConnectOptions().apply {
            userName = mqttConfig.username
            password = mqttConfig.password.toCharArray()
            isCleanSession = false // must be false to auto subscribe on reconnects
            isAutomaticReconnect = true
            if (mqttConfig.tlsEnabled) {
                socketFactory = sslContext.socketFactory
            }
            setWill(stat, "offline".encodeToByteArray(), DEFAULT_QOS, true)
        }

        val bufferOptions = DisconnectedBufferOptions().apply {
            isBufferEnabled = true
            bufferSize = 512
            isPersistBuffer = false
            isDeleteOldestMessages = true
        }

        val scheme = if (mqttConfig.tlsEnabled) "ssl" else "tcp"
        val (host, port) = mqttConfig.address.ifEmpty { DEFAULT_SERVER_ADDRESS }
            .split(":", limit = 2).let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: -1) }

        val serverURI: String = URI(scheme, null, host, port, "", "", "").toString()

        val topic = mqttConfig.root.ifEmpty { DEFAULT_TOPIC_ROOT } +
                DEFAULT_TOPIC_LEVEL // FIXME if (mqttConfig.jsonEnabled) JSON_TOPIC_LEVEL else DEFAULT_TOPIC_LEVEL

        mqttClient = MqttAsyncClient(
            serverURI,
            ownerId,
            MemoryPersistence(),
        )
        mqttClient?.apply {
            setCallback(callback)
            setBufferOpts(bufferOptions)
            connect(connectOptions).waitForCompletion()

            channelSet.subscribeList.forEach { globalId ->
                val topicFilter = "$topic$globalId/#"
                subscribe(topicFilter, DEFAULT_QOS).waitForCompletion()
                info("MQTT Subscribed to topic: $topicFilter")
            }
        }
    }

    fun disconnect() {
        info("MQTT Disconnected")
        mqttClient?.apply {
            ignoreException { disconnect() }
            close(true)
            mqttClient = null
        }
    }

    val proxyMessageFlow: Flow<MqttClientProxyMessage> = callbackFlow {
        val callback = object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String) {
                info("MQTT connectComplete: $serverURI reconnect: $reconnect ")
            }

            override fun connectionLost(cause: Throwable) {
                info("MQTT connectionLost cause: $cause")
                if (cause is IllegalArgumentException) close(cause)
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                trySend(mqttClientProxyMessage {
                    this.topic = topic
                    data = ByteString.copyFrom(message.payload)
                    retained = message.isRetained
                })
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                info("MQTT deliveryComplete messageId: ${token?.messageId}")
            }
        }
        connect(callback)

        awaitClose { disconnect() }
    }

    fun publish(topic: String, data: ByteArray, retained: Boolean) {
        try {
            val token = mqttClient?.publish(topic, data, DEFAULT_QOS, retained)
            info("MQTT Publish messageId: ${token?.messageId}")
        } catch (ex: Exception) {
            errormsg("MQTT Publish error: ${ex.message}")
        }
    }
}
