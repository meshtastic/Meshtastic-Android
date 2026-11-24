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
package org.meshtastic.core.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.di.PROCESS_LIFECYCLE
import org.meshtastic.core.common.state.RadioOperation
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.ignoreExceptionSuspend
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceAddress
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.repository.RadioLinks
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionHost
import org.meshtastic.core.repository.RadioSessionLease
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.error_recovery_exhausted
import org.meshtastic.core.resources.firmware_too_old
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.websocket_connect_failed
import org.meshtastic.core.resources.websocket_handshake_rejected
import org.meshtastic.core.resources.websocket_message_too_big
import org.meshtastic.core.resources.websocket_not_supported
import org.meshtastic.core.resources.websocket_replaced_by_another_client
import org.meshtastic.core.resources.websocket_slot_contested
import org.meshtastic.core.resources.websocket_tls_failed
import org.meshtastic.proto.LockdownAuth
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.sdk.AdminSessionStatus
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.PacedMessageHandle
import org.meshtastic.sdk.RadioClient
import kotlin.concurrent.Volatile

/**
 * Immutable per-session identity. [generation] is bumped for every app session, one per SDK session of the selected
 * radio, so consumers can discard state retained from a previous session even when the selected address is unchanged.
 */
data class RadioTransportSession(val generation: Long, val address: String) {
    /** Immutable context carried with every frame admitted by this session. */
    val context = RadioSessionContext(generation = generation, address = address)

    /** Prevent accidental disclosure of the raw transport address in diagnostic interpolation. */
    override fun toString(): String = "RadioTransportSession(generation=$generation, address=...)"
}

private class SessionOperationState(var admittedOperations: Int = 0, var drainWaiter: CompletableDeferred<Unit>? = null)

/** The selected radio's open link. Each one gets its own scope, cancelled when the link closes. */
private class ActiveLink(val address: String, val link: RadioLink, val scope: CoroutineScope) {
    /** Set when teardown starts, so the link's pipeline cannot open another session. */
    @Volatile var closing = false

    var connect: Job? = null

    /** The link's session pipeline; teardown waits for it so the last session's end is written before it returns. */
    var pipeline: Job? = null

    /** Whether this link has reached [ConnectionState.Connected]; failures before that name a connect problem. */
    @Volatile var connected = false

    /** The reason last shown for this link, so an attempt that fails the same way stays quiet. */
    @Volatile var shownReason: TransportDisconnectReason? = null
}

/** The user-facing text for [reason]. Lookup fails only outside a full resource runtime; the name beats silence. */
internal suspend fun connectionErrorText(reason: TransportDisconnectReason): String = safeCatchingAll {
    getStringSuspend(reason.toConnectionErrorMessage())
}
    .getOrDefault(reason.name)

internal fun TransportDisconnectReason.toConnectionErrorMessage(): StringResource = when (this) {
    TransportDisconnectReason.ReplacedByAnotherClient -> Res.string.websocket_replaced_by_another_client
    TransportDisconnectReason.WebSocketConnectFailed -> Res.string.websocket_connect_failed
    TransportDisconnectReason.WebSocketHandshakeRejected -> Res.string.websocket_handshake_rejected
    TransportDisconnectReason.WebSocketNotSupported -> Res.string.websocket_not_supported
    TransportDisconnectReason.WebSocketTlsFailed -> Res.string.websocket_tls_failed
    TransportDisconnectReason.WebSocketMessageTooBig -> Res.string.websocket_message_too_big
    TransportDisconnectReason.WebSocketSlotContested -> Res.string.websocket_slot_contested
    TransportDisconnectReason.HandshakeRecoveryExhausted -> Res.string.error_recovery_exhausted
    TransportDisconnectReason.FirmwareTooOld -> Res.string.firmware_too_old
}

/**
 * What a connection state's failure means to the user. A reconnect that follows a working connection says nothing: the
 * state itself shows the retry.
 */
private fun ActiveLink.failureReason(state: ConnectionState): TransportDisconnectReason? = when (state) {
    is ConnectionState.Failed ->
        when (state.cause) {
            is MeshtasticException.FirmwareTooOld -> TransportDisconnectReason.FirmwareTooOld

            is MeshtasticException.HandshakeTimeout,
            is MeshtasticException.Protocol,
            -> TransportDisconnectReason.HandshakeRecoveryExhausted

            else -> link.failureReason(state.cause)
        }

    is ConnectionState.Reconnecting -> if (connected) null else link.failureReason(state.cause)

    else -> null
}

/**
 * The selected radio's SDK client and the app sessions it carries.
 *
 * The SDK client owns connecting, backoff and reconnecting. This service builds one client per selected device through
 * the platform's [RadioLinks], connects and disconnects it, asks it to retry at once when Bluetooth, the network or the
 * selected USB device comes back, keeps it off the radio while a [RadioOperation] needs the port, and publishes its
 * [ConnectionState] to [ServiceStateWriter]. [ConnectionState.Connected] is published by the snapshot install instead,
 * once the app holds the session's config and nodes.
 */
@Suppress("LargeClass", "LongParameterList", "TooManyFunctions")
@Single
class SharedRadioInterfaceService(
    private val dispatchers: CoroutineDispatchers,
    private val bluetoothRepository: BluetoothRepository,
    private val networkRepository: NetworkRepository,
    private val serialDevicePresence: SerialDevicePresence,
    @Named(PROCESS_LIFECYCLE) private val processLifecycle: Lifecycle,
    private val radioPrefs: RadioPrefs,
    private val links: RadioLinks,
    private val analytics: PlatformAnalytics,
    private val radioOperationLock: RadioOperationLock,
    private val serviceStateWriter: ServiceStateWriter,
) : RadioInterfaceService {

    override val supportedDeviceTypes: List<DeviceType>
        get() = links.supportedDeviceTypes

    private val _currentDeviceAddressFlow = MutableStateFlow<String?>(radioPrefs.devAddr.value)
    override val currentDeviceAddressFlow: StateFlow<String?> = _currentDeviceAddressFlow.asStateFlow()

    private val selectionLock = SynchronizedObject()

    /** Set by the first [setDeviceAddress]; from then on this process owns the selection, not the prefs mirror. */
    private var selectionPublished = false

    private val sessionGenerationCounter = atomic(0L)
    private val _sessionGeneration = MutableStateFlow(0L)
    override val sessionGeneration: StateFlow<Long> = _sessionGeneration.asStateFlow()

    private val _activeSession = MutableStateFlow<RadioSessionContext?>(null)
    override val activeSession: StateFlow<RadioSessionContext?> = _activeSession.asStateFlow()

    /**
     * The session that owns lifecycle completion. [revokeTransportSession] closes its admission first, waits for every
     * already-admitted operation, then clears this token. Callbacks are rejected as soon as admission closes.
     */
    @Volatile private var activeTransportSession: RadioTransportSession? = null

    /** Serializes session publication/revocation with callback and suspend-operation admission. */
    private val sessionCallbackLock = SynchronizedObject()

    /** Guarded by [sessionCallbackLock]. New work is rejected immediately when teardown closes this gate. */
    private var sessionAdmissionOpen = false

    /** Per-generation operation ownership, guarded by [sessionCallbackLock]. */
    private val sessionOperationStates = mutableMapOf<Long, SessionOperationState>()

    /** Preserves FIFO ordering for handshake work without blocking independently leased packet side effects. */
    private val sessionOperationMutex = Mutex()

    private val activeLink = MutableStateFlow<ActiveLink?>(null)

    /** Serializes publication of the link's state with the link changing. */
    private val stateLock = SynchronizedObject()

    /**
     * True while the user wants the selected radio connected: set by [connect] and by selecting a radio, cleared by
     * [disconnect] and by deselecting. Guarded by [linkMutex].
     */
    @Volatile private var connectionRequested = false

    /** A firmware update asked for the next Bluetooth link to refresh its GATT cache. */
    private val gattRefreshPending = atomic(false)

    private val listenersInitialized = atomic(false)
    private val initLock = Mutex()
    private val linkMutex = Mutex()

    private val _meshActivity =
        MutableSharedFlow<MeshActivity>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val meshActivity: Flow<MeshActivity> = _meshActivity.asFlow()

    private val _connectionError = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val connectionError: Flow<String> = _connectionError.asFlow()

    // Text lookup suspends, so one consumer keeps the errors in the order their states arrived.
    private val connectionErrorReasons = Channel<TransportDisconnectReason>(Channel.UNLIMITED)

    companion object {
        /**
         * Ceiling on a single [runWhileSessionActive] handler. The block holds [sessionOperationMutex] — the whole
         * inbound pipeline — so a handler that suspends indefinitely wedges packet processing, backs up the SDK session
         * stream, and deadlocks teardown's lease drain (a Connected-looking zombie only a force-stop clears; observed
         * in the field as multi-hour "receive queue at capacity" sessions). 2 minutes is far above any legitimate
         * handler (large-mesh config DB installs run seconds) while still bounding the wedge.
         */
        private const val SESSION_HANDLER_TIMEOUT_MILLIS = 2 * 60 * 1000L

        /** How often [revokeTransportSession] reports a lease drain that has not completed. */
        private const val DRAIN_WAIT_LOG_INTERVAL_MILLIS = 15 * 1000L
    }

    init {
        // Mirrors the saved selection so observers (MeshServiceOrchestrator switches the database) see it before any
        // link opens. It stops at the first setDeviceAddress(): prefs commit asynchronously, so a later emission can
        // carry an older selection than the one already published.
        radioPrefs.devAddr
            .onEach { addr ->
                synchronized(selectionLock) { if (!selectionPublished) _currentDeviceAddressFlow.value = addr }
            }
            .catch { Logger.e(it) { "radioPrefs.devAddr address-sync flow crashed" } }
            .launchIn(processLifecycle.coroutineScope)
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            for (reason in connectionErrorReasons) _connectionError.emit(connectionErrorText(reason))
        }
    }

    // ── Session admission ───────────────────────────────────────────────────

    override fun isSessionActive(session: RadioSessionContext): Boolean =
        synchronized(sessionCallbackLock) { sessionAdmissionOpen && activeTransportSession?.context == session }

    override fun runIfSessionActive(session: RadioSessionContext, block: () -> Unit): Boolean =
        synchronized(sessionCallbackLock) {
            if (!sessionAdmissionOpen || activeTransportSession?.context != session) return@synchronized false
            block()
            true
        }

    override suspend fun runWithSessionLease(
        session: RadioSessionContext,
        block: suspend (RadioSessionLease) -> Unit,
    ): Boolean {
        val admittedSession =
            synchronized(sessionCallbackLock) {
                val active = activeTransportSession
                if (!sessionAdmissionOpen || active?.context != session) {
                    null
                } else {
                    checkNotNull(sessionOperationStates[active.generation]).admittedOperations++
                    active
                }
            } ?: return false

        val lease =
            object : RadioSessionLease {
                override val session: RadioSessionContext = session

                override fun isCurrent(): Boolean =
                    synchronized(sessionCallbackLock) { activeTransportSession === admittedSession }
            }

        try {
            block(lease)
            return true
        } finally {
            releaseSessionOperation(admittedSession)
        }
    }

    override suspend fun runWhileSessionActive(
        session: RadioSessionContext,
        label: String,
        block: suspend () -> Unit,
    ): Boolean = sessionOperationMutex.withLock {
        runWithSessionLease(session) {
            // Bound the handler: it holds sessionOperationMutex (the whole inbound pipeline) and an admitted
            // lease (which teardown's drain awaits), so an indefinite suspension here is a total wedge, not a
            // slow packet. Cancelling the block releases both. Only OUR timeout is swallowed — ensureActive()
            // rethrows if the surrounding scope was cancelled concurrently.
            try {
                withTimeout(SESSION_HANDLER_TIMEOUT_MILLIS) { block() }
            } catch (timeout: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                Logger.e(timeout) {
                    "Session handler exceeded ${SESSION_HANDLER_TIMEOUT_MILLIS}ms and was cancelled; " +
                        "dropping its packet to keep the receive pipeline alive (handler=$label)"
                }
            }
        }
    }

    private fun releaseSessionOperation(admittedSession: RadioTransportSession) {
        val drainWaiter =
            synchronized(sessionCallbackLock) {
                val state = sessionOperationStates[admittedSession.generation]
                if (state == null) {
                    Logger.e { "Session operation released after generation ${admittedSession.generation} was revoked" }
                    return@synchronized null
                }
                if (state.admittedOperations <= 0) {
                    Logger.e { "Session operation count underflow for generation ${admittedSession.generation}" }
                    return@synchronized state.drainWaiter.also { state.drainWaiter = null }
                }
                state.admittedOperations--
                if (state.admittedOperations == 0) {
                    state.drainWaiter.also { state.drainWaiter = null }
                } else {
                    null
                }
            }
        drainWaiter?.complete(Unit)
    }

    /** Removes a drained session's operation state without letting a diagnostic invariant failure block teardown. */
    private fun removeDrainedSessionStateLocked(session: RadioTransportSession): CompletableDeferred<Unit>? {
        val state = sessionOperationStates.remove(session.generation)
        if (state == null) {
            Logger.e { "Session generation ${session.generation} lost its operation state during drain" }
            return null
        }
        if (state.admittedOperations != 0) {
            Logger.e {
                "Session generation ${session.generation} revoked with " +
                    "${state.admittedOperations} admitted operation(s) still outstanding"
            }
        }
        return state.drainWaiter?.also {
            Logger.e { "Session generation ${session.generation} retained a drain waiter" }
            state.drainWaiter = null
        }
    }

    /** Admits a new session for [active], or null once [active] is no longer the selected radio's open link. */
    private fun admitSession(active: ActiveLink): RadioSessionContext? = synchronized(sessionCallbackLock) {
        if (activeLink.value !== active || active.closing) return@synchronized null
        check(activeTransportSession == null && sessionOperationStates.isEmpty()) {
            "Cannot admit a session while the previous one is still draining"
        }
        val session = RadioTransportSession(sessionGenerationCounter.incrementAndGet(), active.address)
        sessionOperationStates[session.generation] = SessionOperationState()
        activeTransportSession = session
        sessionAdmissionOpen = true
        _activeSession.value = session.context
        _sessionGeneration.value = session.generation
        Logger.i { "Admitted session ${session.generation} for ${active.address.anonymize}" }
        session.context
    }

    /**
     * Closes admission for [session], waits for its existing suspend operations, then publishes lifecycle completion.
     * The closed gate rejects queued callbacks and new operations immediately; the retained session token prevents a
     * replacement session from overlapping an admitted database commit.
     */
    private suspend fun revokeTransportSession(session: RadioTransportSession?) {
        if (session == null) return
        withContext(NonCancellable) {
            val drainWaiter =
                synchronized(sessionCallbackLock) {
                    if (activeTransportSession !== session) return@synchronized null
                    sessionAdmissionOpen = false
                    val state = sessionOperationStates[session.generation]
                    if (state == null) {
                        Logger.e { "Session generation ${session.generation} has no operation state at revocation" }
                        null
                    } else if (state.admittedOperations == 0) {
                        null
                    } else {
                        state.drainWaiter ?: CompletableDeferred<Unit>().also { state.drainWaiter = it }
                    }
                }
            if (drainWaiter != null) {
                // The drain must complete before a replacement session is admitted (DB-atomicity contract), so keep
                // waiting, but never silently: a lease stuck past the handler timeout means a handler ignored
                // cancellation, and these reports are the observability surface for that wedge.
                var waitedMillis = 0L
                while (withTimeoutOrNull(DRAIN_WAIT_LOG_INTERVAL_MILLIS) { drainWaiter.await() } == null) {
                    waitedMillis += DRAIN_WAIT_LOG_INTERVAL_MILLIS
                    val outstanding =
                        synchronized(sessionCallbackLock) {
                            sessionOperationStates[session.generation]?.admittedOperations ?: 0
                        }
                    Logger.e {
                        "Session teardown blocked ${waitedMillis}ms waiting for $outstanding admitted session " +
                            "operation(s) to release (generation=${session.generation})"
                    }
                }
            }
            val retainedDrainWaiter =
                synchronized(sessionCallbackLock) {
                    if (activeTransportSession === session) {
                        val waiter = removeDrainedSessionStateLocked(session)
                        activeTransportSession = null
                        _activeSession.value = null
                        waiter
                    } else {
                        null
                    }
                }
            retainedDrainWaiter?.complete(Unit)
        }
    }

    private fun sessionFor(context: RadioSessionContext): RadioTransportSession? = activeTransportSession?.takeIf {
        it.context == context
    }

    /** The host one link's pipeline admits its sessions through; it stops admitting once that link closes. */
    private inner class LinkHost(private val active: ActiveLink) :
        RadioSessionHost,
        RadioInterfaceService by this@SharedRadioInterfaceService {
        override fun openSession(): RadioSessionContext? = admitSession(active)

        override suspend fun closeSession(session: RadioSessionContext) {
            revokeTransportSession(synchronized(sessionCallbackLock) { sessionFor(session) })
            // A snapshot install can publish Connected after the link dropped; the drop's state wins once it closes.
            publishState(active)
        }
    }

    // ── The selected radio's link ───────────────────────────────────────────

    /** Publishes [active]'s client state; the snapshot install publishes [ConnectionState.Connected] itself. */
    private fun publishState(active: ActiveLink) = synchronized(stateLock) {
        if (activeLink.value !== active) return@synchronized
        val state = active.link.connection.value
        if (state !is ConnectionState.Connected) serviceStateWriter.setConnectionState(state)
    }

    private fun onLinkState(active: ActiveLink, state: ConnectionState) {
        publishState(active)
        if (state is ConnectionState.Connected) {
            active.connected = true
            active.shownReason = null
        }
        val reason = active.failureReason(state) ?: return
        if (reason == active.shownReason) return
        active.shownReason = reason
        connectionErrorReasons.trySend(reason)
    }

    /** Opens the selected radio's link and starts its client. Must be called under [linkMutex]. */
    private fun openLinkLocked() {
        val address = getBondedDeviceAddress()
        when {
            activeLink.value != null -> Unit
            radioOperationLock.suppressesTransport -> Logger.d { "No link while an operation holds the port" }
            address == null -> Logger.d { "No valid address to connect to" }
            else -> openLink(address)
        }
    }

    private fun openLink(address: String) {
        val scope = CoroutineScope(dispatchers.io + SupervisorJob())
        val link = safeCatching {
            links.open(address, scope)
        }
            .onFailure { Logger.w(it) { "Could not open a link for ${address.anonymize}" } }
            .getOrNull()
        if (link == null) {
            scope.cancel()
            return
        }
        if (gattRefreshPending.value && link.refreshGattCacheOnNextConnect()) gattRefreshPending.value = false
        val active = ActiveLink(address, link, scope)
        synchronized(stateLock) { activeLink.value = active }
        Logger.i { "Opened the link for ${address.anonymize}" }
        active.pipeline = scope.handledLaunch { link.run(LinkHost(active)) }
        link.connection
            .onEach { onLinkState(active, it) }
            .catch { Logger.e(it) { "Link state handling failed for ${address.anonymize}" } }
            .launchIn(scope)
        active.connect = scope.launch {
            // With auto-reconnect on, connect() returns at the first Connected and throws only on Failed, which
            // the state already shows.
            safeCatching { link.connect() }.onFailure { Logger.w { "Connect ended: ${it::class.simpleName}" } }
        }
    }

    /** Drains the open link's session, disconnects its client and releases it. Must be called under [linkMutex]. */
    private suspend fun closeLinkLocked() = withContext(NonCancellable) {
        val active = activeLink.value ?: return@withContext
        active.closing = true
        revokeTransportSession(synchronized(sessionCallbackLock) { activeTransportSession })
        try {
            active.link.disconnect()
            active.pipeline?.cancelAndJoin()
        } finally {
            synchronized(stateLock) {
                activeLink.value = null
                serviceStateWriter.setConnectionState(ConnectionState.Disconnected)
            }
            ignoreExceptionSuspend { active.link.close() }
            active.scope.cancel("link closed")
            Logger.i { "Closed the link for ${active.address.anonymize}" }
        }
    }

    private fun retryNow() {
        activeLink.value?.link?.retryNow()
    }

    private fun initStateListeners() {
        if (listenersInitialized.value) return
        processLifecycle.coroutineScope.launch {
            initLock.withLock {
                if (listenersInitialized.value) return@withLock
                listenersInitialized.value = true

                // The client waits for Bluetooth, a network or the USB device by itself; these end a backoff early.
                bluetoothRepository.state
                    .map { it.enabled }
                    .distinctUntilChanged()
                    .filter { it }
                    .onEach { retryNow() }
                    .catch { Logger.e(it) { "bluetoothRepository.state flow crashed" } }
                    .launchIn(processLifecycle.coroutineScope)

                networkRepository.networkAvailable
                    .distinctUntilChanged()
                    .filter { it }
                    .onEach { retryNow() }
                    .catch { Logger.e(it) { "networkRepository.networkAvailable flow crashed" } }
                    .launchIn(processLifecycle.coroutineScope)

                combine(currentDeviceAddressFlow, serialDevicePresence.deviceKeys, ::selectedSerialPresent)
                    .distinctUntilChanged()
                    .drop(1)
                    .filter { it }
                    .onEach { retryNow() }
                    .catch { Logger.e(it) { "serialDevicePresence flow crashed" } }
                    .launchIn(processLifecycle.coroutineScope)

                observeRadioOperations()
            }
        }
    }

    /** A firmware-maintenance operation owns the port: the link closes for it and reopens when it ends. */
    private fun observeRadioOperations() {
        radioOperationLock.holders
            .map { holders -> holders.values.any(RadioOperation::suppressesTransport) }
            .distinctUntilChanged()
            .drop(1)
            .onEach { suppressed ->
                linkMutex.withLock {
                    if (suppressed) {
                        ignoreExceptionSuspend { closeLinkLocked() }
                    } else if (connectionRequested) {
                        openLinkLocked()
                    }
                }
            }
            .catch { Logger.e(it) { "radioOperationLock flow crashed" } }
            .launchIn(processLifecycle.coroutineScope)
    }

    override fun connect() {
        processLifecycle.coroutineScope.launch {
            linkMutex.withLock {
                connectionRequested = true
                val active = activeLink.value
                val state = active?.link?.connection?.value
                val stopped =
                    state is ConnectionState.Failed ||
                        (state is ConnectionState.Disconnected && active.connect?.isCompleted == true)
                when {
                    active == null -> openLinkLocked()

                    // A client that failed or stopped gets replaced: its session stream ended with it.
                    stopped -> {
                        ignoreExceptionSuspend { closeLinkLocked() }
                        openLinkLocked()
                    }

                    else -> active.link.retryNow()
                }
            }
        }
        initStateListeners()
    }

    override suspend fun disconnect() {
        linkMutex.withLock {
            connectionRequested = false
            gattRefreshPending.value = false
            ignoreExceptionSuspend { closeLinkLocked() }
        }
    }

    override fun requestGattCacheInvalidationOnNextConnect() {
        val applied = activeLink.value?.link?.refreshGattCacheOnNextConnect() == true
        gattRefreshPending.value = !applied
        Logger.d { "GATT cache refresh requested for the next Bluetooth connect" }
    }

    override val mockTransportEnabled: StateFlow<Boolean>
        get() = links.mockTransportEnabled

    override val isReplayTransportAvailable: Boolean
        get() = links.isReplayTransportAvailable

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String =
        links.toInterfaceAddress(interfaceId, rest)

    override fun getDeviceAddress(): String? = _currentDeviceAddressFlow.value

    private fun getBondedDeviceAddress(): String? = getDeviceAddress()?.takeIf(links::isAddressValid)

    override fun setDeviceAddress(deviceAddr: String?): Boolean {
        val sanitized = if (deviceAddr == "n" || deviceAddr.isNullOrBlank()) null else deviceAddr

        val active = activeLink.value
        if (
            active != null && active.address == sanitized && active.link.connection.value is ConnectionState.Connected
        ) {
            Logger.w { "Ignoring setBondedDevice ${active.address.anonymize}, already using that device" }
            return false
        }

        val previousAddress = getBondedDeviceAddress()
        analytics.track("mesh_bond")
        Logger.d { "Setting bonded device to ${sanitized?.anonymize}" }
        radioPrefs.setDevAddr(sanitized)
        synchronized(selectionLock) {
            selectionPublished = true
            _currentDeviceAddressFlow.value = sanitized
        }

        processLifecycle.coroutineScope.launch {
            linkMutex.withLock {
                // A real address arms the connection, so environment observers cannot race the switch into a down
                // state; a deselect clears it, so nothing can bring back a radio the user put away.
                connectionRequested = sanitized != null
                if (sanitized != null && previousAddress != null && sanitized != previousAddress) {
                    gattRefreshPending.value = false
                }
                ignoreExceptionSuspend { closeLinkLocked() }
                if (sanitized != null) openLinkLocked()
            }
        }
        initStateListeners()
        return true
    }

    // ── Session-bound client access ─────────────────────────────────────────

    override fun clientForSession(session: RadioSessionContext): RadioClient? {
        var client: RadioClient? = null
        runIfSessionActive(session) { client = activeLink.value?.link?.client }
        return client
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun adminSessionStatus(node: NodeId): Flow<AdminSessionStatus> = activeLink.flatMapLatest {
        it?.link?.client?.adminSessionStatus(node) ?: flowOf(AdminSessionStatus.NoSession)
    }

    override fun requestNodeDbRefresh(session: RadioSessionContext): Boolean =
        clientForSession(session)?.requestNodeDbRefresh() == true

    /** Runs [send] on [session]'s client under a lease, so teardown drains the handoff before revoking the session. */
    private inline fun <T> withAdmittedClient(session: RadioSessionContext?, send: (ActiveLink) -> T?): T? {
        val admitted =
            synchronized(sessionCallbackLock) {
                val current = activeTransportSession
                val link = activeLink.value
                if (!sessionAdmissionOpen || current == null || link == null) return@synchronized null
                if (session != null && current.context != session) return@synchronized null
                checkNotNull(sessionOperationStates[current.generation]).admittedOperations++
                current to link
            } ?: return null
        return try {
            safeCatching { send(admitted.second) }
                .onFailure { Logger.w(it) { "Active session handoff failed" } }
                .getOrNull()
        } finally {
            releaseSessionOperation(admitted.first)
        }
    }

    override fun sendPaced(session: RadioSessionContext, packet: MeshPacket): PacedMessageHandle? {
        val (handle, linkScope) =
            withAdmittedClient(session) { it.link.client.sendPaced(packet) to it.scope } ?: return null
        linkScope.launch {
            if (handle.awaitDispatch()) runIfSessionActive(session) { _meshActivity.tryEmit(MeshActivity.Send) }
        }
        return handle
    }

    override fun tryLockdown(session: RadioSessionContext, auth: LockdownAuth): Boolean {
        val sent = withAdmittedClient(session) { it.link.client.tryLockdown(auth) } == true
        if (sent) _meshActivity.tryEmit(MeshActivity.Send)
        return sent
    }

    override suspend fun deliverMqttProxy(message: MqttClientProxyMessage): Boolean {
        val sent = withAdmittedClient(null) { it.link.client.mqttProxy.deliver(message) } == true
        if (sent) _meshActivity.tryEmit(MeshActivity.Send)
        return sent
    }

    override fun reportReceiveActivity() {
        _meshActivity.tryEmit(MeshActivity.Receive)
    }
}

/** Whether the selected radio is a USB serial device that is attached now. */
private fun selectedSerialPresent(address: String?, keys: Set<String>): Boolean {
    val key =
        DeviceAddress.parse(address)
            ?.takeIf { it.interfaceId == InterfaceId.SERIAL }
            ?.identity
            ?.takeIf {
                it.isNotEmpty()
            }
    return key != null && key in keys
}
