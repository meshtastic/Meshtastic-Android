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
package org.meshtastic.feature.connections.model

import org.meshtastic.feature.connections.model.host.Bearer
import org.meshtastic.feature.connections.model.host.ExtraRadioDevice
import org.meshtastic.feature.connections.model.host.NodePhase
import org.meshtastic.feature.connections.model.host.SensorKind
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class DashboardNodeStatusTest {

    @Test
    fun theHeaderReadsTheStatusBody() {
        val ui = dashboardNodeStatus(STATUS, bearers = null, sensors = null, nowMs = NOW)!!

        assertEquals(NodePhase.Running, ui.phase)
        assertEquals("!58c43294", ui.nodeId)
        assertEquals("Dash Node", ui.longName)
        assertEquals(2, ui.peerCount)
        assertNull(ui.sensors)
    }

    @Test
    fun aBodyWithNoPhaseIsNoCard() {
        assertNull(dashboardNodeStatus("{\"id\":\"!1\"}", bearers = null, sensors = null, nowMs = NOW))
        assertNull(dashboardNodeStatus("not json", bearers = null, sensors = null, nowMs = NOW))
    }

    @Test
    fun bearersFollowTheCardOrderAndLeaveMqttOut() {
        val ui = dashboardNodeStatus(STATUS, BEARERS, sensors = null, nowMs = NOW)!!

        assertEquals(listOf(Bearer.UDP, Bearer.LORA, Bearer.LORA, Bearer.VIDEO), ui.bearers.map { it.bearer })
        val udp = ui.bearers.first()
        assertEquals(BearerRowState.Active, udp.state)
        assertEquals(12, udp.received)
        assertEquals(BearerRowState.RegionUnset, ui.bearers[1].state)
        assertEquals(BearerRowState.NeedsHardware("a camera"), ui.bearers.last().state)
    }

    @Test
    fun anExtraRadioCarriesWhatTheHostSaysItIsAndItsOwnSwitch() {
        val ui = dashboardNodeStatus(STATUS, BEARERS, sensors = null, nowMs = NOW)!!

        val extra = ui.bearers[2].extra!!
        assertEquals(2, extra.number)
        assertEquals(ExtraRadioDevice.RTL_SDR, extra.details?.device)
        assertEquals(Config.LoRaConfig.ModemPreset.MEDIUM_FAST, extra.details?.preset)
        assertEquals(false, extra.switchedOn)
        assertEquals(false, extra.loraOn)
        assertEquals("disk full", ui.extraRadioFailure)
    }

    @Test
    fun sensorsMapTheBusAndEachDevice() {
        val ui = dashboardNodeStatus(STATUS, bearers = null, SENSORS, nowMs = NOW)!!

        val found = assertIs<SensorsUi.Found>(ui.sensors)
        val bme = found.sensors.single()
        assertEquals("BME280", bme.name)
        assertEquals("0x76", bme.address)
        assertEquals(listOf(SensorKind.ENVIRONMENT), bme.feeds)
        assertEquals(SensorRowState.Reading(3.minutes), bme.state)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L

        const val STATUS =
            """{"phase":"Running","num":1489253012,"id":"!58c43294","longName":"Dash Node","shortName":"DASH",
            "peerCount":2,"failure":null,"settingsReset":false}"""

        const val BEARERS =
            """{"items":[
              {"name":"udp","enabled":true,"availability":"ACTIVE","availabilityReason":null,
               "requirement":{"state":"READY"},"counters":{"rx":12,"tx":3,"relayed":1,"failures":0}},
              {"name":"lora","enabled":false,"availability":"READY","availabilityReason":null,
               "requirement":{"state":"READY"},"counters":{"rx":0,"tx":0,"relayed":0,"failures":0}},
              {"name":"mqtt","enabled":true,"availability":"ACTIVE","availabilityReason":null,
               "requirement":{"state":"READY"},"counters":{"rx":0,"tx":0,"relayed":0,"failures":0}},
              {"name":"video","enabled":false,"availability":null,"availabilityReason":null,
               "requirement":{"state":"NEEDS_HARDWARE","detail":"a camera"},
               "counters":{"rx":0,"tx":0,"relayed":0,"failures":0}}
            ],
            "extraRadios":[
              {"name":"lora-2","enabled":false,"availability":"READY","availabilityReason":null,
               "requirement":{"state":"READY"},"counters":{"rx":4,"tx":0,"relayed":0,"failures":0},
               "switchedOn":false,
               "radio":{"device":"RTL_SDR","id":null,"preset":"MEDIUM_FAST","bridge":true,"duplex":false}}
            ],
            "extraRadioFailure":"disk full"}"""

        const val SENSORS =
            """{"bus":"SCANNED","failure":null,"items":[
              {"address":118,"name":"BME280","sensor":"BME280","feeds":["environment"],"state":"READING",
               "error":null,"pollError":null,"unread":null,
               "reads":{"environment":{"ok":true,"error":null,"succeeded":10,"failed":0,
               "lastSuccessAtMs":1799999820000}}}
            ]}"""
    }
}
