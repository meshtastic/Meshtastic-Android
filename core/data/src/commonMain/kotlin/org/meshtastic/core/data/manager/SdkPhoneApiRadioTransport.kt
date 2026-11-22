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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.TransportFailure
import org.meshtastic.core.model.TransportFailureKind
import org.meshtastic.core.model.TransportPhase
import org.meshtastic.core.model.TransportStatus
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.proto.LockdownAuth
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.proto.ToRadio
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminSessionStatus
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.LogLevel
import org.meshtastic.sdk.LogSink
import org.meshtastic.sdk.MeshEvent
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.PacedMessageHandle
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioSessionEvent
import org.meshtastic.sdk.RadioSessionOverflowException
import org.meshtastic.sdk.TransportState
import org.meshtastic.sdk.transport.ble.BleTransport
import org.meshtastic.sdk.RadioTransport as SdkRadioTransport

/** One SDK client per app transport generation. Room and durable send state belong to the app. */
@Suppress("TooManyFunctions")
internal class SdkPhoneApiRadioTransport(
    private val openTransport: suspend () -> SdkRadioTransport,
    private val service: RadioInterfaceService,
    private val projection: Lazy<RadioSessionProjection>,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val lockdownCoordinator: Lazy<LockdownCoordinator>,
) : RadioTransport {

    private val currentClient = MutableStateFlow<RadioClient?>(null)

    override val client: RadioClient?
        get() = currentClient.value

    override val admin: AdminApi?
        get() = currentClient.value?.admin

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun adminSessionStatus(node: NodeId): Flow<AdminSessionStatus> = currentClient.flatMapLatest {
        it?.adminSessionStatus(node) ?: flowOf(AdminSessionStatus.NoSession)
    }

    override fun sendPaced(packet: MeshPacket): PacedMessageHandle? = currentClient.value?.sendPaced(packet)

    override fun tryLockdown(auth: LockdownAuth): Boolean = currentClient.value?.tryLockdown(auth) == true

    override fun requestNodeDbRefresh(): Boolean = currentClient.value?.requestNodeDbRefresh() == true

    private val session = checkNotNull(service.activeSession.value)
    private var run: Job? = null

    override fun start() {
        lockdownCoordinator.value.onConnect()
        run = service.serviceScope.launch { runPhoneApiSession() }
    }

    private suspend fun runPhoneApiSession() {
        var recoverable = true
        var errorMessage: String? = null
        var ble: BleTransport? = null
        try {
            service.onStatus(TransportStatus(TransportPhase.Connecting))
            val transport = openTransport()
            ble = transport as? BleTransport
            val client =
                RadioClient.Builder()
                    .transport(transport)
                    .storage(InMemoryStorageProvider())
                    .logger(sdkLogSink)
                    .coroutineContext(service.serviceScope.coroutineContext)
                    .autoSyncTimeOnConnect(false)
                    .sessionReceive()
                    .build()
            currentClient.value = client
            runSession(client, transport.state)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (overflow: RadioSessionOverflowException) {
            Logger.w(overflow) { "SDK PhoneAPI lost receive coverage in app session ${session.generation}" }
            recoverable = false
            errorMessage = "SDK PhoneAPI receive stream overflowed"
        } catch (failure: MeshtasticException.Transport) {
            reportSessionFailure(failure)
        } catch (failure: MeshtasticException.HandshakeTimeout) {
            reportSessionFailure(failure)
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            recoverable = false
            reportSessionFailure(failure)
        } finally {
            val client = currentClient.value
            currentClient.value = null
            withContext(NonCancellable) {
                try {
                    client?.disconnect()
                } finally {
                    ble?.shutdown()
                }
            }
        }
        service.onPhoneApiSessionEnded(recoverable, errorMessage)
    }

    private fun reportSessionFailure(failure: Exception) {
        Logger.w { "SDK PhoneAPI session failed: ${failure::class.simpleName}" }
        val status = TransportFailure(TransportFailureKind.ConnectFailed)
        service.onStatus(TransportStatus(TransportPhase.Connecting, status))
    }

    private suspend fun runSession(client: RadioClient, transportState: StateFlow<TransportState>) = coroutineScope {
        val linkStatus = launch { reportLinkSetup(transportState) }
        val connected = async { client.connect() }
        val sideFlows = launch { collectSideFlows(client) }
        var sdkSession: Long? = null
        checkNotNull(client.sessionEvents)
            .takeWhile { it !is RadioSessionEvent.Ended }
            .collect { event ->
                when (event) {
                    is RadioSessionEvent.Lockdown -> {
                        service.runWithSessionLease(session) {
                            lockdownCoordinator.value.handleLockdownStatus(event.status)
                        }
                    }

                    is RadioSessionEvent.Ready -> {
                        linkStatus.cancelAndJoin()
                        service.onConnect()
                        service.onStatus(TransportStatus(TransportPhase.HandshakeNodes))
                        check(projection.value.installReady(event, session)) {
                            "SDK PhoneAPI snapshot session is no longer active"
                        }
                        sdkSession = event.sessionId
                        service.onStatus(TransportStatus(TransportPhase.Connected))
                        Logger.i { "SDK PhoneAPI snapshot installed for session ${session.generation}" }
                        reportAppReady()
                    }

                    else -> if (event.sessionId == sdkSession) projection.value.project(event, session)
                }
            }
        sideFlows.cancelAndJoin()
        linkStatus.cancelAndJoin()
        connected.await()
    }

    /** The SDK's lossy side streams, which carry no session ID and so follow the client's lifetime. */
    private suspend fun collectSideFlows(client: RadioClient) = coroutineScope {
        val projection = projection.value
        launch { client.deviceLogs.collect { projection.onDeviceLog(it, session) } }
        launch { client.displayFrames.collect { projection.onDisplayFrame(it, session) } }
        launch { client.displayPalette.filterNotNull().collect { projection.onDisplayPalette(it, session) } }
        launch {
            client.events.filterIsInstance<MeshEvent.QueueStatusChanged>().collect {
                projection.refreshLocalNodeLastHeard(session)
            }
        }
    }

    private suspend fun reportLinkSetup(transportState: StateFlow<TransportState>) {
        transportState.first { state ->
            val phase =
                when (state) {
                    TransportState.Bonding -> TransportPhase.Pairing

                    TransportState.Connecting -> TransportPhase.Connecting

                    TransportState.Connected -> TransportPhase.HandshakeConfig

                    TransportState.Disconnected,
                    is TransportState.Error,
                    -> null
                }
            if (phase != null) service.onStatus(TransportStatus(phase))
            state == TransportState.Connected || state is TransportState.Error
        }
    }

    private suspend fun reportAppReady() {
        service.runWithSessionLease(session) {
            safeCatching {
                connectionManager.value.onRadioConfigLoaded()
                connectionManager.value.onNodeDbReady()
            }
                .onFailure { failure ->
                    Logger.w { "SDK PhoneAPI app readiness callback failed: ${failure::class.simpleName}" }
                }
        }
    }

    override suspend fun deliverMqttProxy(message: MqttClientProxyMessage): Boolean =
        currentClient.value?.mqttProxy?.deliver(message) == true

    override fun handleSendToRadio(p: ByteArray): Boolean = safeCatching {
        val frame = ToRadio.ADAPTER.decode(p)
        frame.want_config_id == null &&
            frame.heartbeat == null &&
            frame.disconnect != true &&
            currentClient.value?.trySendRaw(frame) == true
    }
        .getOrDefault(false)

    override suspend fun close() = withContext(NonCancellable) {
        val job = run ?: return@withContext
        try {
            job.cancelAndJoin()
        } finally {
            run = null
            lockdownCoordinator.value.onDisconnect()
        }
    }
}

/** Routes the SDK engine's log lines into the app's log at the same severity. */
private val sdkLogSink = LogSink { level, tag, message, cause ->
    val logger = Logger.withTag("SDK/$tag")
    when (level) {
        LogLevel.VERBOSE -> logger.v(cause) { message }
        LogLevel.DEBUG -> logger.d(cause) { message }
        LogLevel.INFO -> logger.i(cause) { message }
        LogLevel.WARN -> logger.w(cause) { message }
        LogLevel.ERROR -> logger.e(cause) { message }
        LogLevel.NONE -> Unit
    }
}
