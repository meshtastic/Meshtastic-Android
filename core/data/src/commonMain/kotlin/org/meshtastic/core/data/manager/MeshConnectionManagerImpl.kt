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
import co.touchlab.kermit.Severity
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.HandshakeConstants
import org.meshtastic.core.repository.HistoryManager
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NodeRestartTracker
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.SessionManager
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.error_recovery_exhausted
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.ToRadio
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Suppress("LongParameterList", "TooManyFunctions")
@Single
class MeshConnectionManagerImpl(
    private val radioInterfaceService: RadioInterfaceService,
    private val serviceRepository: ServiceRepository,
    private val serviceNotifications: MeshNotificationManager,
    private val uiPrefs: UiPrefs,
    private val packetHandler: PacketHandler,
    private val nodeRepository: NodeRepository,
    private val locationManager: MeshLocationManager,
    private val mqttManager: MqttManager,
    private val historyManager: HistoryManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val commandSender: CommandSender,
    private val sessionManager: SessionManager,
    private val nodeManager: NodeManager,
    private val analytics: PlatformAnalytics,
    private val packetRepository: PacketRepository,
    private val workerManager: MeshWorkerManager,
    private val appWidgetUpdater: AppWidgetUpdater,
    private val heartbeatSender: DataLayerHeartbeatSender,
    private val lockdownCoordinator: LockdownCoordinator,
    @Named("ServiceScope") private val scope: CoroutineScope,
    private val nodeRestartTracker: NodeRestartTracker,
) : MeshConnectionManager {
    /**
     * Serializes [onConnectionChanged] to prevent TOCTOU races when multiple coroutines emit state transitions
     * concurrently (e.g. flow collector vs. sleep-timeout coroutine).
     */
    private val connectionMutex = Mutex()

    private var preHandshakeJob: Job? = null
    private var sleepTimeout: Job? = null
    private var locationRequestsJob: Job? = null

    private val handshakeTimeout = atomic<Job?>(null)

    /**
     * One-way latch set by [onHandshakeComplete] and cleared at the start of each fresh handshake in [handleConnected].
     *
     * Prevents late-arriving handshake-progress packets (e.g. a FileInfo that lands between NODE_INFO_NONCE and the
     * completion of the async Room DB install) from re-arming the fast watchdog that [onHandshakeComplete] already
     * cancelled. Without this latch, those late packets would re-introduce the slow-DB false-trip on large meshes that
     * [onHandshakeComplete] was added to prevent: the app state is still Connecting during that window, so the existing
     * Connecting-state guard inside [onHandshakeProgress] is insufficient on its own.
     */
    private val handshakeCompleteLatch = atomic(false)

    /**
     * Consecutive handshake-recovery failure count for [runSiblingHandshakeRecovery].
     *
     * Incremented atomically on each recovery attempt. Reset to 0 by [onHandshakeComplete] (a successful handshake
     * breaks the failure streak) and also reset when the cap is reached and a sticky error is surfaced (so a manual
     * user retry starts fresh). When this reaches [MAX_CONSECUTIVE_RECOVERY_FAILURES], recovery stops and the sticky
     * error is surfaced instead of silently retrying indefinitely.
     */
    private val consecutiveRecoveryFailures = atomic(0)

    private var connectTimeMsec = 0L
    private var connectionRestored = false

    init {
        // Bridge transport-level state into the canonical app-level state.
        // This is the ONLY consumer of RadioInterfaceService.connectionState — it applies
        // light-sleep policy and handshake awareness before writing to ServiceRepository.
        radioInterfaceService.connectionState.onEach(::onRadioConnectionState).launchIn(scope)

        // Ensure notification title and content stay in sync with state changes
        serviceRepository.connectionState.onEach { updateStatusNotification() }.launchIn(scope)

        // An expected node restart ends when the post-reboot config handshake completes.
        serviceRepository.connectionState
            .onEach { if (it == ConnectionState.Connected) nodeRestartTracker.onConnected() }
            .launchIn(scope)

        scope.launch {
            try {
                appWidgetUpdater.updateAll()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.e(e) { "Failed to kickstart LocalStatsWidget" }
            }
        }

        nodeRepository.myNodeInfo
            .onEach { myNodeEntity ->
                locationRequestsJob?.cancel()
                if (myNodeEntity != null) {
                    locationRequestsJob =
                        uiPrefs
                            .shouldProvideNodeLocation(myNodeEntity.myNodeNum)
                            .onEach { shouldProvide ->
                                if (shouldProvide) {
                                    locationManager.start(scope) { pos -> commandSender.sendPosition(pos) }
                                } else {
                                    locationManager.stop()
                                }
                            }
                            .launchIn(scope)
                }
            }
            .launchIn(scope)
    }

    /**
     * Bridges a transport-level [ConnectionState] into the canonical app-level state.
     *
     * Applies light-sleep policy (power-saving / router role) to decide whether a [ConnectionState.DeviceSleep] event
     * should be surfaced as sleep or as a full disconnect, then delegates to [onConnectionChanged] for the actual state
     * transition.
     */
    private suspend fun onRadioConnectionState(newState: ConnectionState) {
        val localConfig = radioConfigRepository.localConfigFlow.first()
        val isRouter = localConfig.device?.role == Config.DeviceConfig.Role.ROUTER
        val lsEnabled = localConfig.power?.is_power_saving == true || isRouter

        val effectiveState =
            when (newState) {
                is ConnectionState.Connected -> ConnectionState.Connected

                is ConnectionState.DeviceSleep ->
                    if (lsEnabled) ConnectionState.DeviceSleep else ConnectionState.Disconnected

                is ConnectionState.Connecting -> ConnectionState.Connecting

                is ConnectionState.Disconnected -> ConnectionState.Disconnected
            }
        onConnectionChanged(effectiveState)
    }

    private suspend fun onConnectionChanged(c: ConnectionState, fromState: ConnectionState? = null): Boolean =
        connectionMutex.withLock {
            val current = serviceRepository.connectionState.value
            if (fromState != null && current != fromState) {
                Logger.d { "Skipping connection transition $current -> $c, expected current state $fromState" }
                return@withLock false
            }
            if (current == c) return@withLock false

            // If the transport reports 'Connected', but we are already in the middle of a handshake (Connecting)
            if (c is ConnectionState.Connected && current is ConnectionState.Connecting) {
                Logger.d { "Ignoring redundant transport connection signal while handshake is in progress" }
                return@withLock false
            }

            Logger.i { "onConnectionChanged: $current -> $c" }

            sleepTimeout?.cancel()
            sleepTimeout = null
            preHandshakeJob?.cancel()
            preHandshakeJob = null
            // Collapse cancel+clear into one atomic swap so a concurrent re-arm cannot
            // orphan a job in the gap between cancel and reassign.
            handshakeTimeout.getAndSet(null)?.cancel()

            when (c) {
                is ConnectionState.Connecting -> serviceRepository.setConnectionState(ConnectionState.Connecting)
                is ConnectionState.Connected -> handleConnected()
                is ConnectionState.DeviceSleep -> handleDeviceSleep()
                is ConnectionState.Disconnected -> handleDisconnected()
            }
            true
        }

    private fun handleConnected() {
        // Track whether this connection was restored from device sleep (vs. a fresh connect),
        // matching Apple's "connectionRestored" attribute for cross-platform DataDog parity.
        connectionRestored = serviceRepository.connectionState.value is ConnectionState.DeviceSleep
        // The service state remains 'Connecting' until config is fully loaded
        if (serviceRepository.connectionState.value != ConnectionState.Connected) {
            serviceRepository.setConnectionState(ConnectionState.Connecting)
        }
        connectTimeMsec = nowMillis
        // A fresh handshake is starting — clear the completion latch so progress signals during
        // this handshake can re-arm the fast watchdog. The latch is set by onHandshakeComplete()
        // and only matters in the window between Stage 2 completion and the subsequent Connected
        // transition; without this reset, a recovery-restarted handshake would have its progress
        // signals ignored (latch left set from the prior failed cycle). This covers both initial
        // user-initiated connects and transport-restart recovery siblings.
        handshakeCompleteLatch.value = false
        lockdownCoordinator.onConnect()
        // Send a wake-up heartbeat before the config request. The firmware may be in a
        // power-saving state where the NimBLE callback context needs warming up. The 100ms
        // delay ensures the heartbeat BLE write is enqueued before the want_config_id
        // (sendToRadio is fire-and-forget through async coroutine launches).
        preHandshakeJob =
            scope.handledLaunch {
                heartbeatSender.sendHeartbeat("pre-handshake")
                delay(PRE_HANDSHAKE_SETTLE_MS)
                Logger.i { "Starting mesh handshake (Stage 1)" }
                startConfigOnly()
            }
    }

    private fun startHandshakeStallGuard(stage: Int, timeout: Duration) {
        val fastTransport = isFastRecoveryTransport()
        // On TCP/USB the firmware handshake completes in roughly 1s when healthy (logs show),
        // while a wedged socket takes the full ~30s transport read timeout without any further
        // progress. The aggressive 12s fast timeout recovers a stuck session quickly; BLE keeps
        // the original generous budget because its GATT latency is high and variable.
        val effectiveTimeout = if (fastTransport) FAST_HANDSHAKE_TIMEOUT else timeout
        val transportLabel = if (fastTransport) "fast transport" else "BLE"
        // Collapse cancel+reassign into one atomic swap so a concurrent re-arm cannot orphan a
        // job in the gap between cancel and reassign.
        handshakeTimeout
            .getAndSet(
                scope.handledLaunch {
                    delay(effectiveTimeout)
                    if (serviceRepository.connectionState.value !is ConnectionState.Connecting) {
                        return@handledLaunch
                    }
                    // A clean transport restart is the ONLY safe stall recovery on every transport.
                    // The previous BLE branch re-sent want_config mid-session via action() here;
                    // that re-send is now deliberately removed because firmware's handleStartConfig()
                    // has no in-flight guard, so a second want_config on the same session re-enters
                    // it and crashes the firmware (reproduced on T-Beam v2.7.25.104df5f in QA). The
                    // firmware per-write dedup that was supposed to drop the retry is single-slot
                    // (memcmp vs the previous write only), and interleaved heartbeats mean the re-sent
                    // nonce is not byte-identical to the prior write — so it slips past dedup and
                    // re-enters handleStartConfig(). A clean transport restart creates a fresh
                    // session and re-enters the handshake naturally, which is both safer and more
                    // deterministic than a same-session retry.
                    Logger.e {
                        "Handshake stall detected at Stage $stage on $transportLabel — " +
                            "requesting forced transport restart"
                    }
                    runSiblingHandshakeRecovery()
                },
            )
            ?.cancel()
    }

    /**
     * Launches the deterministic two-phase stall-recovery sibling used by [startHandshakeStallGuard] on every
     * transport.
     *
     * Phase 1 flips the app-level state from Connecting to Disconnected first, guarded by the connection mutex so a
     * just-completed handshake cannot be torn down after winning the race. Phase 2 then calls
     * [RadioInterfaceService.restartTransport], whose emissions (DeviceSleep → Connected) now arrive from app-level
     * Disconnected, bypass the redundant-Connecting guard in [onConnectionChanged], and re-enter [handleConnected] to
     * restart the handshake cleanly.
     *
     * We MUST NOT call [onConnectionChanged] from the [handshakeTimeout] coroutine after launching the sibling:
     * [onConnectionChanged] cancels handshakeTimeout (the very job running this code), and any work chained after the
     * launch is not guaranteed to run. We MUST ALSO NOT leave the explicit Disconnected call in this coroutine after
     * the sibling launch — otherwise the sibling's restart emissions (DeviceSleep, then Connected) can arrive while the
     * app-level state is still Connecting, causing [onConnectionChanged]'s redundant-Connected-while-Connecting guard
     * to ignore the fresh Connected emission. That leaves the app Disconnected while transport is Connected — the same
     * split-brain this restart path is meant to break.
     *
     * By the time the sibling runs, handshakeTimeout has already completed naturally (it launched the sibling and
     * returned), so the cancellation [onConnectionChanged] would attempt is a no-op on an already-completed job — and
     * because the sibling is parented to `scope`, not to handshakeTimeout, it survives independently.
     *
     * Concurrent-recovery protection is layered across three independent mechanisms, each sufficient on its own:
     * 1. The [connectionMutex] serializes the [onConnectionChanged]`(Disconnected, fromState=Connecting)` call inside
     *    the sibling. If a concurrent sibling already transitioned the app state to Disconnected, a second caller's
     *    `fromState=Connecting` precondition fails and [onConnectionChanged] returns `false`, so the `if (disconnected
     *    && ...)` gate skips `restartTransport()`.
     * 2. The transport-level `isRestarting` CAS inside `SharedRadioInterfaceService.restartTransport()` provides
     *    authoritative dedup at the layer that actually tears down and re-creates the transport, independent of the
     *    app-level state machine above it.
     * 3. The atomic [consecutiveRecoveryFailures]`getAndIncrement()` ensures each concurrent caller observes a unique
     *    `priorFailures` slot, so the give-up decision (and the backoff duration) is race-free even if two siblings
     *    race the same stall window.
     *
     * Backoff and give-up cap: each call atomically increments [consecutiveRecoveryFailures]. Prior failures trigger an
     * exponential [delay] (2 s base, doubling each failure, capped at 30 s) before the transport restart so a node that
     * keeps crashing under handshake re-entry is not re-driven in a tight loop. After
     * [MAX_CONSECUTIVE_RECOVERY_FAILURES] consecutive failed recoveries, recovery stops and a sticky user-facing error
     * is surfaced (Disconnected + error message) instead of silently retrying indefinitely; the user must manually
     * re-select the node to retry, which resets the streak. The streak is also reset to 0 by [onHandshakeComplete] (a
     * successful handshake).
     */
    private fun runSiblingHandshakeRecovery() {
        // Atomically claim a failure slot. getAndIncrement guarantees each concurrent caller
        // observes a unique priorFailures value, so the give-up decision is race-free.
        val priorFailures = consecutiveRecoveryFailures.getAndIncrement()
        scope.handledLaunch {
            // After MAX_CONSECUTIVE_RECOVERY_FAILURES consecutive failed recoveries, stop retrying
            // and surface a sticky error. A node that keeps failing the handshake is likely
            // crashing firmware under re-entry (reproduced on T-Beam v2.7.25.104df5f), and
            // re-driving it indefinitely only makes things worse. Reset the counter here so a
            // manual retry from the user starts a fresh streak.
            if (priorFailures >= MAX_CONSECUTIVE_RECOVERY_FAILURES) {
                Logger.e {
                    "Handshake recovery exhausted after $MAX_CONSECUTIVE_RECOVERY_FAILURES consecutive " +
                        "failures; surfacing sticky error"
                }
                consecutiveRecoveryFailures.value = 0
                serviceRepository.setConnectionProgress("")
                onConnectionChanged(ConnectionState.Disconnected, fromState = ConnectionState.Connecting)
                // safeCatchingAll swallows Skiko ExceptionInInitializerError on headless JVM tests
                // where compose-resources can't load native libs. Production resolves the localized
                // string normally; tests fall back to empty and setErrorMessage is still called.
                val errorMessage =
                    safeCatchingAll { getStringSuspend(Res.string.error_recovery_exhausted) }.getOrDefault("")
                serviceRepository.setErrorMessage(errorMessage, Severity.Error)
                return@handledLaunch
            }
            // Exponential backoff before the next attempt: 2 s base, doubling for each prior
            // failure, hard-capped at 30 s. Skipped on the first attempt (no prior failures).
            // The delay is parented to `scope`, so it is cancelled cleanly if the user
            // disconnects, navigates away, or the service shuts down (structured concurrency).
            if (priorFailures > 0) {
                val backoffSeconds =
                    (RECOVERY_BACKOFF_BASE_SECONDS shl (priorFailures - 1)).coerceAtMost(RECOVERY_BACKOFF_CAP_SECONDS)
                Logger.w {
                    "Handshake recovery backoff: waiting ${backoffSeconds}s before retry (attempt ${priorFailures + 1})"
                }
                delay(backoffSeconds.seconds)
            }
            // Re-check state after backoff: the user may have disconnected during the delay.
            if (serviceRepository.connectionState.value !is ConnectionState.Connecting) return@handledLaunch
            // Surface the forced-recovery progress to the UI before the app-level Disconnected
            // transition lands, so the user sees "Reconnecting…" rather than a stale
            // "Loading node list" while the transport is being torn down and re-established.
            //
            // This progress is intentionally NOT cleared on the recovery's Disconnected window
            // (i.e. NOT in handleDisconnected or this sibling). If recovery fails permanently,
            // "Reconnecting…" may persist on the Disconnected screen until the user retries or
            // navigates away. That leak is acceptable UX: the transport restart has been
            // requested and may still be in flight (restartTransport is one-shot — if the fresh
            // transport also fails, nothing here retries automatically; transport-level
            // network-recovery listeners may independently re-bring-up the transport, and the
            // user can manually retry). Clearing it here would race the deliberate UX signal:
            // handleDisconnected runs synchronously after this call inside the same
            // onConnectionChanged transition, so any clear there would clobber the signal before
            // restartTransport runs. Stale progress is instead cleared at the first downstream
            // setConnectionProgress call during the recovery handshake (e.g. "Device config
            // received" or "Loading node list"), not by handleConnected itself — and at the
            // ViewModel level the Connecting state already dominates progress (the CONNECTING
            // status ignores the progress string), so the UI is correct regardless.
            serviceRepository.setConnectionProgress(ServiceRepository.RECONNECTING_PROGRESS_TEXT)
            val disconnected = onConnectionChanged(ConnectionState.Disconnected, fromState = ConnectionState.Connecting)
            if (disconnected && serviceRepository.connectionState.value is ConnectionState.Disconnected) {
                radioInterfaceService.restartTransport()
            }
        }
    }

    override fun recoverPostHandshakeFailure() {
        Logger.w { "Recovering from post-handshake failure by restarting transport" }
        runSiblingHandshakeRecovery()
    }

    private fun tearDownConnection() {
        packetHandler.stopPacketQueue()
        sessionManager.clearAll() // Prevent stale per-node passkeys on reconnect.
        locationManager.stop()
        mqttManager.stop()
    }

    private fun handleDeviceSleep() {
        serviceRepository.setConnectionState(ConnectionState.DeviceSleep)
        tearDownConnection()

        if (connectTimeMsec != 0L) {
            val now = nowMillis
            val duration = now - connectTimeMsec
            connectTimeMsec = 0L
            analytics.track(
                EVENT_CONNECTED_SECONDS,
                DataPair(EVENT_CONNECTED_SECONDS, duration.milliseconds.toDouble(DurationUnit.SECONDS)),
            )
        }

        sleepTimeout =
            scope.handledLaunch {
                try {
                    val localConfig = radioConfigRepository.localConfigFlow.first()
                    val rawTimeout = (localConfig.power?.ls_secs ?: 0) + DEVICE_SLEEP_TIMEOUT_SECONDS
                    // Cap the timeout so routers or power-saving configs (ls_secs=3600) don't
                    // leave the UI stuck in DeviceSleep for over an hour.
                    val timeout = rawTimeout.coerceAtMost(MAX_SLEEP_TIMEOUT_SECONDS)
                    Logger.d { "Waiting for sleeping device, timeout=$timeout secs (raw=$rawTimeout)" }
                    delay(timeout.seconds)
                    Logger.w { "Device timed out, setting disconnected" }
                    onConnectionChanged(ConnectionState.Disconnected)
                } catch (_: CancellationException) {
                    Logger.d { "device sleep timeout cancelled" }
                }
            }
    }

    private fun handleDisconnected() {
        serviceRepository.setConnectionState(ConnectionState.Disconnected)
        lockdownCoordinator.onDisconnect()
        tearDownConnection()

        analytics.track(
            EVENT_MESH_DISCONNECT,
            DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size),
            DataPair(KEY_NUM_ONLINE, nodeManager.nodeDBbyNodeNum.values.count { it.isOnline }),
        )
        analytics.track(EVENT_NUM_NODES, DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size))
    }

    override fun startConfigOnly() {
        startHandshakeStallGuard(1, HANDSHAKE_TIMEOUT_STAGE1)
        packetHandler.sendToRadio(ToRadio(want_config_id = HandshakeConstants.CONFIG_NONCE))
    }

    override fun clearRadioConfig() {
        scope.handledLaunch {
            radioConfigRepository.clearLocalConfig()
            radioConfigRepository.clearChannelSet()
            radioConfigRepository.clearLocalModuleConfig()
        }
    }

    override fun startNodeInfoOnly() {
        startHandshakeStallGuard(2, HANDSHAKE_TIMEOUT_STAGE2)
        packetHandler.sendToRadio(ToRadio(want_config_id = HandshakeConstants.NODE_INFO_NONCE))
    }

    override fun onRadioConfigLoaded() {
        scope.handledLaunch {
            val queuedPackets = packetRepository.getQueuedPackets()
            queuedPackets.forEach { packet ->
                try {
                    workerManager.enqueueSendMessage(packet.id)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.e(e) { "Failed to enqueue queued packet worker" }
                }
            }
        }
    }

    override suspend fun onNodeDbReady() {
        // Collapse cancel+clear into one atomic swap so a concurrent re-arm cannot
        // orphan a job in the gap between cancel and reassign.
        handshakeTimeout.getAndSet(null)?.cancel()

        val myNodeNum = nodeManager.myNodeNum.value ?: 0
        // Set device time now that the full node picture is ready. Sending this during Stage 1
        // (onRadioConfigLoaded) introduced GATT write contention with the Stage 2 node-info burst.
        commandSender.sendAdmin(myNodeNum) { AdminMessage(set_time_only = nowSeconds.toInt()) }

        // Proactively seed the session passkey. The firmware embeds session_passkey in every
        // admin *response* (wantResponse=true), but set_time_only has no response. A get_owner
        // request is the lightest way to trigger a response and populate the passkey cache so
        // that subsequent write operations don't fail with ADMIN_BAD_SESSION_KEY.
        commandSender.sendAdmin(myNodeNum, wantResponse = true) { AdminMessage(get_owner_request = true) }

        // Start MQTT if enabled
        scope.handledLaunch {
            val moduleConfig = radioConfigRepository.moduleConfigFlow.first()
            mqttManager.startProxy(
                moduleConfig.mqtt?.enabled == true,
                moduleConfig.mqtt?.proxy_to_client_enabled == true,
            )
        }

        reportConnection()

        // Request history
        scope.handledLaunch {
            val moduleConfig = radioConfigRepository.moduleConfigFlow.first()
            moduleConfig.store_forward?.let {
                historyManager.requestHistoryReplay("onNodeDbReady", myNodeNum, it, "Unknown")
            }
        }

        // Request immediate LocalStats and DeviceMetrics update on connection with proper request IDs
        commandSender.requestTelemetry(commandSender.generatePacketId(), myNodeNum, TelemetryType.LOCAL_STATS.ordinal)
        commandSender.requestTelemetry(commandSender.generatePacketId(), myNodeNum, TelemetryType.DEVICE.ordinal)
    }

    /**
     * Synchronously cancels the transport-aware handshake watchdog the moment Stage 2 completes (NODE_INFO_NONCE
     * received). Does NOT replicate [onNodeDbReady]'s post-NodeDB side effects (analytics, MQTT start, history replay,
     * telemetry requests) — those remain gated on [onNodeDbReady] at the end of the async DB install block.
     *
     * See [MeshConnectionManager.onHandshakeComplete] for the full rationale.
     */
    override fun onHandshakeComplete() {
        // Collapse cancel+clear into one atomic swap so a concurrent re-arm cannot orphan a
        // job in the gap between cancel and reassign.
        handshakeTimeout.getAndSet(null)?.cancel()
        // Set the completion latch so late-arriving progress packets (e.g. FileInfo that lands
        // between NODE_INFO_NONCE and the async Room DB install completion) cannot re-arm the
        // fast watchdog we just cancelled. Without this latch, those late packets would
        // re-introduce the slow-DB false-trip on large meshes that this method was added to
        // prevent — the app state is still Connecting during that window, so the Connecting-
        // state guard inside onHandshakeProgress() is insufficient on its own. The latch is
        // cleared at the start of the next fresh handshake in handleConnected().
        handshakeCompleteLatch.value = true
        // A successful handshake breaks the recovery failure streak — reset the consecutive
        // failure counter so the next stall starts from a fresh count.
        consecutiveRecoveryFailures.value = 0
    }

    private fun reportConnection() {
        val myNode = nodeManager.getMyNodeInfo()
        val radioModel = DataPair(KEY_RADIO_MODEL, myNode?.model ?: "unknown")
        analytics.track(
            EVENT_MESH_CONNECT,
            DataPair(KEY_NUM_NODES, nodeManager.nodeDBbyNodeNum.size),
            DataPair(KEY_NUM_ONLINE, nodeManager.nodeDBbyNodeNum.values.count { it.isOnline }),
            radioModel,
        )

        // DataDog RUM custom action matching Apple's "connect" event for cross-platform analytics.
        val transportType = radioInterfaceService.getDeviceAddress()?.let { DeviceType.fromAddress(it)?.name }
        analytics.trackConnect(
            firmwareVersion = myNode?.firmwareVersion,
            transportType = transportType,
            hardwareModel = myNode?.model,
            nodes = nodeManager.nodeDBbyNodeNum.size,
            connectionRestored = connectionRestored,
        )
    }

    override fun updateTelemetry(t: Telemetry) {
        t.local_stats?.let { nodeRepository.updateLocalStats(it) }
        updateStatusNotification(t)
    }

    /**
     * True when the active transport is a TCP or USB serial connection — i.e. a transport whose firmware handshake
     * reliably completes in roughly 1s when healthy and therefore benefits from aggressive silent-restart on stall.
     * Uses the same [DeviceType.fromAddress] pattern as [reportConnection] for transport classification. BLE is
     * excluded because its GATT latency budget is high and variable enough that the long-and-retry stall-guard budgets
     * remain the right trade-off.
     */
    private fun isFastRecoveryTransport(): Boolean =
        radioInterfaceService.getDeviceAddress()?.let { DeviceType.fromAddress(it) } in FAST_RECOVERY_TYPES

    override fun onHandshakeProgress() {
        // Arm only inside the fast-recovery envelope, while Connecting, before the completion latch
        // has fired. BLE retains the long stall-guard budget because its GATT latency is variable.
        val shouldArmFastWatchdog =
            isFastRecoveryTransport() &&
                serviceRepository.connectionState.value is ConnectionState.Connecting &&
                !handshakeCompleteLatch.value
        if (!shouldArmFastWatchdog) return
        // Atomic swap: cancel any in-flight fast watchdog and re-arm it with the full fast
        // timeout in a single operation. This keeps the watchdog quiet as long as meaningful
        // progress keeps arriving within the window, while a true stall still fires on
        // schedule. getAndSet prevents a concurrent re-arm from orphaning a job in the gap
        // between cancel and reassign.
        handshakeTimeout
            .getAndSet(
                scope.handledLaunch {
                    delay(FAST_HANDSHAKE_TIMEOUT)
                    if (serviceRepository.connectionState.value !is ConnectionState.Connecting) {
                        return@handledLaunch
                    }
                    Logger.e {
                        "Fast-handshake watchdog expired after progress stalled — requesting forced transport restart"
                    }
                    runSiblingHandshakeRecovery()
                },
            )
            ?.cancel()
    }

    override fun updateStatusNotification(telemetry: Telemetry?) {
        val state = serviceRepository.connectionState.value
        // During an expected node restart the disconnect is transient; keep the persistent notification on the
        // connecting presentation instead of flashing "disconnected".
        val presented =
            if (state == ConnectionState.Disconnected && nodeRestartTracker.restartExpected.value) {
                ConnectionState.Connecting
            } else {
                state
            }
        serviceNotifications.updateServiceStateNotification(presented, telemetry = telemetry)
    }

    companion object {
        // Hoisted constant — used on every meaningful handshake packet via
        // isFastRecoveryTransport(); avoids allocating a fresh Set per packet.
        private val FAST_RECOVERY_TYPES = setOf(DeviceType.TCP, DeviceType.USB)

        private const val DEVICE_SLEEP_TIMEOUT_SECONDS = 30

        // Maximum time (in seconds) to wait for a sleeping device before declaring it
        // disconnected, regardless of the device's ls_secs configuration. Without this
        // cap, routers (ls_secs=3600) leave the UI in DeviceSleep for over an hour.
        private const val MAX_SLEEP_TIMEOUT_SECONDS = 300

        /**
         * Delay between the pre-handshake heartbeat and the want_config_id send.
         *
         * Ensures the heartbeat BLE write completes and the firmware's NimBLE callback context is warmed up before the
         * config request arrives. 100ms is well above observed ESP32 task scheduling latency (~10–50ms) while adding
         * negligible connection latency.
         */
        private const val PRE_HANDSHAKE_SETTLE_MS = 100L

        private val HANDSHAKE_TIMEOUT_STAGE1 = 30.seconds

        /**
         * Stage 2 drains the full node database, which can be significantly larger than Stage 1 config on big meshes.
         * 60 s matches the meshtastic-client SDK timeout and avoids premature stall-guard triggers on meshes with 50+
         * nodes.
         */
        private val HANDSHAKE_TIMEOUT_STAGE2 = 60.seconds

        /**
         * Transport-aware fast-recovery timeout for the handshake stall guard, applied only to TCP and USB serial
         * transports.
         *
         * Production logs on TCP/USB show a healthy firmware handshake completes in roughly 1 second, while a wedged
         * socket sits idle for the full transport-level read timeout (~30s) without any further progress. 12s sits
         * comfortably above the healthy success envelope and well below the transport read timeout, so firing a silent
         * [RadioInterfaceService.restartTransport] at 12s recovers a stuck TCP/USB session quickly without
         * false-positiving on healthy connections.
         *
         * BLE is intentionally excluded — its GATT latency budget is variable enough that the existing
         * [HANDSHAKE_TIMEOUT_STAGE1] (30s) and [HANDSHAKE_TIMEOUT_STAGE2] (60s) budgets remain the right trade-off.
         * Both transports now recover exclusively via [runSiblingHandshakeRecovery] (transport restart); the previous
         * BLE mid-session want_config retry has been removed (see [startHandshakeStallGuard] for the firmware crash
         * rationale).
         */
        private val FAST_HANDSHAKE_TIMEOUT = 12.seconds

        /**
         * Maximum consecutive handshake-recovery attempts before surfacing a sticky error to the user.
         *
         * Each call to [runSiblingHandshakeRecovery] counts as one attempt. After this many consecutive attempts fail
         * to lead to a successful handshake, recovery stops and a sticky Disconnected + error state is surfaced,
         * requiring the user to manually re-select the node to retry. This prevents indefinite re-driving of a node
         * whose firmware is crashing under handshake re-entry (reproduced on T-Beam v2.7.25.104df5f).
         */
        private const val MAX_CONSECUTIVE_RECOVERY_FAILURES = 3

        /**
         * Base delay (seconds) for the exponential backoff applied between consecutive recovery attempts in
         * [runSiblingHandshakeRecovery]. Doubled for each prior failure and hard-capped at
         * [RECOVERY_BACKOFF_CAP_SECONDS].
         *
         * 2 s base gives the firmware and transport a brief breather after a failed restart without adding perceptible
         * latency to the first retry; doubling bounds the worst-case loop tightly under the 3-strike cap.
         */
        private const val RECOVERY_BACKOFF_BASE_SECONDS = 2L

        /**
         * Hard cap (seconds) for the exponential backoff between recovery attempts.
         *
         * Keeps the per-attempt delay bounded even if [MAX_CONSECUTIVE_RECOVERY_FAILURES] is raised in the future. 30 s
         * matches the transport-level read timeout envelope, so a backed-off retry never waits longer than the
         * underlying transport would have taken to fail on its own.
         */
        // Cap unreachable while MAX_CONSECUTIVE_RECOVERY_FAILURES=3 (max computed delay is 4s);
        // retained as defense-in-depth if MAX is raised in the future.
        private const val RECOVERY_BACKOFF_CAP_SECONDS = 30L

        private const val EVENT_CONNECTED_SECONDS = "connected_seconds"
        private const val EVENT_MESH_DISCONNECT = "mesh_disconnect"
        private const val EVENT_NUM_NODES = "num_nodes"
        private const val EVENT_MESH_CONNECT = "mesh_connect"

        private const val KEY_NUM_NODES = "num_nodes"
        private const val KEY_NUM_ONLINE = "num_online"
        private const val KEY_RADIO_MODEL = "radio_model"
    }
}
