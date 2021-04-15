package com.geeksville.mesh.service

import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MyNodeInfo
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.RadioConfigProtos
import kotlinx.serialization.Serializable

/// Our saved preferences as stored on disk
@Serializable
data class MeshServiceSettingsData(
    val nodeDB: Array<NodeInfo>,
    val myInfo: MyNodeInfo,
    val messages: Array<DataPacket>,
    val regionCode: Int = RadioConfigProtos.RegionCode.Unset_VALUE
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MeshServiceSettingsData

        if (!nodeDB.contentEquals(other.nodeDB)) return false
        if (myInfo != other.myInfo) return false
        if (!messages.contentEquals(other.messages)) return false
        if (regionCode != other.regionCode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nodeDB.contentHashCode()
        result = 31 * result + myInfo.hashCode()
        result = 31 * result + messages.contentHashCode()
        result = 31 * result + regionCode
        return result
    }
}
