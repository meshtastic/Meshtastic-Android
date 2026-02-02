/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.model

import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel
import java.util.Locale

class NodeInfoTest {
    private val model = HardwareModel.ANDROID_SIM
    private val node =
        listOf(
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
        Assert.assertEquals(node[1].distance(node[4]), 1779)
    }

    @Test
    fun distanceStrGood() {
        Assert.assertEquals(node[1].distanceStr(node[2], Config.DisplayConfig.DisplayUnits.METRIC.value), "1.1 km")
        Assert.assertEquals(node[1].distanceStr(node[3], Config.DisplayConfig.DisplayUnits.METRIC.value), "111 m")
        Assert.assertEquals(node[1].distanceStr(node[4], Config.DisplayConfig.DisplayUnits.IMPERIAL.value), "1.1 mi")
        Assert.assertEquals(node[1].distanceStr(node[3], Config.DisplayConfig.DisplayUnits.IMPERIAL.value), "364 ft")
    }
}
