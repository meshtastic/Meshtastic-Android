/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh

import androidx.core.os.LocaleListCompat
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*

class NodeInfoTest {
    private val model = MeshProtos.HardwareModel.ANDROID_SIM
    private val node = listOf(
        NodeInfo(4, MeshUser("+zero", "User Zero", "U0", model)),
        NodeInfo(5, MeshUser("+one", "User One", "U1", model), Position(37.1, 121.1, 35)),
        NodeInfo(6, MeshUser("+two", "User Two", "U2", model), Position(37.11, 121.1, 40)),
        NodeInfo(7, MeshUser("+three", "User Three", "U3", model), Position(37.101, 121.1, 40)),
        NodeInfo(8, MeshUser("+four", "User Four", "U4", model), Position(37.116, 121.1, 40)),
    )

    private val currentDefaultLocale = LocaleListCompat.getDefault().get(0) ?: Locale.US

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(currentDefaultLocale)
    }

    @Test
    fun distanceGood() {
        Assert.assertEquals(node[1].distance(node[2]), 1111)
        Assert.assertEquals(node[1].distance(node[3]), 111)
        Assert.assertEquals(node[1].distance(node[4]), 1777)
    }

    @Test
    fun distanceStrGood() {
        Assert.assertEquals(node[1].distanceStr(node[2], DisplayUnits.METRIC_VALUE), "1.1 km")
        Assert.assertEquals(node[1].distanceStr(node[3], DisplayUnits.METRIC_VALUE), "111 m")
        Assert.assertEquals(node[1].distanceStr(node[4], DisplayUnits.IMPERIAL_VALUE), "1.1 mi")
        Assert.assertEquals(node[1].distanceStr(node[3], DisplayUnits.IMPERIAL_VALUE), "364 ft")
    }
}
