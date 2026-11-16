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
package org.meshtastic.core.model.util

import org.meshtastic.proto.MeshPacket
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignalSurveyTest {

    private val now = 1_700_000_000L

    private fun packet(build: (MeshPacket.Builder) -> Unit = {}) = MeshPacket.Builder()
        .also { wb ->
            wb.rx_time = now.toInt()
            wb.rx_rssi = -90
            wb.transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA
            wb.hop_start = 3
            wb.hop_limit = 3
            build(wb)
        }
        .build()

    @Test
    fun `a packet just heard over LoRa is a live reception`() {
        assertTrue(packet().isLiveLoraReception(now))
    }

    @Test
    fun `a packet heard at the age limit still counts`() {
        assertTrue(packet().isLiveLoraReception(now + SURVEY_MAX_PACKET_AGE_SECONDS))
    }

    @Test
    fun `a packet queued before the phone connected is not live`() {
        assertFalse(packet().isLiveLoraReception(now + SURVEY_MAX_PACKET_AGE_SECONDS + 1))
    }

    @Test
    fun `a packet stamped ahead of the phone is not live`() {
        assertFalse(packet().isLiveLoraReception(now - 1))
    }

    @Test
    fun `a reception from firmware that leaves the transport unset counts`() {
        val unset = packet { it.transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_INTERNAL }
        assertTrue(unset.isLiveLoraReception(now))
    }

    @Test
    fun `a packet with no arrival time is not live`() {
        assertFalse(packet { it.rx_time = 0 }.isLiveLoraReception(now))
    }

    @Test
    fun `a packet with no rssi was not heard by this radio`() {
        assertFalse(packet { it.rx_rssi = null }.isLiveLoraReception(now))
    }

    @Test
    fun `an MQTT packet is not a radio reception`() {
        assertFalse(packet { it.via_mqtt = true }.isLiveLoraReception(now))
    }

    @Test
    fun `a packet that arrived over another transport is not a LoRa reception`() {
        val udp = packet { it.transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_MULTICAST_UDP }
        assertFalse(udp.isLiveLoraReception(now))
    }

    @Test
    fun `a packet that spent a hop was relayed`() {
        assertTrue(packet().isDirectReception())
        assertFalse(packet { it.hop_limit = 2 }.isDirectReception())
    }
}
