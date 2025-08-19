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

import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.database.entity.MyNodeEntity
import com.geeksville.mesh.database.entity.NodeEntity
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveNodeData @Inject constructor(private val radioConfigRepository: RadioConfigRepository) {
    // True after we've done our initial node db init
    @Volatile var haveNodeDb = false

    var myNodeInfo: MyNodeEntity? = null

    val myNodeNum
        get() = myNodeInfo?.myNodeNum ?: throw RadioNotConnectedException("We don't yet have our myNodeInfo")

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

    fun getOrPut(num: Int, node: () -> NodeEntity): NodeEntity = nodeDbByNodeNum.getOrPut(num) { node() }

    fun getById(id: String?) = nodeDbById[id]

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
}
