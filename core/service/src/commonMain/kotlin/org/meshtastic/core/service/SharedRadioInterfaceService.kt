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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import org.meshtastic.core.repository.ServiceStateWriter
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.error_recovery_exhausted
import org.meshtastic.core.resources.firmware_too_old
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.serial_permission_denied
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
import org.meshtastic.sdk.AdminSessionStatus
import org.meshtastic.sdk.ConfigPhase
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
data class ActiveRadioSession(val generation: Long, val address: String) {
    /** Immutable context carried with every frame admitted by this session. */
    val context = RadioSessionContext(generation = generation, address = address)

    /** Prevent accidental disclosure of the raw transport address in diagnostic interpolation. */
    override fun toString(): String = "ActiveRadioSession(generation=$generation, address=...)"
}

/**
 * The admitted app session and the scope its work runs in. Cancelling the scope closes admission; joining it drains.
 */
private class OpenSession(val active: ActiveRadioSession, val scope: CoroutineScope)

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
    TransportDisconnectReason.UsbPermissionDenied -> Res.string.usb_permission_denied
    TransportDisconnectReason.SerialPermissionDenied -> Res.string.serial_permission_denied
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
 * [ConnectionState] to [ServiceStateWriter]. [ConnectionState.Connected] shows only once the app holds the session's
 * config and nodes.
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
     * The session that owns lifecycle completion. [revokeSession] cancels its scope first (admission closes), joins the
     * work already admitted, then clears this token.
     */
    @Volatile private var activeRadioSession: OpenSession? = null

    private val activeLink = MutableStateFlow<ActiveLink?>(null)

    /** Shown when no link is open: Disconnected, or Failed for a selected radio whose link could not open. */
    private val idleState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    /** The generation whose handshake snapshot the app has installed. */
    private val installedGeneration = MutableStateFlow<Long?>(null)

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
        /** How often [revokeSession] reports a session drain that has not completed. */
        private const val DRAIN_WAIT_LOG_INTERVAL_MILLIS = 15 * 1000L

        /** The handshake stage a link failed at when the app could not install its snapshot. */
        private const val SNAPSHOT_INSTALL_STAGE = "snapshot install"

        private val APP_CONFIGURING = ConnectionState.Configuring(ConfigPhase.Stage2, 1f)
    }

    init {
        publishConnectionState()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun publishConnectionState() {
        combine(
            activeLink.flatMapLatest { it?.link?.connection ?: idleState },
            installedGeneration,
            _activeSession,
        ) { sdk, installed, session ->
            // The SDK's Connected is the app's only once the app holds this session's snapshot.
            if (sdk is ConnectionState.Connected && installed != session?.generation) APP_CONFIGURING else sdk
        }
            .distinctUntilChanged()
            .onEach { serviceStateWriter.setConnectionState(it) }
            .catch { Logger.e(it) { "Connection state publication failed" } }
            .launchIn(processLifecycle.coroutineScope)
    }

    // ── Session admission ───────────────────────────────────────────────────

    private fun openFor(session: RadioSessionContext): OpenSession? = activeRadioSession?.takeIf {
        it.active.context == session && it.scope.isActive
    }

    override fun isSessionActive(session: RadioSessionContext): Boolean = openFor(session) != null

    override fun sessionScope(session: RadioSessionContext): CoroutineScope? = openFor(session)?.scope

    /** Admits a new session for [active], or null once [active] is no longer the selected radio's open link. */
    private fun admitSession(active: ActiveLink): RadioSessionContext? {
        if (activeLink.value !== active || active.closing) return null
        check(activeRadioSession == null) { "Cannot admit a session while the previous one is still draining" }
        val session = ActiveRadioSession(sessionGenerationCounter.incrementAndGet(), active.address)
        val scope = CoroutineScope(dispatchers.default + SupervisorJob(active.scope.coroutineContext.job))
        activeRadioSession = OpenSession(session, scope)
        _activeSession.value = session.context
        _sessionGeneration.value = session.generation
        Logger.i { "Admitted session ${session.generation} for ${active.address.anonymize}" }
        return session.context
    }

    /**
     * Closes admission for [open] by cancelling its scope, joins the operations already admitted, then publishes
     * lifecycle completion. The retained session token prevents a replacement session from overlapping an admitted
     * database commit.
     */
    private suspend fun revokeSession(open: OpenSession?) {
        if (open == null) return
        withContext(NonCancellable) {
            val job = open.scope.coroutineContext.job
            job.cancel()
            // The drain must finish before a replacement session is admitted, so keep waiting, but never silently: a
            // block stuck past the handler timeout means a handler ignored cancellation.
            var waitedMillis = 0L
            while (withTimeoutOrNull(DRAIN_WAIT_LOG_INTERVAL_MILLIS) { job.join() } == null) {
                waitedMillis += DRAIN_WAIT_LOG_INTERVAL_MILLIS
                Logger.e {
                    "Session teardown blocked ${waitedMillis}ms waiting for ${job.children.count()} admitted session " +
                        "operation(s) to release (generation=${open.active.generation})"
                }
            }
            if (activeRadioSession === open) {
                activeRadioSession = null
                _activeSession.value = null
            }
        }
    }

    private fun sessionFor(context: RadioSessionContext): OpenSession? = activeRadioSession?.takeIf {
        it.active.context == context
    }

    /** The host one link's pipeline admits its sessions through; it stops admitting once that link closes. */
    private inner class LinkHost(private val active: ActiveLink) :
        RadioSessionHost,
        RadioInterfaceService by this@SharedRadioInterfaceService {
        override fun openSession(): RadioSessionContext? = admitSession(active)

        override suspend fun closeSession(session: RadioSessionContext) {
            revokeSession(sessionFor(session))
        }

        override fun onSnapshotInstalled(session: RadioSessionContext) {
            installedGeneration.value = session.generation
        }

        // The pipeline cannot close the link it runs on, so the close runs beside it.
        override fun onSnapshotInstallFailed(session: RadioSessionContext) {
            processLifecycle.coroutineScope.launch {
                linkMutex.withLock {
                    if (activeLink.value !== active) return@withLock
                    ignoreExceptionSuspend { closeLinkLocked() }
                    idleState.value = ConnectionState.Failed(MeshtasticException.HandshakeTimeout(SNAPSHOT_INSTALL_STAGE))
                    connectionErrorReasons.trySend(TransportDisconnectReason.HandshakeRecoveryExhausted)
                }
            }
        }
    }

    // ── The selected radio's link ───────────────────────────────────────────

    private fun onLinkState(active: ActiveLink, state: ConnectionState) {
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
            idleState.value =
                ConnectionState.Failed(MeshtasticException.Transport("No link could be opened for the selected radio"))
            return
        }
        if (gattRefreshPending.value && link.refreshGattCacheOnNextConnect()) gattRefreshPending.value = false
        val active = ActiveLink(address, link, scope)
        activeLink.value = active
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
        val active = activeLink.value
        if (active == null) {
            // A link that never opened, or one closed after its install failed, left Failed with nothing to close.
            idleState.value = ConnectionState.Disconnected
            return@withContext
        }
        active.closing = true
        revokeSession(activeRadioSession)
        try {
            active.link.disconnect()
            active.pipeline?.cancelAndJoin()
        } finally {
            idleState.value = ConnectionState.Disconnected
            activeLink.value = null
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

    override fun clientForSession(session: RadioSessionContext): RadioClient? =
        if (isSessionActive(session)) activeLink.value?.link?.client else null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun adminSessionStatus(node: NodeId): Flow<AdminSessionStatus> = activeLink.flatMapLatest {
        it?.link?.client?.adminSessionStatus(node) ?: flowOf(AdminSessionStatus.NoSession)
    }

    override fun requestNodeDbRefresh(session: RadioSessionContext): Boolean =
        clientForSession(session)?.requestNodeDbRefresh() == true

    /** Runs [send] on [session]'s client while that session owns admission; teardown does not wait for the handoff. */
    private inline fun <T> withAdmittedClient(session: RadioSessionContext?, send: (ActiveLink) -> T?): T? {
        val open = activeRadioSession?.takeIf { it.scope.isActive && (session == null || it.active.context == session) }
        val link = activeLink.value
        if (open == null || link == null) return null
        return safeCatching { send(link) }.onFailure { Logger.w(it) { "Active session handoff failed" } }.getOrNull()
    }

    override fun sendPaced(session: RadioSessionContext, packet: MeshPacket): PacedMessageHandle? {
        val (handle, linkScope) =
            withAdmittedClient(session) { it.link.client.sendPaced(packet) to it.scope } ?: return null
        linkScope.launch {
            if (handle.awaitDispatch() && isSessionActive(session)) _meshActivity.tryEmit(MeshActivity.Send)
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
