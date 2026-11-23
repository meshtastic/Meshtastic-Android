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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
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
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.common.util.ignoreExceptionSuspend
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceAddress
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.model.TransportPhase
import org.meshtastic.core.model.TransportStatus
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionLease
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.error_recovery_exhausted
import org.meshtastic.core.resources.firmware_too_old
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.usb_permission_denied
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
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminSessionStatus
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.PacedMessageHandle
import org.meshtastic.sdk.RadioClient
import kotlin.concurrent.Volatile

/**
 * Immutable per-start transport identity. [generation] is bumped on every transport start (including same-address
 * reconnect) so the [SharedRadioInterfaceService] and its consumers can discard state retained from a previous
 * transport instance even when the selected address is unchanged.
 */
data class RadioTransportSession(val generation: Long, val address: String) {
    /** Immutable context carried with every frame admitted by this session. */
    val context = RadioSessionContext(generation = generation, address = address)

    /** Prevent accidental disclosure of the raw transport address in diagnostic interpolation. */
    override fun toString(): String = "RadioTransportSession(generation=$generation, address=...)"
}

private class SessionOperationState(var admittedOperations: Int = 0, var drainWaiter: CompletableDeferred<Unit>? = null)

private data class SelectedSerialPresence(val key: String?, val present: Boolean)

private data class UsbRecoverySnapshot(val presence: SelectedSerialPresence, val state: ConnectionState)

private data class UsbRecoveryTriggerState(
    val key: String? = null,
    val present: Boolean = false,
    val armedByAbsence: Boolean = false,
    val triggerRecovery: Boolean = false,
)

private fun selectedSerialPresence(address: String?, keys: Set<String>): SelectedSerialPresence {
    val key =
        DeviceAddress.parse(address)
            ?.takeIf { it.interfaceId == InterfaceId.SERIAL }
            ?.identity
            ?.takeIf { it.isNotEmpty() }
    return SelectedSerialPresence(key = key, present = key != null && key in keys)
}

private fun UsbRecoveryTriggerState.next(snapshot: UsbRecoverySnapshot): UsbRecoveryTriggerState {
    val key = snapshot.presence.key
    val present = snapshot.presence.present
    return when {
        key == null -> UsbRecoveryTriggerState()

        snapshot.state == ConnectionState.Disconnected -> UsbRecoveryTriggerState(key = key, present = present)

        key != this.key ->
            UsbRecoveryTriggerState(
                key = key,
                present = present,
                armedByAbsence = !present && snapshot.state == ConnectionState.DeviceSleep,
            )

        !present ->
            UsbRecoveryTriggerState(
                key = key,
                armedByAbsence = armedByAbsence || this.present || snapshot.state == ConnectionState.DeviceSleep,
            )

        else -> {
            val trigger = armedByAbsence && snapshot.state == ConnectionState.DeviceSleep
            UsbRecoveryTriggerState(
                key = key,
                present = true,
                armedByAbsence = armedByAbsence && !trigger,
                triggerRecovery = trigger,
            )
        }
    }
}

/** The user-facing text for [reason]. Lookup fails only outside a full resource runtime; the name beats silence. */
internal suspend fun connectionErrorText(reason: TransportDisconnectReason): String = safeCatchingAll {
    getStringSuspend(reason.toConnectionErrorMessage())
}
    .getOrDefault(reason.name)

private fun TransportDisconnectReason.toConnectionErrorMessage(): StringResource = when (this) {
    TransportDisconnectReason.UsbPermissionDenied -> Res.string.usb_permission_denied
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

private val LINK_UP_PHASES =
    setOf(TransportPhase.HandshakeConfig, TransportPhase.HandshakeNodes, TransportPhase.Connected)

/** Permanent closes that only the user's next connect may undo. */
private val USER_CONNECT_REASONS =
    setOf(
        TransportDisconnectReason.ReplacedByAnotherClient,
        TransportDisconnectReason.WebSocketMessageTooBig,
        TransportDisconnectReason.WebSocketSlotContested,
    )

/**
 * Shared multiplatform connection orchestrator for Meshtastic radios.
 *
 * Manages the connection lifecycle (connect, active, disconnect, reconnect loop), device address state flows, and
 * hardware state observability (BLE/Network toggles). Delegates the actual raw byte transport mapping to a
 * platform-specific [RadioTransportFactory].
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
    private val transportFactory: RadioTransportFactory,
    private val analytics: PlatformAnalytics,
    private val radioOperationLock: RadioOperationLock,
) : RadioInterfaceService {

    override val supportedDeviceTypes: List<DeviceType>
        get() = transportFactory.supportedDeviceTypes

    /**
     * Transport-level connection state reflecting the raw hardware link status.
     *
     * Updated directly by [onConnect] and [onDisconnect] when the physical transport (BLE, TCP, Serial) connects or
     * disconnects. This is consumed exclusively by
     * [MeshConnectionManager][org.meshtastic.core.repository.MeshConnectionManager], which reconciles it into the
     * canonical app-level
     * [ServiceRepository.connectionState][org.meshtastic.core.repository.ServiceRepository.connectionState].
     */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentDeviceAddressFlow = MutableStateFlow<String?>(radioPrefs.devAddr.value)
    override val currentDeviceAddressFlow: StateFlow<String?> = _currentDeviceAddressFlow.asStateFlow()

    private val selectionLock = SynchronizedObject()

    /** Set by the first [setDeviceAddress]; from then on this process owns the selection, not the prefs mirror. */
    private var selectionPublished = false

    // Monotonically increasing generation bumped on every transport start (including same-address reconnect). Exposed
    // through [sessionGeneration] so the controller layer can clear connection-session identity at each session
    // boundary instead of only when the selected address changes.
    private val sessionGenerationCounter = atomic(0L)
    private val _sessionGeneration = MutableStateFlow(0L)
    override val sessionGeneration: StateFlow<Long> = _sessionGeneration.asStateFlow()

    private val _activeSession = MutableStateFlow<RadioSessionContext?>(null)
    override val activeSession: StateFlow<RadioSessionContext?> = _activeSession.asStateFlow()

    /**
     * The transport session that owns lifecycle completion. [stopTransportLocked] closes its admission first, waits for
     * every already-admitted operation, then clears this token before closing the underlying transport. Close-time and
     * post-close callbacks are rejected as soon as admission closes.
     */
    @Volatile private var activeTransportSession: RadioTransportSession? = null

    /** Serializes session publication/revocation with callback and suspend-operation admission. */
    private val sessionCallbackLock = SynchronizedObject()

    /** Guarded by [sessionCallbackLock]. New work is rejected immediately when teardown closes this gate. */
    private var sessionAdmissionOpen = false

    /** The generation whose link is up: set by its transport's connect, cleared on any disconnect. Lock-guarded. */
    private var linkedGeneration: Long? = null

    /** Per-generation operation ownership, guarded by [sessionCallbackLock]. */
    private val sessionOperationStates = mutableMapOf<Long, SessionOperationState>()

    /** Preserves FIFO ordering for handshake work without blocking independently leased packet side effects. */
    private val sessionOperationMutex = Mutex()

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

    private sealed interface TransportSendAdmission {
        data class Admitted(val session: RadioTransportSession, val transport: RadioTransport) : TransportSendAdmission

        data object AdmissionClosed : TransportSendAdmission

        data object NoActiveSession : TransportSendAdmission

        data object NoTransport : TransportSendAdmission
    }

    /** Admits one synchronous transport handoff under the same gate drained by [revokeTransportSession]. */
    private fun admitTransportSend(expected: RadioSessionContext? = null): TransportSendAdmission =
        synchronized(sessionCallbackLock) {
            val session = activeTransportSession
            val transport = radioTransport
            when {
                !sessionAdmissionOpen -> TransportSendAdmission.AdmissionClosed

                session == null || (expected != null && session.context != expected) ->
                    TransportSendAdmission.NoActiveSession

                transport == null -> TransportSendAdmission.NoTransport

                else -> {
                    val state = sessionOperationStates[session.generation]
                    if (state == null) {
                        Logger.e { "Session generation ${session.generation} has no operation state at send admission" }
                        TransportSendAdmission.NoActiveSession
                    } else {
                        state.admittedOperations++
                        TransportSendAdmission.Admitted(session = session, transport = transport)
                    }
                }
            }
        }

    /** Runs a callback only while [session] still owns admission, atomically with session teardown. */
    private inline fun runIfTransportSessionActive(session: RadioTransportSession, block: () -> Unit): Boolean =
        synchronized(sessionCallbackLock) {
            if (!sessionAdmissionOpen || activeTransportSession !== session) return@synchronized false
            block()
            true
        }

    /**
     * Closes admission for [session], waits for its existing suspend operations, then publishes lifecycle completion.
     * The closed gate rejects queued callbacks and new operations immediately; the retained session token prevents a
     * replacement generation from overlapping an admitted database commit.
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
                // The drain must complete before a replacement generation is admitted (DB-atomicity contract), so we
                // keep waiting — but never silently. A lease stuck past the handler timeout means a handler ignored
                // cancellation; these error-level reports are the observability surface for that wedge (this wait
                // previously blocked disconnect()/restart forever with no telemetry at all).
                var waitedMillis = 0L
                while (withTimeoutOrNull(DRAIN_WAIT_LOG_INTERVAL_MILLIS) { drainWaiter.await() } == null) {
                    waitedMillis += DRAIN_WAIT_LOG_INTERVAL_MILLIS
                    val outstanding =
                        synchronized(sessionCallbackLock) {
                            sessionOperationStates[session.generation]?.admittedOperations ?: 0
                        }
                    Logger.e {
                        "Transport teardown blocked ${waitedMillis}ms waiting for $outstanding admitted session " +
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

    private val _meshActivity =
        MutableSharedFlow<MeshActivity>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val meshActivity: Flow<MeshActivity> = _meshActivity.asFlow()

    private val _connectionError = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val connectionError: Flow<String> = _connectionError.asFlow()

    private val _transportStatus = MutableStateFlow<TransportStatus?>(null)
    override val transportStatus: StateFlow<TransportStatus?> = _transportStatus.asStateFlow()

    override val serviceScope: CoroutineScope
        get() = _serviceScope

    private var _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())

    private val activeTransport = MutableStateFlow<RadioTransport?>(null)
    private var radioTransport: RadioTransport?
        get() = activeTransport.value
        set(value) {
            activeTransport.value = value
        }

    override fun requestNodeDbRefresh(session: RadioSessionContext): Boolean {
        var queued = false
        runIfSessionActive(session) { queued = radioTransport?.requestNodeDbRefresh() == true }
        return queued
    }

    private var runningTransportId: InterfaceId? = null
    private var isStarted = false

    /**
     * True while an explicit connection lifecycle is active (set by [connect]/[setDeviceAddress], cleared by
     * [disconnect]). The hardware ([bluetoothRepository.state]) and network ([networkRepository.networkAvailable])
     * listeners consult this to avoid starting a transport the user has torn down — without it, BT/network recovery
     * emissions can wake a transport after explicit disconnect, leaving the app "connected" with no orchestrator
     * collector and an unloaded NodeDB/channels.
     *
     * Guarded by [transportMutex]; every read/write site holds the lock. The @Volatile keeps diagnostic reads honest.
     */
    @Volatile private var connectionRequested = false

    // A link that gave the radio's one client slot away, or that the radio refused, stays down until the user connects
    // again: a recovery restart would take the slot back from the client that now holds it.
    @Volatile private var awaitingUserConnect = false

    /**
     * Consecutive PhoneAPI sessions whose handshake timed out or whose receive overflowed. Guarded by [transportMutex].
     */
    private var recoveryRestarts = 0

    /**
     * Backoff for consecutive PhoneAPI connection failures; null starts from the link's first retry delay. Guarded by
     * [transportMutex].
     */
    private var phoneApiRetryDelay: Long? = null

    /**
     * The reason last shown for a retried PhoneAPI failure, so a retry that fails the same way stays quiet. Guarded by
     * [transportMutex].
     */
    private var retryReason: TransportDisconnectReason? = null

    private val gattCacheInvalidationRequested = atomic(false)

    /** Prevents concurrent transport restarts from stacking. */
    private val isRestarting = atomic(false)

    private val listenersInitialized = atomic(false)

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
        private const val PHONE_API_INITIAL_RETRY_MS = 1_000L

        /**
         * Restarting the handshake crashes some firmware, and a burst that overflows every session will overflow the
         * next, so a radio that keeps failing either way is left to the user.
         */
        private const val MAX_RECOVERY_RESTARTS = 3

        /** Firmware frees its GATT session only seconds after a link drops, so a BLE retry waits for that first. */
        private const val BLE_INITIAL_RETRY_MS = 3_000L
        private const val PHONE_API_MAX_RETRY_MS = 30_000L
    }

    private val initLock = Mutex()
    private val transportMutex = Mutex()

    init {
        // Address sync runs INDEPENDENTLY of connect() so that observers (notably
        // MeshServiceOrchestrator) see a valid currentDeviceAddressFlow BEFORE the transport
        // starts. Previously this mirror lived inside initStateListeners()'s devAddr listener and
        // was coupled to startTransportLocked() — but initStateListeners() is only invoked from
        // connect(), so the flow never updated until connect() ran. That created a cold-start race:
        // the orchestrator's currentDeviceAddressFlow observer could fire AFTER the transport had
        // already started, violating the invariant that the active DB must be switched to the
        // selected device's DB before its transport starts.
        //
        // This listener ONLY mirrors radioPrefs.devAddr into _currentDeviceAddressFlow; it never
        // starts a transport. Transport start remains driven exclusively by connect() (initial),
        // setDeviceAddress() (explicit user switch), BLE/network state changes (environment
        // recovery), and liveness restarts (zombie recovery) — see startTransportLocked() callers.
        // It stops at the first setDeviceAddress(): prefs commit asynchronously, so a later emission can carry an
        // older selection than the one already published.
        radioPrefs.devAddr
            .onEach { addr ->
                synchronized(selectionLock) { if (!selectionPublished) _currentDeviceAddressFlow.value = addr }
            }
            .catch { Logger.e(it) { "radioPrefs.devAddr address-sync flow crashed" } }
            .launchIn(processLifecycle.coroutineScope)
    }

    private fun initStateListeners() {
        if (listenersInitialized.value) return
        processLifecycle.coroutineScope.launch {
            initLock.withLock {
                if (listenersInitialized.value) return@withLock
                listenersInitialized.value = true

                bluetoothRepository.state
                    .onEach { state ->
                        transportMutex.withLock {
                            if (state.enabled) {
                                // Environmental recovery only: don't wake a transport the user has
                                // explicitly disconnected from. stopTransportLocked() below still fires on
                                // BLE-disabled to tear down a running BLE link, but we deliberately do NOT
                                // clear connectionRequested here — that is disconnect()'s job.
                                if (
                                    connectionRequested &&
                                    !awaitingUserConnect &&
                                    !radioOperationLock.suppressesTransport
                                ) {
                                    startTransportLocked()
                                }
                            } else if (runningTransportId == InterfaceId.BLUETOOTH) {
                                stopTransportLocked()
                            }
                        }
                    }
                    .catch { Logger.e(it) { "bluetoothRepository.state flow crashed" } }
                    .launchIn(processLifecycle.coroutineScope)

                networkRepository.networkAvailable
                    .onEach { state ->
                        transportMutex.withLock {
                            if (state) {
                                // Environmental recovery only — see the BLE listener above for rationale.
                                if (
                                    connectionRequested &&
                                    !awaitingUserConnect &&
                                    !radioOperationLock.suppressesTransport
                                ) {
                                    startTransportLocked()
                                }
                            } else if (runningTransportId?.isNetwork == true) {
                                stopTransportLocked()
                            }
                        }
                    }
                    .catch { Logger.e(it) { "networkRepository.networkAvailable flow crashed" } }
                    .launchIn(processLifecycle.coroutineScope)

                observeUsbRecoveryTriggers()
            }
        }
    }

    /**
     * Observes selected-SERIAL-device presence and transport state to auto-restart USB serial after replug.
     *
     * Recovery arms only after the selected serial key is observed absent, then fires when that same key is present
     * while the transport is in [ConnectionState.DeviceSleep]. This prevents normal unplug races from restarting
     * against stale presence before UsbRepository removes the key.
     *
     * Including [_connectionState] closes the race where presence returns before the I/O-death callback flips state to
     * [ConnectionState.DeviceSleep]. Each physical replug consumes one armed edge, and recovery remains gated by
     * [connectionRequested] and [runningTransportId] so explicit disconnects cannot resurrect a transport.
     */
    private fun observeUsbRecoveryTriggers() {
        val selectedPresence =
            combine(currentDeviceAddressFlow, serialDevicePresence.deviceKeys, ::selectedSerialPresence)
                .distinctUntilChanged()

        combine(selectedPresence, _connectionState, ::UsbRecoverySnapshot)
            .runningFold(UsbRecoveryTriggerState()) { triggerState, snapshot -> triggerState.next(snapshot) }
            .distinctUntilChanged()
            .drop(1)
            .filter { it.triggerRecovery }
            .onEach {
                transportMutex.withLock {
                    if (!connectionRequested) return@withLock
                    // A USB firmware-maintenance sequence owns the port: the device is enumerating as bare erase or
                    // bootloader firmware, and binding a mesh transport to it would claim the port the flow needs.
                    if (radioOperationLock.suppressesTransport) return@withLock
                    if (radioTransport != null && runningTransportId != InterfaceId.SERIAL) return@withLock
                    // Race-defense: the combine snapshot may be stale by the time we acquire
                    // transportMutex — another path (setDeviceAddress, BLE liveness restart) may
                    // have just brought this transport up to Connected/Connecting. The combine-level
                    // state filter narrows the trigger; this check guards the emission → mutex
                    // acquisition window so we never tear down a fresh healthy transport.
                    val state = _connectionState.value
                    if (state is ConnectionState.Connected || state is ConnectionState.Connecting) {
                        return@withLock
                    }
                    // Re-check presence under the lock — the combine snapshot may be stale
                    // if the device was unplugged or the selection changed while awaiting
                    // the mutex. Mirrors the race-defense pattern of the state check above.
                    val currentKeys = serialDevicePresence.deviceKeys.value
                    if (!selectedSerialPresence(_currentDeviceAddressFlow.value, currentKeys).present) {
                        return@withLock
                    }
                    // A previously-started SERIAL transport that died on unplug is still held in
                    // radioTransport (the I/O-death path emits DeviceSleep but does not null it). Tear it
                    // down silently so startTransportLocked() can build a fresh one for the replugged
                    // device. Mirrors the BLE liveness recovery shape: notifyPermanent=false (no
                    // user-facing Disconnected).
                    // Keep teardown and bring-up isolated: a transient USB close error must not skip
                    // the fresh start, and neither error should terminate this long-lived recovery flow.
                    ignoreExceptionSuspend {
                        stopTransportLocked(notifyPermanent = false)
                    }
                    ignoreExceptionSuspend { startTransportLocked() }
                }
            }
            .catch { Logger.e(it) { "serialDevicePresence recovery flow crashed" } }
            .launchIn(processLifecycle.coroutineScope)
    }

    override fun connect() {
        processLifecycle.coroutineScope.launch {
            transportMutex.withLock {
                // Mark the connection lifecycle as active BEFORE starting so concurrent
                // hardware/network listeners observe the gate as open.
                connectionRequested = true
                // A link that stopped for good still holds its transport, which would make the start below a no-op.
                if (awaitingUserConnect) {
                    awaitingUserConnect = false
                    ignoreExceptionSuspend {
                        stopTransportLocked(notifyPermanent = false)
                    }
                }
                phoneApiRetryDelay = null
                retryReason = null
                recoveryRestarts = 0
                // connect() is fire-and-forget, so a recoverable factory failure has no caller to receive it. The
                // start path already rolls back partial session state; contain the failure here so it does not become
                // an uncaught lifecycle-scope exception, while CancellationException and fatal Errors still propagate.
                ignoreExceptionSuspend { startTransportLocked() }
            }
        }
        initStateListeners()
    }

    override suspend fun disconnect() {
        transportMutex.withLock {
            // Tear the gate down BEFORE stopTransportLocked() so a concurrent state-listener
            // emission arriving while we wait for the mutex cannot re-start the transport.
            connectionRequested = false
            phoneApiRetryDelay = null
            retryReason = null
            recoveryRestarts = 0
            gattCacheInvalidationRequested.value = false
            ignoreExceptionSuspend { stopTransportLocked() }
        }
    }

    override suspend fun restartTransport(expectedSession: RadioSessionContext?) {
        // Claim the restart before waiting for the mutex so overlapping requests cannot queue another cycle.
        if (!isRestarting.compareAndSet(expect = false, update = true)) {
            Logger.d { "restartTransport: skipped, concurrent restart in progress" }
            return
        }
        try {
            transportMutex.withLock {
                if (expectedSession != null && !isSessionActive(expectedSession)) return@withLock
                if (!connectionRequested) {
                    Logger.d { "restartTransport: skipped-not-requested" }
                    return@withLock
                }
                if (getBondedDeviceAddress() == null) {
                    Logger.d { "restartTransport: skipped-no-address" }
                    return@withLock
                }
                // Honor the documented "safe no-op when no transport running" contract: environmental
                // stops (network unavailable, BLE disabled) intentionally preserve
                // connectionRequested=true so the recovery listeners above can re-bring-up the
                // transport later. A stale restart job running after such a stop must NOT bypass that
                // recovery path by creating a transport directly via startTransportLocked().
                if (radioTransport == null) {
                    Logger.d { "restartTransport: skipped-no-transport" }
                    return@withLock
                }
                Logger.w { "restartTransport: restarting transport for ${getDeviceAddress()?.anonymize}" }
                // Cycle the selected link without clearing the request to stay connected.
                onDisconnect(isPermanent = false)
                // notifyPermanent=false below (no user-facing Disconnected modal — the app-level
                // state machine drives that separately).
                ignoreExceptionSuspend { stopTransportLocked(notifyPermanent = false) }
                // Defense-in-depth mirroring the liveness recovery gate; today all
                // connectionRequested mutators (connect/disconnect/setDeviceAddress) hold
                // transportMutex so this re-check is unreachable, but it guards against future
                // refactors that mutate the gate without serialization.
                if (!connectionRequested) {
                    Logger.d { "restartTransport: aborted, disconnect requested during stop" }
                    return@withLock
                }
                // startTransportLocked() re-validates the selected address (no-op if null) and emits
                // Connected through the transport callbacks (via the new transport's onConnect) once
                // the fresh transport comes up — there is no Connecting emission at the transport
                // layer (that is an app-level state owned by MeshConnectionManager).
                startTransportLocked()
                Logger.i { "restartTransport: completed" }
            }
        } finally {
            isRestarting.value = false
        }
    }

    private fun retirePhoneApiSession(
        session: RadioTransportSession,
        recoverable: Boolean,
        errorMessage: String?,
        handshakeTimedOut: Boolean,
        reason: TransportDisconnectReason?,
        receiveOverflowed: Boolean,
    ) {
        val interfaceId = DeviceAddress.parse(session.address)?.interfaceId
        // The SDK worker can report its own end. Run teardown independently so close() can join that worker.
        processLifecycle.coroutineScope.launch {
            val retryDelay = transportMutex.withLock {
                if (!isSessionActive(session.context)) return@launch
                val linkWasUp = synchronized(sessionCallbackLock) { linkedGeneration == session.generation }
                // A session that reached Ready and ended without overflowing was stable.
                if (linkWasUp && !receiveOverflowed) {
                    phoneApiRetryDelay = null
                    retryReason = null
                    recoveryRestarts = 0
                }
                if (handshakeTimedOut || receiveOverflowed) recoveryRestarts++
                val recoveryExhausted = recoveryRestarts > MAX_RECOVERY_RESTARTS
                if (recoveryExhausted) {
                    Logger.w { "PhoneAPI recovery exhausted after $MAX_RECOVERY_RESTARTS restarts" }
                    recoveryRestarts = 0
                }
                if (!recoverable || recoveryExhausted) connectionRequested = false
                val shownReason =
                    when {
                        recoveryExhausted -> TransportDisconnectReason.HandshakeRecoveryExhausted
                        recoverable && reason == retryReason -> null
                        else -> reason
                    }
                if (recoverable && reason != null) retryReason = reason

                ignoreExceptionSuspend { stopTransportLocked(notifyPermanent = false) }
                onDisconnect(
                    isPermanent =
                    !recoverable ||
                        recoveryExhausted ||
                        (interfaceId != InterfaceId.SERIAL && interfaceId != InterfaceId.BLUETOOTH),
                    errorMessage = errorMessage.takeUnless { recoveryExhausted },
                    reason = shownReason,
                )
                if (!connectionRequested) return@launch
                nextPhoneApiRetryDelay(interfaceId)
            }
            retryPhoneApiSession(session, interfaceId, retryDelay)
        }
    }

    /** The link's current PhoneAPI retry delay, doubling the next one. Guarded by [transportMutex]. */
    private fun nextPhoneApiRetryDelay(interfaceId: InterfaceId?): Long {
        val initialRetry =
            if (interfaceId == InterfaceId.BLUETOOTH) BLE_INITIAL_RETRY_MS else PHONE_API_INITIAL_RETRY_MS
        return (phoneApiRetryDelay ?: initialRetry).also {
            phoneApiRetryDelay = (it * 2).coerceAtMost(PHONE_API_MAX_RETRY_MS)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun retryPhoneApiSession(session: RadioTransportSession, interfaceId: InterfaceId?, delayMs: Long) {
        var wait = delayMs
        while (true) {
            delay(wait)
            // Network and USB presence observers can restart a link once its environment is ready.
            if (interfaceId?.isNetwork == true && !networkRepository.networkAvailable.first()) return
            wait = transportMutex.withLock {
                val stillOwed =
                    connectionRequested &&
                        radioTransport == null &&
                        sessionGenerationCounter.value == session.generation &&
                        getDeviceAddress() == session.address
                if (!stillOwed) return
                if (interfaceId == InterfaceId.BLUETOOTH && !bluetoothRepository.state.value.enabled) return
                if (radioOperationLock.suppressesTransport) return
                if (interfaceId == InterfaceId.SERIAL) {
                    val presence = selectedSerialPresence(session.address, serialDevicePresence.deviceKeys.value)
                    when (presence.key?.let(serialDevicePresence::portExists)) {
                        // A rebooting radio re-enumerates, and no presence observer reports its port coming back.
                        false -> return@withLock nextPhoneApiRetryDelay(interfaceId)

                        true -> Unit

                        // Legacy selections are bus paths. Let the USB factory try its single-device
                        // resolution for those addresses even if the presence keys use VID/PID.
                        null -> if (!presence.present && presence.key?.startsWith("/dev/") != true) return
                    }
                }
                Logger.i { "Retrying PhoneAPI transport after session ${session.generation}" }
                ignoreExceptionSuspend { startTransportLocked() }
                return
            }
        }
    }

    override fun requestGattCacheInvalidationOnNextConnect() {
        gattCacheInvalidationRequested.value = true
        Logger.d { "GATT cache invalidation requested for next BLE connect" }
    }

    override fun consumeGattCacheInvalidationRequest(): Boolean = gattCacheInvalidationRequested.getAndSet(false)

    override val mockTransportEnabled: StateFlow<Boolean>
        get() = transportFactory.mockTransportEnabled

    override val isReplayTransportAvailable: Boolean
        get() = transportFactory.isReplayTransportAvailable

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String =
        transportFactory.toInterfaceAddress(interfaceId, rest)

    override fun getDeviceAddress(): String? = _currentDeviceAddressFlow.value

    private fun getBondedDeviceAddress(): String? {
        val address = getDeviceAddress()
        return if (transportFactory.isAddressValid(address)) {
            address
        } else {
            null
        }
    }

    override fun setDeviceAddress(deviceAddr: String?): Boolean {
        val sanitized = if (deviceAddr == "n" || deviceAddr.isNullOrBlank()) null else deviceAddr

        if (getBondedDeviceAddress() == sanitized && isStarted && _connectionState.value == ConnectionState.Connected) {
            Logger.w { "Ignoring setBondedDevice ${sanitized?.anonymize}, already using that device" }
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
            transportMutex.withLock {
                // The sanitized address is the single source of truth for the connectionRequested
                // gate: a real address arms the lifecycle (connect() equivalent) so environmental
                // listeners cannot race the rebind into a "down" state; null/("n") is a deselect
                // that MUST clear the gate so subsequent BT/network recovery cannot resurrect a
                // transport for a device the user explicitly tore down. Only start a fresh
                // transport when an address was actually selected.
                connectionRequested = sanitized != null
                awaitingUserConnect = false
                phoneApiRetryDelay = null
                retryReason = null
                recoveryRestarts = 0
                if (sanitized != null && previousAddress != null && sanitized != previousAddress) {
                    gattCacheInvalidationRequested.value = false
                }
                ignoreExceptionSuspend { stopTransportLocked() }
                if (sanitized != null) {
                    // setDeviceAddress() is fire-and-forget. startTransportLocked() has already rolled back any
                    // partially admitted session, so contain a recoverable factory failure instead of crashing the
                    // process-lifecycle scope. Explicit suspend restart callers still receive their failures.
                    ignoreExceptionSuspend { startTransportLocked() }
                }
            }
        }
        return true
    }

    /** Must be called under [transportMutex]. */
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private suspend fun startTransportLocked() {
        if (radioTransport != null) return

        // Never autoconnect to the simulated node. The mock transport may be offered in the
        // device-picker UI on debug builds, but it must only connect when the user explicitly
        // selects it (i.e. its address is stored in radioPrefs).
        val address = getBondedDeviceAddress()

        if (address == null) {
            Logger.d { "No valid address to connect to" }
            return
        }

        // Build a fresh per-instance session and admit it BEFORE constructing the transport so the wrapper captures
        // the new session. Bumping the public generation also signals downstream consumers (e.g.
        // RadioControllerImpl)
        // to invalidate session-scoped state from any previous transport instance.
        val generation = sessionGenerationCounter.incrementAndGet()
        val session = RadioTransportSession(generation = generation, address = address)
        synchronized(sessionCallbackLock) {
            check(activeTransportSession == null && sessionOperationStates.isEmpty()) {
                "Cannot admit a transport while the previous session is still draining"
            }
            sessionOperationStates[generation] = SessionOperationState()
            activeTransportSession = session
            sessionAdmissionOpen = true
            _activeSession.value = session.context
            _sessionGeneration.value = generation
        }
        val sessionBoundService = SessionBoundRadioInterfaceService(session)
        val connectionStateBeforeStart = _connectionState.value

        Logger.i { "Starting radio transport for ${address.anonymize} (generation=$generation)" }
        val newTransport =
            try {
                transportFactory.createTransport(address, sessionBoundService)
            } catch (failure: Throwable) {
                // A failed factory call consumed a generation that may already have reached observers. Keep the
                // generation monotonic, but revoke the admitted session and every transport-lifecycle field before
                // rethrowing the original failure. A later retry will receive a strictly newer generation.
                revokeTransportSession(session)
                radioTransport = null
                runningTransportId = null
                isStarted = false
                _connectionState.value = connectionStateBeforeStart
                throw failure
            }
        val published =
            synchronized(sessionCallbackLock) {
                if (activeTransportSession !== session || !sessionAdmissionOpen) {
                    // A replaced or cleared token has no revoker left to reclaim this generation's operation state.
                    // A closed gate with a matching token means revokeTransportSession is mid-flight; it owns removal
                    // inside its NonCancellable block, so do not remove the entry here.
                    if (activeTransportSession !== session) sessionOperationStates.remove(generation)
                    false
                } else {
                    radioTransport = newTransport
                    true
                }
            }
        if (!published) {
            // Revocation already closed this session's admission and owns the canonical lifecycle rollback. Do not
            // restore connectionStateBeforeStart here; that would overwrite the newer teardown state.
            val publicationFailure =
                IllegalStateException("Transport session was revoked before its transport could be published")
            try {
                withContext(NonCancellable) { newTransport.close() }
            } catch (closeFailure: Exception) {
                if (closeFailure is CancellationException) currentCoroutineContext().ensureActive()
                publicationFailure.addSuppressed(closeFailure)
            }
            throw publicationFailure
        }
        try {
            newTransport.start()
        } catch (failure: Throwable) {
            try {
                stopTransportLocked(notifyPermanent = false)
            } catch (closeFailure: Exception) {
                if (closeFailure is CancellationException) currentCoroutineContext().ensureActive()
                failure.addSuppressed(closeFailure)
            }
            _connectionState.value = connectionStateBeforeStart
            throw failure
        }
        runningTransportId = DeviceAddress.parse(address)?.interfaceId
        isStarted = true
    }

    /** Closes the SDK session after its app leases drain. Must be called under [transportMutex]. */
    private suspend fun stopTransportLocked(notifyPermanent: Boolean = true) = withContext(NonCancellable) {
        val currentTransport = radioTransport
        val currentSession = synchronized(sessionCallbackLock) { activeTransportSession }
        revokeTransportSession(currentSession)
        Logger.i { "Stopping transport $currentTransport" }
        isStarted = false
        radioTransport = null
        runningTransportId = null
        _transportStatus.value = null
        try {
            currentTransport?.close()
        } finally {
            _serviceScope.cancel("stopping transport")
            _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())
            if (notifyPermanent && currentTransport != null) onDisconnect(isPermanent = true)
        }
    }

    override fun adminForSession(session: RadioSessionContext): AdminApi? {
        var admin: AdminApi? = null
        runIfSessionActive(session) { admin = radioTransport?.admin }
        return admin
    }

    override fun clientForSession(session: RadioSessionContext): RadioClient? {
        var client: RadioClient? = null
        runIfSessionActive(session) { client = radioTransport?.client }
        return client
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun adminSessionStatus(node: NodeId): Flow<AdminSessionStatus> = activeTransport.flatMapLatest {
        it?.adminSessionStatus(node) ?: flowOf(AdminSessionStatus.NoSession)
    }

    override fun sendPaced(session: RadioSessionContext, packet: MeshPacket): PacedMessageHandle? {
        val admission = admitTransportSend(expected = session) as? TransportSendAdmission.Admitted ?: return null
        return try {
            admission.transport.sendPaced(packet)?.also { handle ->
                serviceScope.launch {
                    if (handle.awaitDispatch()) runIfSessionActive(session) { _meshActivity.tryEmit(MeshActivity.Send) }
                }
            }
        } finally {
            releaseSessionOperation(admission.session)
        }
    }

    override fun tryLockdown(session: RadioSessionContext, auth: LockdownAuth): Boolean =
        when (val admission = admitTransportSend(expected = session)) {
            is TransportSendAdmission.Admitted -> sendThroughAdmittedTransport(admission) { it.tryLockdown(auth) }
            else -> false
        }

    override fun trySendToRadio(bytes: ByteArray): Boolean =
        // Admission and teardown share one session gate. Once accepted, teardown drains the synchronous handoff before
        // revoking the session; transport implementations still own their asynchronous delivery outcome.
        when (val admission = admitTransportSend()) {
            is TransportSendAdmission.Admitted ->
                sendThroughAdmittedTransport(admission) { it.handleSendToRadio(bytes) }

            TransportSendAdmission.AdmissionClosed,
            TransportSendAdmission.NoActiveSession,
            -> {
                Logger.d { "trySendToRadio: no admitted transport session, dropping ${bytes.size} bytes" }
                false
            }

            TransportSendAdmission.NoTransport -> {
                Logger.w { "trySendToRadio: admitted session has no radio transport, dropping ${bytes.size} bytes" }
                false
            }
        }

    private inline fun sendThroughAdmittedTransport(
        admission: TransportSendAdmission.Admitted,
        send: (RadioTransport) -> Boolean,
    ): Boolean = try {
        val sent = safeCatching {
            send(admission.transport)
        }
            .onFailure { Logger.w(it) { "trySendToRadio: active transport send failed" } }
            .getOrDefault(false)
        if (sent) {
            safeCatching { _meshActivity.tryEmit(MeshActivity.Send) }
                .onFailure { Logger.w(it) { "trySendToRadio: failed to publish mesh activity" } }
        }
        sent
    } finally {
        releaseSessionOperation(admission.session)
    }

    override suspend fun deliverMqttProxy(message: MqttClientProxyMessage): Boolean =
        when (val admission = admitTransportSend()) {
            is TransportSendAdmission.Admitted ->
                sendThroughAdmittedTransport(admission) { it.deliverMqttProxy(message) }

            else -> false
        }

    override fun reportReceiveActivity() {
        _meshActivity.tryEmit(MeshActivity.Receive)
    }

    override fun onConnect() {
        synchronized(sessionCallbackLock) { publishConnected() }
    }

    /** Applies a callback already admitted under [sessionCallbackLock]. */
    private fun publishConnected() {
        // MutableStateFlow.value is thread-safe (backed by atomics) — assign directly rather than
        // launching a coroutine. The async launch pattern introduced a window where a concurrent
        // onDisconnect launch could execute AFTER an onConnect launch, leaving the service stuck
        // in Connected while the transport was actually disconnected.
        linkedGeneration = activeTransportSession?.generation
        if (_connectionState.value != ConnectionState.Connected) {
            Logger.d { "Broadcasting connection state change to Connected" }
            _connectionState.value = ConnectionState.Connected
        }
    }

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?, reason: TransportDisconnectReason?) {
        synchronized(sessionCallbackLock) { publishDisconnected(isPermanent, errorMessage, reason) }
    }

    /**
     * Handshake-level phases arrive here directly. They apply only while the admitted session's own link is up, so a
     * phase a superseded session's handshake reports late never lands on its replacement.
     */
    override fun onStatus(status: TransportStatus) {
        synchronized(sessionCallbackLock) {
            val session = activeTransportSession
            if (session != null && session.generation == linkedGeneration) _transportStatus.value = status
        }
    }

    /** Applies a callback already admitted under [sessionCallbackLock]. */
    private fun publishDisconnected(isPermanent: Boolean, errorMessage: String?, reason: TransportDisconnectReason?) {
        if (errorMessage != null || reason != null) {
            processLifecycle.coroutineScope.launch(dispatchers.default) {
                val message = errorMessage ?: reason?.let { connectionErrorText(it) }
                if (message != null) _connectionError.emit(message)
            }
        }
        if (isPermanent && reason in USER_CONNECT_REASONS) awaitingUserConnect = true
        linkedGeneration = null
        // A handshake phase outlives its link otherwise: transports without phases of their own never replace it.
        if (_transportStatus.value?.phase in LINK_UP_PHASES) _transportStatus.value = null
        val newTargetState = if (isPermanent) ConnectionState.Disconnected else ConnectionState.DeviceSleep
        if (_connectionState.value != newTargetState) {
            Logger.d { "Broadcasting connection state change to $newTargetState" }
            _connectionState.value = newTargetState
        }
    }

    /**
     * Per-transport-session wrapper around this service. Delegates every [RadioInterfaceService] surface (scope,
     * address, sendToRadio, etc.) to the enclosing service; [RadioTransportCallback] entry points are gated on the
     * captured [session] still being the [activeTransportSession]. Admission and the synchronous callback side effect
     * share [sessionCallbackLock] with teardown, eliminating a check-then-use window. Late callbacks from a torn-down
     * transport are dropped BEFORE bytes enter the shared received-data channel or connection/config state mutates.
     * Address is logged with [anonymize] and the session generation only — never the raw address bytes.
     */
    private inner class SessionBoundRadioInterfaceService(val session: RadioTransportSession) :
        RadioInterfaceService by this@SharedRadioInterfaceService {
        override fun consumeGattCacheInvalidationRequest(): Boolean {
            var requested = false
            runIfTransportSessionActive(session) {
                requested = this@SharedRadioInterfaceService.consumeGattCacheInvalidationRequest()
            }
            return requested
        }

        override fun onConnect() {
            val admitted = runIfTransportSessionActive(session, ::publishConnected)
            if (!admitted) {
                Logger.d { "Dropping stale onConnect gen=${session.generation} addr=${session.address.anonymize}" }
            }
        }

        override fun onDisconnect(isPermanent: Boolean, errorMessage: String?, reason: TransportDisconnectReason?) {
            val admitted =
                runIfTransportSessionActive(session) { publishDisconnected(isPermanent, errorMessage, reason) }
            if (!admitted) {
                Logger.d { "Dropping stale onDisconnect gen=${session.generation} addr=${session.address.anonymize}" }
            }
        }

        override fun onPhoneApiSessionEnded(
            recoverable: Boolean,
            errorMessage: String?,
            handshakeTimedOut: Boolean,
            reason: TransportDisconnectReason?,
            receiveOverflowed: Boolean,
        ) {
            retirePhoneApiSession(session, recoverable, errorMessage, handshakeTimedOut, reason, receiveOverflowed)
        }

        override fun onStatus(status: TransportStatus) {
            val admitted = runIfTransportSessionActive(session) { _transportStatus.value = status }
            if (!admitted) {
                Logger.d { "Dropping stale onStatus gen=${session.generation} addr=${session.address.anonymize}" }
            }
        }
    }
}
