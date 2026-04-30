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
import org.meshtastic.core.model.NodeInfo
import org.meshtastic.core.model.util.NodeIdLookup
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.StatusMessage
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import org.meshtastic.proto.NodeInfo as ProtoNodeInfo
import org.meshtastic.proto.Position as ProtoPosition

/** Interface for managing the in-memory node database and processing received node information. */
@Suppress("TooManyFunctions")
interface NodeManager : NodeIdLookup {
    /** Reactive map of all nodes by their number. */
    val nodeDBbyNodeNum: Map<Int, Node>

    /** Reactive map of all nodes by their ID string. */
    val nodeDBbyID: Map<String, Node>

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

    /** Loads the cached node database from the repository. */
    fun loadCachedNodeDB()

    /** Clears the in-memory node database. */
    fun clear()

    /** Returns information about the local node. */
    fun getMyNodeInfo(): MyNodeInfo?

    /** Returns the local node ID. */
    fun getMyId(): String

    /** Returns a list of all known nodes. */
    fun getNodes(): List<NodeInfo>

    /** Processes a received user packet. */
    fun handleReceivedUser(fromNum: Int, p: User, channel: Int = 0, manuallyVerified: Boolean = false)

    /** Processes a received position packet. */
    fun handleReceivedPosition(fromNum: Int, myNodeNum: Int, p: ProtoPosition, defaultTime: Long)

    /** Processes a received telemetry packet. */
    fun handleReceivedTelemetry(fromNum: Int, telemetry: Telemetry)

    /** Processes a received paxcounter packet. */
    fun handleReceivedPaxcounter(fromNum: Int, p: Paxcount)

    /** Processes a received node status message. */
    fun handleReceivedNodeStatus(fromNum: Int, s: StatusMessage)

    /** Updates the status string for a node. */
    fun updateNodeStatus(nodeNum: Int, status: String?)

    /** Updates a node using a transformation function. */
    fun updateNode(nodeNum: Int, withBroadcast: Boolean = true, channel: Int = 0, transform: (Node) -> Node)

    /** Removes a node from the in-memory database by its number. */
    fun removeByNodenum(nodeNum: Int)

    /** Installs node information from a ProtoNodeInfo object. */
    fun installNodeInfo(info: ProtoNodeInfo, withBroadcast: Boolean = true)

    /** Inserts hardware metadata for a node. */
    fun insertMetadata(nodeNum: Int, metadata: DeviceMetadata)
}
