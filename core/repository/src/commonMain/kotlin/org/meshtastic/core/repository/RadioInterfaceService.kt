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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
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
 * The selected radio and its SDK client: device selection, session admission and the per-session SDK clients.
 *
 * The SDK client owns connecting and reconnecting. This service builds one client per selected device, starts and stops
 * it, nudges it when the environment recovers, and publishes the client's [org.meshtastic.sdk.ConnectionState] as
 * [ServiceRepository.connectionState], which is what UI and features observe.
 */
@Suppress("TooManyFunctions")
interface RadioInterfaceService : RadioSessionAuthority {
    /** Captures the SDK admin API for a single active radio session. */
    fun adminForSession(session: RadioSessionContext): AdminApi? = clientForSession(session)?.admin

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

    /** Queues a nodes-only refresh for the admitted SDK session. */
    fun requestNodeDbRefresh(session: RadioSessionContext): Boolean = false

    /** Flow of the current device address. */
    val currentDeviceAddressFlow: StateFlow<String?>

    /**
     * Monotonically increasing generation bumped on every app session, one per SDK session, a reconnect included.
     * Consumers use this to discard state retained from a previous session. Stub implementations expose a constant zero
     * flow.
     */
    val sessionGeneration: StateFlow<Long>

    /**
     * Whether the virtual demo transports may be offered and bound right now.
     *
     * @see RadioLinks.mockTransportEnabled
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

    /**
     * Connects the selected radio: starts its client, or asks a client waiting out a reconnect backoff to try now. A
     * client that failed or was disconnected is replaced by a fresh one.
     */
    fun connect()

    /**
     * Revokes app session admission and disconnects the selected radio's client after its leases drain. Safe to call
     * with nothing connected. Suspends until teardown completes.
     */
    suspend fun disconnect()

    /**
     * Requests that the next Bluetooth connection refreshes Android's GATT service cache before service discovery,
     * after a firmware update that may have changed the radio's service table on the same address. Cleared by
     * [disconnect] and by selecting a different radio.
     */
    fun requestGattCacheInvalidationOnNextConnect()

    /** Returns the current device address. */
    fun getDeviceAddress(): String?

    /** Sets the device address to connect to. */
    fun setDeviceAddress(deviceAddr: String?): Boolean

    /** Constructs a full radio address for the specific interface type. */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String

    /** Flow of user-facing connection error messages (e.g. a radio that gave its slot to another client). */
    val connectionError: Flow<String>
}
