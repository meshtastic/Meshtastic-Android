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
package org.meshtastic.core.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.model.TransportStatus
import org.meshtastic.proto.LockdownAuth
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MqttClientProxyMessage
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminSessionStatus
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.PacedMessageHandle
import org.meshtastic.sdk.RadioClient

/** A lifecycle lease held by one already-admitted operation for [session]. */
interface RadioSessionLease {
    val session: RadioSessionContext

    /** True until this admitted operation releases the session and rollover may complete. */
    fun isCurrent(): Boolean
}

/** Owns admission and revocation for work bound to one transport session. */
interface RadioSessionAuthority {
    /**
     * The transport session that still owns lifecycle completion, or null after teardown drains every admitted
     * operation. Implementations close admission before teardown, so [isSessionActive] and the run helpers reject new
     * work immediately even while this flow temporarily retains the draining session.
     */
    val activeSession: StateFlow<RadioSessionContext?>

    /**
     * Returns whether [session] still owns transport admission. Implementations must make this reflect the admission
     * gate, not only [activeSession], because the draining session may remain published after new work is rejected.
     */
    fun isSessionActive(session: RadioSessionContext): Boolean

    /**
     * Runs [block] only while [session] owns transport admission. Implementations must make the admission check and
     * synchronous side effect atomic with teardown.
     */
    fun runIfSessionActive(session: RadioSessionContext, block: () -> Unit): Boolean

    /**
     * Acquires a lifecycle lease for suspend [block]. Once admitted, teardown closes admission to later work and waits
     * for this block to finish before publishing session completion or starting a replacement transport. [block] may
     * use [RadioSessionLease.isCurrent] for transaction-bound checks that must remain valid through commit even after
     * teardown has closed new admission. Implementations must acquire and release the lease through the same admission
     * state used by teardown; comparing only [activeSession] cannot satisfy the draining contract. Callers must keep
     * the block bounded and must not invoke transport lifecycle methods from inside it.
     */
    suspend fun runWithSessionLease(session: RadioSessionContext, block: suspend (RadioSessionLease) -> Unit): Boolean

    /**
     * Runs [block] while holding the same lifecycle lease, without exposing the lease token. Implementations may
     * serialize this convenience path to preserve handshake ordering; independently deferred work should use
     * [runWithSessionLease] so it can acquire its own lease before its parent operation returns. [label] names the
     * operation in diagnostics and must not carry packet contents or identifiers.
     */
    suspend fun runWhileSessionActive(session: RadioSessionContext, label: String, block: suspend () -> Unit): Boolean =
        runWithSessionLease(session) { block() }
}

/**
 * The selected radio's link and its SDK session: device selection, session admission and the per-session SDK clients.
 *
 * This is the **transport layer** — it manages the hardware connection (BLE, TCP, Serial, USB) to a Meshtastic radio.
 * Its [connectionState] reflects whether the physical link is up or down, **before** any handshake or config-loading
 * logic is applied.
 *
 * **Important:** UI and feature modules should **never** observe [connectionState] directly. Instead, they should use
 * [ServiceRepository.connectionState], which is the canonical app-level connection state that accounts for handshake
 * progress and other higher-level concerns. The only legitimate consumer of this transport-level flow is
 * [MeshConnectionManager], which bridges transport state changes into the app-level
 * [ServiceRepository.connectionState].
 *
 * @see ServiceRepository.connectionState
 */
@Suppress("TooManyFunctions")
interface RadioInterfaceService :
    RadioTransportCallback,
    RadioSessionAuthority {
    /** Captures the SDK admin API for a single active radio session. */
    fun adminForSession(session: RadioSessionContext): AdminApi? = null

    /** Captures the SDK client for a single active radio session, or null once [session] no longer owns admission. */
    fun clientForSession(session: RadioSessionContext): RadioClient? = null

    /** Observes per-node admin status across radio session changes. */
    fun adminSessionStatus(node: NodeId): Flow<AdminSessionStatus> = flowOf(AdminSessionStatus.NoSession)

    /** Submits a mesh packet only while [session] owns transport admission. */
    fun sendPaced(session: RadioSessionContext, packet: MeshPacket): PacedMessageHandle? = null

    /** Queues authentication only while [session] owns the link. */
    fun tryLockdown(session: RadioSessionContext, auth: LockdownAuth): Boolean = false

    /** The device types supported by this platform's radio interface. */
    val supportedDeviceTypes: List<DeviceType>

    /**
     * Transport-level connection state of the radio hardware.
     *
     * This flow reflects the raw state of the physical link (BLE, TCP, Serial, USB):
     * - [ConnectionState.Connected] — the transport link is established
     * - [ConnectionState.Disconnected] — the transport link is down
     * - [ConnectionState.DeviceSleep] — the SDK judged the drop a power-saving device's sleep, by design
     *
     * **This is NOT the canonical app-level connection state.** The transport may report [ConnectionState.Connected]
     * while the app is still performing the mesh handshake (config + node-info exchange), during which the app-level
     * state remains [ConnectionState.Connecting].
     *
     * Only [MeshConnectionManager] should observe this flow. All other consumers (ViewModels, feature modules, UI) must
     * use [ServiceRepository.connectionState].
     *
     * @see ServiceRepository.connectionState
     */
    val connectionState: StateFlow<ConnectionState>

    /** Queues a nodes-only refresh for the admitted SDK session. */
    fun requestNodeDbRefresh(session: RadioSessionContext): Boolean = false

    /** Flow of the current device address. */
    val currentDeviceAddressFlow: StateFlow<String?>

    /**
     * Monotonically increasing generation bumped on every transport start (including same-address reconnect). Consumers
     * use this to discard state retained from a previous transport instance. Stub implementations that never start a
     * real transport expose a constant zero flow.
     */
    val sessionGeneration: StateFlow<Long>

    /**
     * Whether the virtual demo transports may be offered and bound right now.
     *
     * @see RadioTransportFactory.mockTransportEnabled
     */
    val mockTransportEnabled: StateFlow<Boolean>

    /**
     * Whether this build carries the packet capture the replay transport needs. False means selecting a replay address
     * would fall back to the plain mock, so callers offering a replay device must hide it.
     */
    val isReplayTransportAvailable: Boolean

    /** Flow of radio activity events. */
    val meshActivity: Flow<MeshActivity>

    /** Reports one frame received from the radio on [meshActivity]. */
    fun reportReceiveActivity() = Unit

    /** Hands a broker message to the active session's MQTT client proxy; false when no session can take it. */
    suspend fun deliverMqttProxy(message: MqttClientProxyMessage): Boolean = false

    /** Initiates the connection to the radio. */
    fun connect()

    /**
     * Revokes app session admission and closes the active SDK session after its leases drain. The SDK handles the
     * goodbye frame and link shutdown. Safe to call with no active session. Suspends until teardown completes.
     */
    suspend fun disconnect()

    /**
     * Cycles the active transport in place: stops it and starts a replacement for the same selected device, without
     * touching the connection-request gate or [currentDeviceAddressFlow].
     *
     * Contract:
     * - A no-op when [expectedSession] is given and no longer owns admission, when no connection is requested, when no
     *   device is selected, or when no transport is running.
     * - Safe to call concurrently with an explicit [disconnect]; the internal gate check makes it a no-op then.
     * - The replacement is built from the same bonded address through the normal start path.
     * - Observers see a transient [ConnectionState.Disconnected] through [RadioTransportCallback], then
     *   [ConnectionState.Connected] when the replacement connects. [ConnectionState.Connecting] is an app-level state
     *   set by [MeshConnectionManager], never a transport callback.
     *
     * Suspends until the teardown/restart cycle completes.
     */
    suspend fun restartTransport(expectedSession: RadioSessionContext? = null)

    /**
     * Requests that the next BLE transport connection invalidates Android's GATT service cache before service
     * discovery. Used after OTA firmware updates where the device reboots with a potentially different BLE service
     * table on the same MAC address.
     *
     * The flag is one-shot: consumed on the first connect after being set.
     */
    fun requestGattCacheInvalidationOnNextConnect()

    /**
     * Consumes and returns the GATT cache invalidation request. Returns `true` exactly once after
     * [requestGattCacheInvalidationOnNextConnect] was called, then resets to `false`.
     *
     * Intended to be called by the BLE transport during connection setup.
     */
    fun consumeGattCacheInvalidationRequest(): Boolean

    /**
     * Whether the BLE link that just connected should refresh Android's GATT cache: the post-OTA request, or a long BLE
     * failure streak suspected of a stale cache. Called once per link, after it connects; consumes either trigger.
     */
    fun consumeGattCacheRefreshOnConnect(): Boolean = consumeGattCacheInvalidationRequest()

    /** Returns the current device address. */
    fun getDeviceAddress(): String?

    /** Sets the device address to connect to. */
    fun setDeviceAddress(deviceAddr: String?): Boolean

    /** Constructs a full radio address for the specific interface type. */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String

    /** Flow of user-facing connection error messages (e.g. permission failures). */
    val connectionError: Flow<String>

    /**
     * What the selected device's connection is doing and why it last failed, as reported through
     * [RadioTransportCallback.onStatus]. Null when nothing has been reported since the last explicit disconnect or
     * device switch.
     */
    val transportStatus: StateFlow<TransportStatus?>

    /** The scope in which interface-related coroutines should run. */
    val serviceScope: CoroutineScope
}
