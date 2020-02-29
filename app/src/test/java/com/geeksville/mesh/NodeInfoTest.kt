package com.geeksville.mesh

import org.junit.Assert
import org.junit.Test

class NodeInfoTest {
    val ni1 = NodeInfo(4, MeshUser("+one", "User One", "U1"), Position(37.1, 121.1, 35))
    val ni2 = NodeInfo(5, MeshUser("+two", "User Two", "U2"), Position(37.11, 121.1, 40))
    val ni3 = NodeInfo(6, MeshUser("+three", "User Three", "U3"), Position(37.101, 121.1, 40))

    @Test
    fun distanceGood() {
        Assert.assertEquals(ni1.distance(ni2), 1111)
        Assert.assertEquals(ni1.distance(ni3), 111)
    }

    @Test
    fun distanceStrGood() {
        Assert.assertEquals(ni1.distanceStr(ni2), "1.1 km")
        Assert.assertEquals(ni1.distanceStr(ni3), "111 m")
    }
}
