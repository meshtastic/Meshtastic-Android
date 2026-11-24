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
import org.meshtastic.proto.DisplayPalette
import org.meshtastic.proto.LogRecord
import org.meshtastic.sdk.AutoReconnectConfig
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.MeshEvent
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.RadioSessionEvent
import org.meshtastic.sdk.RadioSessionOverflowException
import org.meshtastic.sdk.RadioTransportProvider
import org.meshtastic.sdk.SessionScoped
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

    private data class Slot(val sdkId: Long, val context: RadioSessionContext)

    private val current = MutableStateFlow<Slot?>(null)

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
                val events = client.sessionEvents
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

    internal suspend fun consume(events: Flow<RadioSessionEvent>, host: RadioSessionHost) {
        events.collect { event ->
            if (event is RadioSessionEvent.Ended) {
                // An Ended for an id that is not the open slot (already closed, or never opened) is a no-op.
                if (current.value?.sdkId == event.sessionId) endSession(host)
                return@collect
            }
            val slot =
                current.value?.takeIf { it.sdkId == event.sessionId } ?: openSession(host, event.sessionId)
                    ?: return@collect
            val session = slot.context
            // Nothing restarts this collector, so one event the app fails to write must not end the session's stream.
            safeCatching {
                when (event) {
                    is RadioSessionEvent.Lockdown ->
                        host.runWithSessionLease(session) { pipeline.lockdown.handleLockdownStatus(event.status) }

                    is RadioSessionEvent.Ready -> install(event, session, host)

                    else -> pipeline.sessionProjection.project(event, session)
                }
            }
                .onFailure { Logger.e(it) { "Writing SDK session event ${event::class.simpleName} failed" } }
        }
    }

    private suspend fun install(event: RadioSessionEvent.Ready, session: RadioSessionContext, host: RadioSessionHost) {
        if (!pipeline.sessionProjection.installReady(event, session)) {
            Logger.w { "SDK session snapshot was not installed for app session ${session.generation}" }
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

    private suspend fun openSession(host: RadioSessionHost, sdkId: Long): Slot? {
        endSession(host) // close-before-open: a new id means the previous session is over
        val opened = host.openSession() ?: return null
        pipeline.lockdown.onConnect()
        return Slot(sdkId, opened).also { current.value = it }
    }

    private suspend fun endSession(host: RadioSessionHost) {
        val slot = current.value ?: return
        current.value = null
        host.closeSession(slot.context)
        pipeline.lockdown.onDisconnect()
    }

    internal fun routeDeviceLog(item: SessionScoped<LogRecord>) {
        current.value?.takeIf { it.sdkId == item.sessionId }?.let {
            pipeline.sessionProjection.onDeviceLog(item.value, it.context)
        }
    }

    internal fun routeDisplayPalette(palette: DisplayPalette) {
        current.value?.context?.let { pipeline.sessionProjection.onDisplayPalette(palette, it) }
    }

    /**
     * Tagged streams go to the open session when their ID matches it. The palette and queue status carry no session ID
     * and go to the open session, if any.
     */
    private suspend fun collectSideFlows() = coroutineScope {
        val projection = pipeline.sessionProjection
        launch { client.deviceLogs.collectGuarded("device log") { routeDeviceLog(it) } }
        launch {
            client.outboundPackets.collectGuarded("outbound packet") { item ->
                current.value?.takeIf { it.sdkId == item.sessionId }?.let {
                    projection.onOutboundPacket(item.value, it.context)
                }
            }
        }
        launch {
            client.displayFrames.collectGuarded("display frame") { item ->
                current.value?.takeIf { it.sdkId == item.sessionId }?.let {
                    projection.onDisplayFrame(item.value, it.context)
                }
            }
        }
        launch {
            client.displayPalette.filterNotNull().collectGuarded("display palette") { routeDisplayPalette(it) }
        }
        launch {
            client.events.filterIsInstance<MeshEvent.QueueStatusChanged>().collectGuarded("queue status") {
                current.value?.context?.let { projection.refreshLocalNodeLastHeard(it) }
            }
        }
    }
}

/** Collects [this], writing each item with [handle]; one item the app fails to write must not end the stream. */
internal suspend fun <T> Flow<T>.collectGuarded(label: String, handle: suspend (T) -> Unit) = collect { item ->
    safeCatching { handle(item) }.onFailure { Logger.e(it) { "Writing SDK $label failed" } }
}
