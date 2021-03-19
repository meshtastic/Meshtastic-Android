package com.geeksville.mesh

import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class NodeInfoTest {
    val model = MeshProtos.HardwareModel.ANDROID_SIM
    val ni1 = NodeInfo(4, MeshUser("+one", "User One", "U1", model), Position(37.1, 121.1, 35))
    val ni2 = NodeInfo(5, MeshUser("+two", "User Two", "U2", model), Position(37.11, 121.1, 40))
    val ni3 = NodeInfo(6, MeshUser("+three", "User Three", "U3", model), Position(37.101, 121.1, 40))

    private val currentDefaultLocale = LocaleListCompat.getDefault().get(0)

    @Before
    fun setup()
    {
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(currentDefaultLocale)
    }

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
