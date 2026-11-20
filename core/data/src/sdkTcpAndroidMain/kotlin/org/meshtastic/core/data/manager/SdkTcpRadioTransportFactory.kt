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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.TransportFailure
import org.meshtastic.core.model.TransportFailureKind
import org.meshtastic.core.model.TransportPhase
import org.meshtastic.core.model.TransportStatus
import org.meshtastic.core.network.radio.AndroidSdkTcpTransportFactory
import org.meshtastic.core.network.transport.parseTcpRadioAddress
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.ReceivedRadioFrame
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.ToRadio
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioSessionEvent
import org.meshtastic.sdk.RadioSessionOverflowException
import org.meshtastic.sdk.transport.tcp.TcpTransport

@Single(binds = [AndroidSdkTcpTransportFactory::class])
internal class SdkTcpRadioTransportFactory(
    private val configManager: Lazy<MeshConfigFlowManagerImpl>,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val messageProcessor: Lazy<MeshMessageProcessor>,
    private val nodeManager: Lazy<NodeManager>,
) : AndroidSdkTcpTransportFactory {
    override fun create(address: String, service: RadioInterfaceService): RadioTransport {
        val (host, port) = parseTcpRadioAddress(address)
        val client =
            RadioClient.Builder()
                .transport(TcpTransport(host, port))
                .storage(InMemoryStorageProvider())
                .coroutineContext(service.serviceScope.coroutineContext)
                .autoSyncTimeOnConnect(false)
                .sessionReceive()
                .sessionReceiveEnvelopes()
                .build()
        return SdkTcpRadioTransport(client, service, configManager, connectionManager, messageProcessor, nodeManager)
    }
}

/** One SDK client per Android transport generation. The app still owns writes, settings and durable send state. */
@Suppress("LongParameterList")
private class SdkTcpRadioTransport(
    private val client: RadioClient,
    private val service: RadioInterfaceService,
    private val configManager: Lazy<MeshConfigFlowManagerImpl>,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val messageProcessor: Lazy<MeshMessageProcessor>,
    private val nodeManager: Lazy<NodeManager>,
) : RadioTransport {
    override val managesPhoneApi: Boolean = true
    private val session = checkNotNull(service.activeSession.value)
    private var run: Job? = null

    override fun start() {
        run =
            service.serviceScope.launch {
                try {
                    service.onStatus(TransportStatus(TransportPhase.Connecting))
                    runSession()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (overflow: RadioSessionOverflowException) {
                    Logger.w(overflow) { "SDK TCP lost receive coverage in app session ${session.generation}" }
                    service.onDisconnect(isPermanent = true, errorMessage = "SDK TCP receive stream overflowed")
                } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                    Logger.w { "SDK TCP session failed: ${failure::class.simpleName}" }
                    val status = TransportFailure(TransportFailureKind.ConnectFailed)
                    service.onStatus(TransportStatus(TransportPhase.Connecting, status))
                } finally {
                    withContext(NonCancellable) {
                        client.disconnect()
                        service.onDisconnect(isPermanent = true)
                    }
                }
            }
    }

    private suspend fun runSession() = coroutineScope {
        val connected = async { client.connect() }
        var sdkSession: Long? = null
        checkNotNull(client.sessionEvents)
            .takeWhile { it !is RadioSessionEvent.Ended }
            .collect { event ->
                when (event) {
                    is RadioSessionEvent.Ready -> {
                        service.onConnect()
                        service.onStatus(TransportStatus(TransportPhase.HandshakeNodes))
                        check(install(event)) { "SDK TCP snapshot session is no longer active" }
                        sdkSession = event.sessionId
                        service.onStatus(TransportStatus(TransportPhase.Connected))
                        Logger.i { "SDK TCP snapshot installed for session ${session.generation}" }
                        reportAppReady()
                    }

                    is RadioSessionEvent.Packet -> {
                        if (event.sessionId == sdkSession) {
                            val envelope = FromRadio.Builder().also { it.packet = event.packet }.build()
                            handleEnvelope(envelope)
                        }
                    }

                    is RadioSessionEvent.Envelope -> {
                        if (event.sessionId == sdkSession) handleEnvelope(event.envelope)
                    }

                    is RadioSessionEvent.Ended -> Unit
                }
            }
        connected.await()
    }

    private suspend fun install(event: RadioSessionEvent.Ready): Boolean {
        val config = event.configBundle
        return configManager.value.installRadioSnapshot(
            session,
            RadioHandshakeSnapshot(
                myInfo = checkNotNull(event.myInfo),
                metadata = config?.metadata,
                channels = event.channels.orEmpty(),
                configs = config?.configs.orEmpty(),
                moduleConfigs = config?.moduleConfigs.orEmpty(),
                deviceUiConfig = config?.deviceUIConfig,
                nodes = event.nodes.values.toList(),
            ),
        )
    }

    private suspend fun reportAppReady() {
        service.runWithSessionLease(session) {
            safeCatching {
                connectionManager.value.onRadioConfigLoaded()
                connectionManager.value.onNodeDbReady()
            }
                .onFailure { failure ->
                    Logger.w { "SDK TCP app readiness callback failed: ${failure::class.simpleName}" }
                }
        }
    }

    private suspend fun handleEnvelope(envelope: FromRadio) {
        val nodes = nodeManager.value
        val node = envelope.node_info
        if (node == null) {
            messageProcessor.value.handleFromRadio(
                ReceivedRadioFrame(envelope.encode().toByteString(), session),
                nodes.myNodeNum.value,
            )
        } else {
            service.runWithSessionLease(session) { nodes.installNodeInfoAndPersist(node) }
        }
    }

    override fun handleSendToRadio(p: ByteArray): Boolean = safeCatching {
        val frame = ToRadio.ADAPTER.decode(p)
        frame.want_config_id == null && frame.heartbeat == null && frame.disconnect != true && client.trySendRaw(frame)
    }
        .getOrDefault(false)

    override suspend fun close() = withContext(NonCancellable) {
        run?.cancelAndJoin()
        run = null
    }
}
