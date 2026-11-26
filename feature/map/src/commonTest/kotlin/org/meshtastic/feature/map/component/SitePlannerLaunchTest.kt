/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.map.component

import org.meshtastic.core.model.Node
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals

class SitePlannerLaunchTest {

    @Test
    fun `node detail launch retains selected node while manual launch follows connected node`() {
        val connectedNode = positionedNode(num = 11, latitudeI = 10_000_000, longitudeI = 20_000_000)
        val selectedNode = positionedNode(num = 22, latitudeI = 30_000_000, longitudeI = 40_000_000)
        val updatedConnectedNode = positionedNode(num = 11, latitudeI = 50_000_000, longitudeI = 60_000_000)

        val routeLaunch =
            SitePlannerLaunch(
                initialParams = selectedNode.toSitePlannerParams(channelSet = null),
                selectedNode = selectedNode,
            )
        val manualLaunch = SitePlannerLaunch(initialParams = connectedNode.toSitePlannerParams(channelSet = null))

        assertEquals(selectedNode.latitude to selectedNode.longitude, routeLaunch.nodeLocation(connectedNode))
        assertEquals(
            updatedConnectedNode.latitude to updatedConnectedNode.longitude,
            manualLaunch.nodeLocation(updatedConnectedNode),
        )
    }

    @Test
    fun `jp site planner frequency follows connected radio firmware version`() {
        val jpNarrowSlowSlot2 = jpChannelSet(ModemPreset.NARROW_SLOW, channelNum = 2)
        val legacyRadio = positionedNode(num = 1, latitudeI = 356_812_360, longitudeI = 1_397_671_250, fw = "2.7.26")
        val aribRadio = positionedNode(num = 2, latitudeI = 356_812_360, longitudeI = 1_397_671_250, fw = "2.8.3")

        assertEquals(
            920.59375,
            legacyRadio.toSitePlannerParams(jpNarrowSlowSlot2, radio = legacyRadio).txFreqMhz,
            0.001,
        )
        assertEquals(920.8, aribRadio.toSitePlannerParams(jpNarrowSlowSlot2, radio = aribRadio).txFreqMhz, 0.001)
        assertEquals(920.59375, legacyRadio.toSitePlannerParams(jpNarrowSlowSlot2, radio = null).txFreqMhz, 0.001)
    }

    @Test
    fun `remote subject site planner uses connected radio firmware rather than subject firmware`() {
        val jpNarrowSlowSlot2 = jpChannelSet(ModemPreset.NARROW_SLOW, channelNum = 2)
        val legacyNode = positionedNode(num = 1, latitudeI = 356_812_360, longitudeI = 1_397_671_250, fw = "2.7.26")
        val aribNode = positionedNode(num = 2, latitudeI = 356_812_360, longitudeI = 1_397_671_250, fw = "2.8.3")

        assertEquals(920.8, legacyNode.toSitePlannerParams(jpNarrowSlowSlot2, radio = aribNode).txFreqMhz, 0.001)
        assertEquals(920.59375, aribNode.toSitePlannerParams(jpNarrowSlowSlot2, radio = legacyNode).txFreqMhz, 0.001)
    }

    private fun jpChannelSet(preset: ModemPreset, channelNum: Int): ChannelSet = ChannelSet.Builder()
        .also { wb ->
            wb.settings = listOf(ChannelSettings.Builder().build())
            wb.lora_config =
                LoRaConfig.Builder()
                    .also { lb ->
                        lb.region = RegionCode.JP
                        lb.use_preset = true
                        lb.modem_preset = preset
                        lb.channel_num = channelNum
                    }
                    .build()
        }
        .build()

    private fun positionedNode(num: Int, latitudeI: Int, longitudeI: Int, fw: String? = null): Node = Node(
        num = num,
        metadata = fw?.let { DeviceMetadata.Builder().also { wb -> wb.firmware_version = it }.build() },
        position =
        Position.Builder()
            .also { wb ->
                wb.latitude_i = latitudeI
                wb.longitude_i = longitudeI
            }
            .build(),
    )
}
