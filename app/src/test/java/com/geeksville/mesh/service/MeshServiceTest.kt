package com.geeksville.mesh.service

import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.Position
import org.junit.Assert
import org.junit.Test


class MeshServiceTest {

    val nodeInfo = NodeInfo(4, MeshUser("+one", "User One", "U1"), Position(37.1, 121.1, 35, 10))

    @Test
    fun givenNodeInfo_whenUpdatingWithNewTime_thenPositionTimeIsUpdated() {

        val newerTime = 20
        updateNodeInfoTime(nodeInfo, newerTime)
        Assert.assertEquals(newerTime, nodeInfo.position?.time)
    }

    @Test
    fun givenNodeInfo_whenUpdatingWithOldTime_thenPositionTimeIsNotUpdated() {
        val olderTime = 5
        val timeBeforeTryingToUpdate = nodeInfo.position?.time
        updateNodeInfoTime(nodeInfo, olderTime)
        Assert.assertEquals(timeBeforeTryingToUpdate, nodeInfo.position?.time)
    }
}


