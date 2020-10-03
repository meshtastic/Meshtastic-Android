package com.geeksville.mesh

import com.geeksville.mesh.android.nowInSeconds
import com.geeksville.mesh.service.MeshService

fun MeshProtos.NodeInfo.toMeshUser() = MeshUser(
    user.id,
    user.longName,
    user.shortName
)

val MeshProtos.MeshPacket.rxTimeOrNow get() = if (rxTime != 0) rxTime else nowInSeconds().toInt()

/// Generate a new mesh packet builder with our node as the sender, and the specified node num
fun newMeshPacketBuilderTo(myNodeInfo: MyNodeInfo, myNodeNum: Int, receiverIdNum: Int): MeshProtos.MeshPacket.Builder
    = MeshProtos.MeshPacket.newBuilder().apply {
    val useShortAddresses = myNodeInfo?.nodeNumBits != 32
    from = myNodeNum
    // We might need to change broadcast addresses to work with old device loads
    to = if (useShortAddresses && receiverIdNum == MeshService.NODENUM_BROADCAST) 255 else receiverIdNum
}
