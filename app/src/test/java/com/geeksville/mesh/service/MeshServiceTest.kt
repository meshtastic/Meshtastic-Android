package com.geeksville.mesh.service

import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.Position
import org.junit.Assert
import org.junit.Test


class MeshServiceTest {
    val model = MeshProtos.HardwareModel.ANDROID_SIM
    val nodeInfo = NodeInfo(4, MeshUser("+one", "User One", "U1", model), Position(37.1, 121.1, 35, 10))

    @Test
    fun givenNodeInfo_whenUpdatingWithNewTime_thenPositionTimeIsUpdated() {

        val newerTime = 20
        updateNodeInfoTime(nodeInfo, newerTime)
        Assert.assertEquals(newerTime, nodeInfo.lastHeard)
    }
}


