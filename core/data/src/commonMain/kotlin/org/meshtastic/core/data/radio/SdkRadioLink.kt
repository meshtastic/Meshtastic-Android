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
package org.meshtastic.core.data.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.manager.RadioSessionProjection
import org.meshtastic.core.data.manager.sdkLogSink
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionHost
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.sdk.AutoReconnectConfig
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.MeshEvent
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioSessionEvent
import org.meshtastic.sdk.RadioSessionOverflowException
import org.meshtastic.sdk.RadioTransportProvider
import org.meshtastic.sdk.TransportIdentity
import kotlin.time.Duration.Companion.seconds

/** The reconnect schedule: a BLE retry first waits for firmware to free its GATT session, and settles sooner. */
fun radioReconnectConfig(ble: Boolean): AutoReconnectConfig = AutoReconnectConfig(
    initialBackoff = if (ble) 3.seconds else 1.seconds,
    maxBackoff = 30.seconds,
    stableAfter = if (ble) 5.seconds else 30.seconds,
    maxHandshakeRecoveries = 3,
    jitter = 0.0,
)

/** Builds the SDK client of a selected radio and the pipeline that writes its sessions into the app. */
@Single
class RadioSessionPipeline(
    private val projection: Lazy<RadioSessionProjection>,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val lockdownCoordinator: Lazy<LockdownCoordinator>,
) {
    /**
     * A link whose client asks [provider] for a transport before every attempt.
     *
     * @param gattCache the link's Bluetooth transport, whose cached GATT table a firmware update can make stale
     * @param failureReason reads a failure of this kind of link in terms the user can act on
     * @param close releases what [provider] holds once the client has disconnected
     */
    @Suppress("LongParameterList")
    fun link(
        identity: TransportIdentity,
        ble: Boolean,
        scope: CoroutineScope,
        provider: RadioTransportProvider,
        gattCache: GattCacheRefresh? = null,
        failureReason: (MeshtasticException) -> TransportDisconnectReason? = { null },
        close: () -> Unit = {},
    ): RadioLink {
        val client =
            RadioClient.Builder()
                .transport(identity, provider)
                .storage(InMemoryStorageProvider())
                .logger(sdkLogSink)
                .coroutineContext(scope.coroutineContext)
                .sessionReceive()
                .autoReconnect(radioReconnectConfig(ble))
                .build()
        return SdkRadioLink(client, this, gattCache, failureReason, close)
    }

    internal val sessionProjection: RadioSessionProjection
        get() = projection.value

    internal val connections: MeshConnectionManager
        get() = connectionManager.value

    internal val lockdown: LockdownCoordinator
        get() = lockdownCoordinator.value
}

/** Requests a refresh of Android's cached GATT table on the next connect. */
fun interface GattCacheRefresh {
    fun refreshOnNextConnect()
}

/** One selected radio's SDK client, which owns reconnecting, and the app sessions it feeds through a host. */
internal class SdkRadioLink(
    override val client: RadioClient,
    private val pipeline: RadioSessionPipeline,
    private val gattCache: GattCacheRefresh?,
    private val reasonOf: (MeshtasticException) -> TransportDisconnectReason?,
    private val onClose: () -> Unit,
) : RadioLink {

    private val current = MutableStateFlow<RadioSessionContext?>(null)
    private var sdkSession: Long? = null

    override fun refreshGattCacheOnNextConnect(): Boolean {
        gattCache?.refreshOnNextConnect() ?: return false
        return true
    }

    override fun failureReason(cause: MeshtasticException): TransportDisconnectReason? = reasonOf(cause)

    override fun close() = onClose()

    override suspend fun run(host: RadioSessionHost) {
        try {
            coroutineScope {
                val sideFlows = launch { collectSideFlows() }
                val events = checkNotNull(client.sessionEvents)
                var streaming = true
                while (streaming) {
                    try {
                        consume(events, host)
                        streaming = false
                    } catch (overflow: RadioSessionOverflowException) {
                        // The SDK restarts the session; collecting again resumes with its Ended, then the next Ready.
                        Logger.w(overflow) { "SDK session ${overflow.sessionId} lost receive coverage" }
                    }
                }
                sideFlows.cancelAndJoin()
            }
        } finally {
            withContext(NonCancellable) { endSession(host) }
        }
    }

    private suspend fun consume(events: Flow<RadioSessionEvent>, host: RadioSessionHost) {
        events.collect { event ->
            if (event is RadioSessionEvent.Ended) {
                if (event.sessionId == sdkSession) endSession(host)
                return@collect
            }
            // A session that stopped at Lockdown ends without an Ended; the next session's first event closes it.
            if (event.sessionId != sdkSession) {
                endSession(host)
                val opened = host.openSession() ?: return@collect
                pipeline.lockdown.onConnect()
                sdkSession = event.sessionId
                current.value = opened
            }
            val session = current.value ?: return@collect
            when (event) {
                is RadioSessionEvent.Lockdown ->
                    host.runWithSessionLease(session) { pipeline.lockdown.handleLockdownStatus(event.status) }

                is RadioSessionEvent.Ready -> install(event, session, host)

                else -> pipeline.sessionProjection.project(event, session)
            }
        }
    }

    private suspend fun install(event: RadioSessionEvent.Ready, session: RadioSessionContext, host: RadioSessionHost) {
        if (!pipeline.sessionProjection.installReady(event, session)) {
            Logger.w { "SDK session snapshot was not installed: app session ${session.generation} closed first" }
            return
        }
        Logger.i { "SDK session snapshot installed for app session ${session.generation}" }
        host.runWithSessionLease(session) {
            safeCatching {
                pipeline.connections.onRadioConfigLoaded()
                pipeline.connections.onNodeDbReady()
            }
                .onFailure { failure ->
                    Logger.w { "App readiness callback failed: ${failure::class.simpleName}" }
                }
        }
    }

    private suspend fun endSession(host: RadioSessionHost) {
        val session = current.value ?: return
        current.value = null
        sdkSession = null
        host.closeSession(session)
        pipeline.lockdown.onDisconnect()
    }

    /** The SDK's lossy side streams, which carry no session ID, go to whichever app session is open. */
    private suspend fun collectSideFlows() = coroutineScope {
        val projection = pipeline.sessionProjection
        launch { client.deviceLogs.collect { log -> current.value?.let { projection.onDeviceLog(log, it) } } }
        launch {
            client.outboundPackets.collect { packet -> current.value?.let { projection.onOutboundPacket(packet, it) } }
        }
        launch {
            client.displayFrames.collect { frame -> current.value?.let { projection.onDisplayFrame(frame, it) } }
        }
        launch {
            client.displayPalette.filterNotNull().collect { palette ->
                current.value?.let { projection.onDisplayPalette(palette, it) }
            }
        }
        launch {
            client.events.filterIsInstance<MeshEvent.QueueStatusChanged>().collect {
                current.value?.let { projection.refreshLocalNodeLastHeard(it) }
            }
        }
    }
}
