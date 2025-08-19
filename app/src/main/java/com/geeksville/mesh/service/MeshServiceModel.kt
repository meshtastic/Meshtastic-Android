/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.service

import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import com.geeksville.mesh.LocalOnlyProtos.LocalModuleConfig
import com.geeksville.mesh.MeshProtos.MeshPacket
import com.geeksville.mesh.ModuleConfigProtos
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.service.MeshService.Companion.IdNotFoundException
import com.geeksville.mesh.service.MeshService.Companion.InvalidNodeIdException
import com.geeksville.mesh.service.MeshService.Companion.NodeNumNotFoundException
import com.google.protobuf.ByteString
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("TooManyFunctions")
@Singleton
class MeshServiceModel @Inject constructor(private val radioConfigRepository: RadioConfigRepository) {
    private val hexIdRegex = """\!([0-9A-Fa-f]+)""".toRegex()

    val configTotal by lazy { ConfigProtos.Config.getDescriptor().fields.size }
    val moduleTotal by lazy { ModuleConfigProtos.ModuleConfig.getDescriptor().fields.size }
    var sessionPasskey: ByteString = ByteString.EMPTY

    var localConfig: LocalConfig = LocalConfig.getDefaultInstance()
    var moduleConfig: LocalModuleConfig = LocalModuleConfig.getDefaultInstance()
    var channelSet: AppOnlyProtos.ChannelSet = AppOnlyProtos.ChannelSet.getDefaultInstance()

    // True after we've done our initial node db init
    @Volatile var haveNodeDb = false

    var myNodeInfo: MyNodeEntity? = null

    val myNodeNum
        get() = myNodeInfo?.myNodeNum ?: throw RadioNotConnectedException("We don't yet have our myNodeInfo")

    val myNodeId
        get() = idFromNum(myNodeNum)

    // The database of active nodes, index is the node number
    private val nodeDbByNodeNum = ConcurrentHashMap<Int, NodeEntity>()

    val values
        get() = nodeDbByNodeNum.values

    val count
        get() = nodeDbByNodeNum.size

    /** How many nodes are currently online (including our local node) */
    val onlineCount
        get() = nodeDbByNodeNum.values.count { it.isOnline }

    // The database of active nodes, index is the node user ID string
    // NOTE: some NodeInfos might be in only nodeDBbyNodeNum (because we don't yet know an ID).
    private val nodeDbById
        get() = nodeDbByNodeNum.mapKeys { it.value.user.id }

    fun getByNum(num: Int) = nodeDbByNodeNum[num]

    @Throws(NodeNumNotFoundException::class)
    fun getByNumOrThrow(num: Int) = getByNum(num) ?: throw NodeNumNotFoundException(num)

    fun getOrPut(num: Int, node: () -> NodeEntity): NodeEntity = nodeDbByNodeNum.getOrPut(num) { node() }

    fun getById(id: String?) = nodeDbById[id]

    /**
     * Map a userid to a node/ node num, or throw an exception if not found We prefer to find nodes based on their
     * assigned IDs, but if no ID has been assigned to a node, we can also find it based on node number
     */
    @Throws(NodeNumNotFoundException::class, IdNotFoundException::class, InvalidNodeIdException::class)
    fun getByIdOrThrow(id: String): NodeEntity {
        // If this is a valid hexaddr will be !null
        val hexStr = hexIdRegex.matchEntire(id)?.groups?.get(1)?.value

        return getById(id)
            ?: when {
                id == DataPacket.ID_LOCAL -> getByNumOrThrow(myNodeNum)
                hexStr != null -> {
                    @Suppress("MagicNumber")
                    val n = hexStr.toLong(16).toInt()
                    getByNum(n) ?: throw IdNotFoundException(id)
                }

                else -> throw InvalidNodeIdException(id)
            }
    }

    /**
     * Map a nodeNum to the nodeId string If we have a NodeInfo for this ID we prefer to return the string ID inside the
     * user record. but some nodes might not have a user record at all (because not yet received), in that case, we
     * return a hex version of the ID just based on the number
     */
    fun idFromNum(num: Int): String = if (num == DataPacket.NODENUM_BROADCAST) {
        DataPacket.ID_BROADCAST
    } else {
        getByNum(num)?.user?.id ?: DataPacket.nodeNumToDefaultId(num)
    }

    fun numFromId(id: String): Int = when (id) {
        DataPacket.ID_BROADCAST -> DataPacket.NODENUM_BROADCAST
        DataPacket.ID_LOCAL -> myNodeNum
        else -> getByIdOrThrow(id).num
    }

    fun remove(num: Int) {
        nodeDbByNodeNum.remove(num)
    }

    suspend fun refreshNodeDb() {
        discardNodeDb() // Get rid of any old state
        myNodeInfo = radioConfigRepository.myNodeInfo.value
        nodeDbByNodeNum.putAll(radioConfigRepository.getNodeDBbyNum())
        // Note: we do not haveNodeDB = true because that means we've got a valid db from a real
        // device (rather than
        // this possibly stale hint)
    }

    /** discard entire node db & message state - used when downloading a new db from the device */
    fun discardNodeDb() {
        debug("Discarding NodeDB")
        myNodeInfo = null
        nodeDbByNodeNum.clear()
        haveNodeDb = false
    }

    /**
     * Generate a new mesh packet builder with our node as the sender, and the specified recipient
     *
     * If id is null we assume a broadcast message
     */
    fun newMeshPacketToId(id: String): MeshPacket.Builder = newMeshPacketToNum(numFromId(id))

    /** Generate a new mesh packet builder with our node as the sender, and the specified node num */
    fun newMeshPacketToNum(num: Int): MeshPacket.Builder = MeshPacket.newBuilder().apply {
        if (myNodeInfo == null) {
            throw RadioNotConnectedException()
        }

        from = 0 // don't add myNodeNum

        to = num
    }
}
