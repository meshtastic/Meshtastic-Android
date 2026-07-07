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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.ignoreExceptionSuspend
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.proto.ToRadio
import kotlin.concurrent.Volatile

private const val USB_PERMISSION_DENIED_ERROR = "USB permission denied. Reconnect the device to try again."

private data class SelectedSerialPresence(val key: String?, val present: Boolean)

private data class UsbRecoverySnapshot(val presence: SelectedSerialPresence, val state: ConnectionState)

private data class UsbRecoveryTriggerState(
    val key: String? = null,
    val present: Boolean = false,
    val armedByAbsence: Boolean = false,
    val triggerRecovery: Boolean = false,
)

private fun selectedSerialPresence(address: String?, keys: Set<String>): SelectedSerialPresence {
    val key = address?.takeIf { it.firstOrNull() == InterfaceId.SERIAL.id }?.drop(1)?.takeIf { it.isNotEmpty() }
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

private fun TransportDisconnectReason.toConnectionErrorMessage(): String = when (this) {
    TransportDisconnectReason.UsbPermissionDenied -> USB_PERMISSION_DENIED_ERROR
}

/**
 * Shared multiplatform connection orchestrator for Meshtastic radios.
 *
 * Manages the connection lifecycle (connect, active, disconnect, reconnect loop), device address state flows, and
 * hardware state observability (BLE/Network toggles). Delegates the actual raw byte transport mapping to a
 * platform-specific [RadioTransportFactory].
 */
@Suppress("LongParameterList", "TooManyFunctions")
@Single
class SharedRadioInterfaceService(
    private val dispatchers: CoroutineDispatchers,
    private val bluetoothRepository: BluetoothRepository,
    private val networkRepository: NetworkRepository,
    private val serialDevicePresence: SerialDevicePresence,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
    private val radioPrefs: RadioPrefs,
    private val transportFactory: RadioTransportFactory,
    private val analytics: PlatformAnalytics,
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

    // Unbounded Channel preserves strict FIFO delivery of incoming radio bytes, which the
    // firmware handshake depends on (initial config packet ordering). A SharedFlow with
    // `launch { emit() }` per packet reorders under concurrent dispatch and breaks config load.
    // trySend on an UNLIMITED channel never suspends and never drops, so handleFromRadio can
    // remain a non-suspend synchronous callback.
    private val _receivedData = Channel<ByteArray>(Channel.UNLIMITED)
    override val receivedData: Flow<ByteArray> = _receivedData.receiveAsFlow()

    private val _meshActivity =
        MutableSharedFlow<MeshActivity>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val meshActivity: Flow<MeshActivity> = _meshActivity.asFlow()

    private val _connectionError = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val connectionError: Flow<String> = _connectionError.asFlow()

    override val serviceScope: CoroutineScope
        get() = _serviceScope

    private var _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())
    private var radioTransport: RadioTransport? = null
    private var runningTransportId: InterfaceId? = null
    private var isStarted = false

    /**
     * Set while [stopTransportLocked] is draining the polite disconnect frame. [sendToRadio] checks this so any late
     * traffic submitted after we've announced disconnection is dropped rather than racing in front of the firmware-side
     * link teardown.
     */
    @Volatile private var isStopping = false

    /**
     * True while an explicit connection lifecycle is active (set by [connect]/[setDeviceAddress], cleared by
     * [disconnect]). The hardware ([bluetoothRepository.state]) and network ([networkRepository.networkAvailable])
     * listeners and the [checkLiveness] zombie-recovery path consult this to avoid starting a transport the user has
     * torn down — without it, BT/network recovery emissions can wake a transport after explicit disconnect, leaving the
     * app "connected" with no orchestrator collector and an unloaded NodeDB/channels.
     *
     * Guarded by [transportMutex]; every read/write site holds the lock. The @Volatile keeps diagnostic reads honest.
     */
    @Volatile private var connectionRequested = false

    /** Prevents concurrent liveness-induced transport restarts from stacking. */
    private val isRestarting = atomic(false)

    private val listenersInitialized = atomic(false)
    private var heartbeatJob: Job? = null
    private var lastHeartbeatMillis = 0L

    @Volatile private var lastDataReceivedMillis = 0L

    /**
     * Internal test seam for deterministic clock injection. Production uses [nowMillis]; tests override this to a
     * controllable clock so [onConnect], [handleFromRadio], [checkLiveness], and [keepAlive] all share one coherent
     * time source. Not a constructor parameter to avoid breaking Koin @Single annotation generation (which would try to
     * resolve `() -> Long` from the DI graph).
     */
    @Volatile
    @Suppress("MemberVisibilityCanBePrivate")
    internal var clockMillis: () -> Long = { nowMillis }

    /** The current time from the injected clock. */
    private fun now(): Long = clockMillis()

    companion object {
        private const val HEARTBEAT_INTERVAL_MILLIS = 30 * 1000L

        // If we haven't received any data from the radio within this window after sending a
        // heartbeat while the connection is nominally "Connected", the connection is likely a
        // zombie (BLE stack didn't report disconnect). Two missed heartbeat intervals gives
        // the firmware a reasonable window to respond or send telemetry.
        private const val LIVENESS_TIMEOUT_MILLIS = HEARTBEAT_INTERVAL_MILLIS * 2

        /**
         * Upper bound on how long we wait for the polite `ToRadio(disconnect = true)` frame to flush before tearing the
         * transport down. 500ms gives BLE's write-retry path (`BleRetry` backs off 500ms) room for one attempt on a
         * flaky GATT connection. Serial and TCP typically flush well under this window.
         */
        private const val POLITE_DISCONNECT_DRAIN_MS = 500L
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
        // _currentDeviceAddressFlow is a MutableStateFlow (atomic .value), so the unconditional
        // assignment here is race-free without holding transportMutex; same-address writes are
        // idempotent no-ops.
        radioPrefs.devAddr
            .onEach { addr -> _currentDeviceAddressFlow.value = addr }
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
                                if (connectionRequested) {
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
                                if (connectionRequested) {
                                    startTransportLocked()
                                }
                            } else if (runningTransportId == InterfaceId.TCP) {
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
                    if (runningTransportId != InterfaceId.SERIAL) return@withLock
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
                    // user-facing Disconnected), sendPoliteDisconnect=false (the link is already gone).
                    // Keep teardown and bring-up isolated: a transient USB close error must not skip
                    // the fresh start, and neither error should terminate this long-lived recovery flow.
                    ignoreExceptionSuspend {
                        stopTransportLocked(notifyPermanent = false, sendPoliteDisconnect = false)
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
                startTransportLocked()
            }
        }
        initStateListeners()
    }

    override suspend fun disconnect() {
        transportMutex.withLock {
            // Tear the gate down BEFORE stopTransportLocked() so a concurrent state-listener
            // emission arriving while we wait for the mutex cannot re-start the transport.
            connectionRequested = false
            ignoreExceptionSuspend { stopTransportLocked() }
        }
    }

    override suspend fun restartTransport() {
        // CAS BEFORE the mutex, mirroring checkLiveness()'s coordination structure: both
        // restart paths CAS synchronously, one wins, one loses immediately. Performing the
        // CAS inside transportMutex.withLock races checkLiveness's outer
        // `finally { isRestarting = false }` (which runs AFTER mutex release): a queued
        // restartTransport that resumes from mutex.wait can observe isRestarting == false,
        // win the CAS, and produce an extra transport cycle (3 instead of 2) under the JVM's
        // real dispatcher. The loser here observes isRestarting == true and defers to the
        // in-flight cycle. startTransportLocked() is idempotent w.r.t. an existing transport,
        // but the CAS also prevents a double stop/stop race on the teardown side.
        if (!isRestarting.compareAndSet(expect = false, update = true)) {
            Logger.d { "restartTransport: skipped, concurrent restart in progress" }
            return
        }
        try {
            transportMutex.withLock {
                // Silent recovery for app-level handshake stalls. The transport may still be physically
                // up (TCP socket alive, firmware unresponsive to want_config_id), so cycling it in place
                // WITHOUT clearing connectionRequested avoids the split-brain where setDeviceAddress's
                // fast-path would otherwise block same-node reconnect. The caller (MeshConnectionManager)
                // is responsible for the app-level Disconnected flip; this method only cycles the
                // transport and emits the transport-level DeviceSleep -> Connected transitions via
                // callbacks (no Connecting — that is an app-level state, not a transport emission).
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
                // Mirror checkLiveness()'s coordination contract: emit a transport-level
                // Connected -> DeviceSleep transition before the stop/start cycle so
                // transport-level observers see a full DeviceSleep -> Connected cycle when
                // the fresh transport's onConnect() fires and can re-trigger their onConnected
                // logic. The caller (runSiblingHandshakeRecovery) flips the app-level state to
                // Disconnected before invoking restartTransport(), so this emission exists for
                // transport-level coordination, not for app-level StateFlow dedupe. Silent:
                // transient DeviceSleep, not a permanent Disconnected.
                onDisconnect(isPermanent = false)
                // notifyPermanent=false below (no user-facing Disconnected modal — the app-level
                // state machine drives that separately) and sendPoliteDisconnect=false (firmware
                // is unresponsive, writing a goodbye frame into a dead link only delays teardown).
                ignoreExceptionSuspend { stopTransportLocked(notifyPermanent = false, sendPoliteDisconnect = false) }
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

    override fun isMockTransport(): Boolean = transportFactory.isMockTransport()

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

        analytics.track("mesh_bond")

        Logger.d { "Setting bonded device to ${sanitized?.anonymize}" }
        radioPrefs.setDevAddr(sanitized)
        _currentDeviceAddressFlow.value = sanitized

        processLifecycle.coroutineScope.launch {
            transportMutex.withLock {
                // The sanitized address is the single source of truth for the connectionRequested
                // gate: a real address arms the lifecycle (connect() equivalent) so environmental
                // listeners cannot race the rebind into a "down" state; null/("n") is a deselect
                // that MUST clear the gate so subsequent BT/network recovery cannot resurrect a
                // transport for a device the user explicitly tore down. Only start a fresh
                // transport when an address was actually selected.
                connectionRequested = sanitized != null
                ignoreExceptionSuspend { stopTransportLocked() }
                if (sanitized != null) {
                    startTransportLocked()
                }
            }
        }
        return true
    }

    /** Must be called under [transportMutex]. */
    private fun startTransportLocked() {
        if (radioTransport != null) return

        // Never autoconnect to the simulated node. The mock transport may be offered in the
        // device-picker UI on debug builds, but it must only connect when the user explicitly
        // selects it (i.e. its address is stored in radioPrefs).
        val address = getBondedDeviceAddress()

        if (address == null) {
            Logger.d { "No valid address to connect to" }
            return
        }

        Logger.i { "Starting radio transport for ${address.anonymize}" }
        isStarted = true
        runningTransportId = address.firstOrNull()?.let { InterfaceId.forIdChar(it) }
        radioTransport = transportFactory.createTransport(address, this)
        startHeartbeat()
    }

    /**
     * Must be called under [transportMutex].
     *
     * @param notifyPermanent When `true`, emits a permanent disconnect state to [connectionState]. Set `false` during
     *   automatic liveness recovery to avoid surfacing a user-facing disconnect.
     * @param sendPoliteDisconnect When `true`, sends a `ToRadio(disconnect = true)` frame to the firmware before
     *   tearing down. Set `false` when the transport is already dead (zombie session) to avoid writing into a broken
     *   link.
     */
    private suspend fun stopTransportLocked(notifyPermanent: Boolean = true, sendPoliteDisconnect: Boolean = true) {
        val currentTransport = radioTransport
        Logger.i { "Stopping transport $currentTransport" }
        // Best-effort polite goodbye: tell the firmware we're disconnecting on purpose so it can
        // tear down its side of the link cleanly instead of relying on timeouts / hardware events.
        // Flip isStopping before sending so any concurrent sendToRadio() drops incoming traffic —
        // we don't want normal packets racing behind the disconnect frame. Skip only when already
        // Disconnected; firmware can still consume the goodbye while handshaking or sleeping, so
        // it's worth sending in every other state. The send is fire-and-forget through the
        // transport's own scope; the drain delay gives async transports a window to flush before
        // close() cancels their write scope. BLE's retry path backs off 500ms, so this window
        // also covers one retry on flaky GATT links.
        if (
            sendPoliteDisconnect && currentTransport != null && _connectionState.value != ConnectionState.Disconnected
        ) {
            isStopping = true
            ignoreExceptionSuspend {
                currentTransport.handleSendToRadio(ToRadio(disconnect = true).encode())
                delay(POLITE_DISCONNECT_DRAIN_MS)
            }
        }
        isStarted = false
        radioTransport = null
        runningTransportId = null
        isStopping = false
        currentTransport?.close()

        _serviceScope.cancel("stopping transport")
        _serviceScope = CoroutineScope(dispatchers.io + SupervisorJob())

        if (notifyPermanent && currentTransport != null) {
            onDisconnect(isPermanent = true)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        lastDataReceivedMillis = now()
        heartbeatJob =
            serviceScope.launch {
                while (true) {
                    delay(HEARTBEAT_INTERVAL_MILLIS)
                    keepAlive()
                    checkLiveness()
                }
            }
    }

    /**
     * Detects zombie connections where the BLE stack didn't report a disconnect.
     *
     * If we believe we're connected but haven't received any data from the radio within [LIVENESS_TIMEOUT_MILLIS], the
     * connection is likely dead. Signal a non-permanent disconnect so the reconnect machinery can take over.
     *
     * Uses [clockMillis] for the current time so tests can inject a deterministic clock.
     */
    internal fun checkLiveness() {
        if (_connectionState.value != ConnectionState.Connected) return

        val silenceMs = now() - lastDataReceivedMillis
        if (silenceMs > LIVENESS_TIMEOUT_MILLIS) {
            // "Silence" = lastDataReceivedMillis not updated by handleFromRadio (no inbound
            // packets). Only BLE suffers from silent zombie sessions (no disconnect signal from
            // stack), so the liveness-restart path is BLE-only. For non-BLE transports we return
            // WITHOUT emitting a disconnect or mutating ConnectionState — there is no
            // transport-level timeout contract proving that silence past this threshold means
            // the session is dead.
            if (runningTransportId != InterfaceId.BLUETOOTH) {
                Logger.d { "Ignoring liveness timeout for non-BLE transport (silence: ${silenceMs}ms)" }
                return
            }

            Logger.w {
                "Liveness check failed: no data received for ${silenceMs}ms " +
                    "(threshold: ${LIVENESS_TIMEOUT_MILLIS}ms). Restarting BLE transport."
            }

            // Force transport restart to recover from silent zombie sessions where the BLE stack
            // did not report a disconnect. Uses the same processLifecycle scope and transportMutex
            // pattern as setDeviceAddress() to guarantee clean teardown/restart sequencing.
            // The onDisconnect notification is emitted INSIDE the compareAndSet guard so that a
            // double liveness-timeout (timer not cancelled between fires) does not produce
            // duplicate disconnect notifications for a single restart cycle.
            if (isRestarting.compareAndSet(expect = false, update = true)) {
                // Silent recovery: emit the non-permanent state transition (DeviceSleep) so the
                // reconnect machinery takes over, but do NOT pass an errorMessage. Automatic
                // liveness recovery is self-healing — surfacing a modal dialog for a transient
                // condition the app already handled is confusing UX. The warning log above
                // remains the observability surface for this event.
                //
                // Ordering note (pre-existing): onDisconnect fires here, BEFORE the launched
                // restart coroutine's `connectionRequested` check below. If an explicit disconnect()
                // races this timeout, a spurious DeviceSleep emission can leak to observers. The
                // connectionRequested gate still prevents the worse outcome — transport resurrection
                // — so this is a benign UI-level transient, not a state-machine bug.
                onDisconnect(isPermanent = false)
                processLifecycle.coroutineScope.launch {
                    try {
                        transportMutex.withLock {
                            // Defense against a race between checkLiveness() firing and a
                            // concurrent disconnect(): if the user has torn the connection down
                            // since the heartbeat scheduled this restart, leave it down. The
                            // transport is already null after disconnect()'s stopTransportLocked().
                            if (!connectionRequested) {
                                Logger.d { "Skipping liveness restart: connection no longer requested" }
                                return@withLock
                            }
                            ignoreExceptionSuspend {
                                stopTransportLocked(notifyPermanent = false, sendPoliteDisconnect = false)
                            }
                            startTransportLocked()
                        }
                    } finally {
                        isRestarting.value = false
                    }
                }
            }
        }
    }

    fun keepAlive(now: Long = now()) {
        if (now - lastHeartbeatMillis > HEARTBEAT_INTERVAL_MILLIS) {
            radioTransport?.keepAlive()
            lastHeartbeatMillis = now
        }
    }

    override fun sendToRadio(bytes: ByteArray) {
        if (isStopping) {
            Logger.d { "sendToRadio: transport stopping, dropping ${bytes.size} bytes" }
            return
        }
        // Snapshot the transport to avoid calling handleSendToRadio on a null reference.
        // There is still a benign race: stopTransportLocked() may cancel _serviceScope
        // between the null-check and the launch, causing the coroutine to be silently
        // dropped. This is acceptable — if the transport is shutting down, dropping the
        // send is the correct behavior.
        val currentTransport =
            radioTransport
                ?: run {
                    Logger.w { "sendToRadio: no active radio transport, dropping ${bytes.size} bytes" }
                    return
                }
        _serviceScope.handledLaunch {
            currentTransport.handleSendToRadio(bytes)
            _meshActivity.tryEmit(MeshActivity.Send)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun handleFromRadio(bytes: ByteArray) {
        try {
            lastDataReceivedMillis = now()
            // trySend synchronously onto the unbounded Channel so packet order matches arrival
            // order. The previous `launch { emit() }` pattern dispatched each packet onto a
            // fresh coroutine, letting the scheduler reorder them — which broke the firmware
            // config handshake (see PhoneAPI.cpp initial-handshake sequence).
            val result = _receivedData.trySend(bytes)
            if (result.isFailure) {
                Logger.e(result.exceptionOrNull()) { "Failed to enqueue ${bytes.size} received bytes; dropping packet" }
            }
            _meshActivity.tryEmit(MeshActivity.Receive)
        } catch (t: Throwable) {
            Logger.e(t) { "handleFromRadio failed while emitting data" }
        }
    }

    override fun resetReceivedBuffer() {
        // Drain any bytes buffered while no collector was attached. Without this, a stop/start cycle
        // would replay stale bytes ahead of the next session's firmware handshake, since the channel
        // outlives the orchestrator's per-start scope.
        @Suppress("EmptyWhileBlock", "ControlFlowWithEmptyBody")
        while (_receivedData.tryReceive().isSuccess) {}
    }

    override fun onConnect() {
        // MutableStateFlow.value is thread-safe (backed by atomics) — assign directly rather than
        // launching a coroutine. The async launch pattern introduced a window where a concurrent
        // onDisconnect launch could execute AFTER an onConnect launch, leaving the service stuck
        // in Connected while the transport was actually disconnected.
        lastDataReceivedMillis = now()
        if (_connectionState.value != ConnectionState.Connected) {
            Logger.d { "Broadcasting connection state change to Connected" }
            _connectionState.value = ConnectionState.Connected
        }
    }

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?, reason: TransportDisconnectReason?) {
        val resolvedErrorMessage = errorMessage ?: reason?.toConnectionErrorMessage()
        if (resolvedErrorMessage != null) {
            processLifecycle.coroutineScope.launch(dispatchers.default) { _connectionError.emit(resolvedErrorMessage) }
        }
        val newTargetState = if (isPermanent) ConnectionState.Disconnected else ConnectionState.DeviceSleep
        if (_connectionState.value != newTargetState) {
            Logger.d { "Broadcasting connection state change to $newTargetState" }
            _connectionState.value = newTargetState
        }
    }
}
