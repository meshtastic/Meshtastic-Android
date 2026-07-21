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

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.NodeIdLookup
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FirmwareEdition
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.StatusMessage
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo
import org.meshtastic.proto.Position as ProtoPosition

/**
 * Fresh-handshake identity for one connection session. Separate from the general [NodeManager.myNodeNum]/
 * [NodeManager.myDeviceId] StateFlows, which can retain stale cached values across transport switches. [address],
 * [nodeNum], and [deviceId] are captured from one selected transport and one MyNodeInfo handshake so consumers never
 * have to combine independently delivered address and identity flows. [sessionGeneration] is the active transport
 * session generation at handshake time, so the association collector can discard an identity retained from a previous
 * transport instance even if every other field is equal.
 */
data class ConnectionIdentity(
    val sessionGeneration: Long,
    val address: String,
    val nodeNum: Int,
    val deviceId: String?,
) {
    /** Privacy-safe diagnostic form: neither the transport address nor factory-burned device ID may enter logs. */
    override fun toString(): String = "ConnectionIdentity(sessionGeneration=$sessionGeneration, address=..., " +
        "nodeNum=$nodeNum, deviceIdPresent=${deviceId != null})"
}

/** Interface for managing the in-memory node database and processing received node information. */
@Suppress("TooManyFunctions")
interface NodeManager : NodeIdLookup {
    /** Reactive map of all nodes by their number. */
    val nodeDBbyNodeNum: Map<Int, Node>

    /** Look up a node by its user ID string (e.g. `"!a1b2c3d4"`). */
    fun getNodeById(id: String): Node?

    /** Whether the node database is ready. */
    val isNodeDbReady: StateFlow<Boolean>

    /** Sets whether the node database is ready. */
    fun setNodeDbReady(ready: Boolean)

    /** Whether node database writes are allowed. */
    val allowNodeDbWrites: StateFlow<Boolean>

    /** Sets whether node database writes are allowed. */
    fun setAllowNodeDbWrites(allowed: Boolean)

    /** The local node number as a thread-safe [StateFlow]. */
    val myNodeNum: StateFlow<Int?>

    /** Sets the local node number. */
    fun setMyNodeNum(num: Int?)

    /**
     * The connected device's factory-burned hardware id from MyNodeInfo, as a thread-safe [StateFlow]. Null when not
     * connected or when the hardware/firmware doesn't report one. Unlike [myNodeNum] (which firmware 2.8 re-derives
     * from the public key), this survives upgrades, erases, and key changes.
     */
    val myDeviceId: StateFlow<String?>

    /** Sets the connected device's hardware id. */
    fun setMyDeviceId(id: String?)

    /** The firmware edition reported by the connected device. */
    val firmwareEdition: StateFlow<FirmwareEdition?>

    /** Sets the firmware edition of the connected device. */
    fun setFirmwareEdition(edition: FirmwareEdition?)

    /**
     * Fresh-handshake identity for the current connection session. Null when no handshake identity has been confirmed
     * for the active transport. Cleared synchronously before a new address is published, and populated atomically when
     * [handleMyInfo] captures the selected address and decodes nodeNum and deviceId from the same MyNodeInfo packet.
     * Generation-boundary cleanup atomically preserves an identity already published for the new session. Never
     * populated by [loadCachedNodeDB] or reactive DB cache emissions.
     */
    val connectionIdentity: StateFlow<ConnectionIdentity?>

    /** Clears the connection-session identity source synchronously. */
    fun clearConnectionIdentity()

    /**
     * Atomically clears an identity retained from a generation other than [activeSessionGeneration]. An identity that
     * already belongs to the active generation is preserved, even when its publication races a delayed boundary
     * collector from the same generation.
     */
    fun clearStaleConnectionIdentity(activeSessionGeneration: Long)

    /**
     * Publishes a fresh connection-session identity from a handshake MyNodeInfo. Invoked exactly once per handshake
     * after [sessionGeneration], [address], [nodeNum], and [deviceId] have been captured for the same session.
     */
    fun publishConnectionIdentity(sessionGeneration: Long, address: String, nodeNum: Int, deviceId: String?)

    /** Loads the cached node database from the repository. */
    fun loadCachedNodeDB()

    /** Clears the in-memory node database. */
    fun clear()

    /** Returns information about the local node. */
    fun getMyNodeInfo(): MyNodeInfo?

    /** Returns the local node ID. */
    fun getMyId(): String

    /** Processes a received user packet. */
    fun handleReceivedUser(
        fromNum: Int,
        p: User,
        channel: Int = 0,
        manuallyVerified: Boolean = false,
        session: RadioSessionContext? = null,
    )

    /** Processes a received position packet. */
    fun handleReceivedPosition(
        fromNum: Int,
        myNodeNum: Int,
        p: ProtoPosition,
        defaultTime: Long,
        session: RadioSessionContext? = null,
    )

    /** Processes a received telemetry packet. */
    fun handleReceivedTelemetry(fromNum: Int, telemetry: Telemetry)

    /** Processes a received paxcounter packet. */
    fun handleReceivedPaxcounter(fromNum: Int, p: Paxcount, session: RadioSessionContext? = null)

    /** Processes a received node status message. */
    fun handleReceivedNodeStatus(fromNum: Int, s: StatusMessage, session: RadioSessionContext? = null)

    /** Updates the status string for a node. */
    fun updateNodeStatus(nodeNum: Int, status: String?)

    /** Updates node status and awaits any required persistence. */
    suspend fun updateNodeStatusAndPersist(nodeNum: Int, status: String?)

    /**
     * Updates a node using a side-effect-free [transform] and schedules persistence. The transform may be evaluated
     * more than once after compare-and-set contention; callers must perform notifications and other effects afterward.
     */
    fun updateNode(nodeNum: Int, channel: Int = 0, transform: (Node) -> Node)

    /** Session-bound counterpart to [updateNode]; deferred persistence is admitted only for [session]. */
    fun updateNodeForSession(nodeNum: Int, session: RadioSessionContext, channel: Int = 0, transform: (Node) -> Node)

    /**
     * Updates a node using a side-effect-free [transform] and awaits any required persistence before returning. The
     * transform may be evaluated more than once after compare-and-set contention.
     *
     * Session-scoped packet processing uses this while holding transport authority so an old session cannot enqueue a
     * database write that resumes after a device switch. Non-session UI and controller updates continue to use
     * [updateNode].
     */
    suspend fun updateNodeAndPersist(nodeNum: Int, channel: Int = 0, transform: (Node) -> Node)

    /** Removes a node from the in-memory database by its number. */
    fun removeByNodenum(nodeNum: Int)

    /**
     * Installs node information in memory. Callers that require an immediate standalone write use
     * [installNodeInfoAndPersist]; configuration handshakes batch the resulting snapshot through the repository.
     */
    fun installNodeInfo(info: ProtoNodeInfo)

    /** Installs node information and awaits any required persistence. */
    suspend fun installNodeInfoAndPersist(info: ProtoNodeInfo)

    /** Inserts hardware metadata for a node. */
    fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata, session: RadioSessionContext? = null)
}
