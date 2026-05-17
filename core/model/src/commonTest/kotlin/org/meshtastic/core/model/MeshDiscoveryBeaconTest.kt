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
@file:Suppress("MagicNumber")

package org.meshtastic.core.model

import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeshDiscoveryBeaconTest {
    @Test
    fun decode_validPayload() {
        val payload = discoveryPayload()

        val beacon = MeshDiscoveryBeacon.decode(PortNum.PRIVATE_APP.value, payload)

        requireNotNull(beacon)
        assertEquals(0, beacon.version)
        assertEquals(MeshDiscoveryBeacon.RoleHint.WILL_FORWARD, beacon.roleHint)
        assertEquals(MeshDiscoveryBeacon.ForwardingHint.CORE, beacon.forwardingHint)
        assertEquals(915_000, beacon.frequencyKHz)
        assertEquals(915f, beacon.frequencyMHz)
        assertEquals(MeshDiscoveryBeacon.Bandwidth.BW_250, beacon.bandwidth)
        assertEquals(7, beacon.spreadingFactor)
        assertEquals(5, beacon.codingRate)
        assertEquals(0x12345678, beacon.nodeId)
        assertEquals("!12345678", beacon.nodeIdString)
        assertEquals(0x5a, beacon.primaryChannelHash)
        assertEquals("ShortFast", beacon.primaryChannelName)
    }

    @Test
    fun decode_rejectsWrongSize() {
        assertNull(MeshDiscoveryBeacon.decode(byteArrayOf(0).toByteString()))
    }

    @Test
    fun decode_rejectsNonCandidatePort() {
        assertNull(MeshDiscoveryBeacon.decode(PortNum.LORAWAN_BRIDGE.value, discoveryPayload()))
    }

    @Test
    fun decode_acceptsLora24Frequency() {
        val payload = discoveryPayload().toByteArray()
        payload[1] = 0x25
        payload[2] = 0x16
        payload[3] = 0xa0.toByte() // 2430624 kHz

        val beacon = MeshDiscoveryBeacon.decode(PortNum.PRIVATE_APP.value, payload.toByteString())

        requireNotNull(beacon)
        assertEquals(2_430_624, beacon.frequencyKHz)
    }

    @Test
    fun decode_rejectsReservedHeaderBits() {
        val payload = discoveryPayload().toByteArray()
        payload[0] = 0b0001_0000

        assertNull(MeshDiscoveryBeacon.decode(payload.toByteString()))
    }

    @Test
    fun decode_rejectsImplausibleFrequency() {
        val payload = discoveryPayload().toByteArray()
        payload[1] = 0
        payload[2] = 0
        payload[3] = 1

        assertNull(MeshDiscoveryBeacon.decode(payload.toByteString()))
    }

    @Test
    fun decode_rejectsNonAsciiChannelName() {
        val payload = discoveryPayload().toByteArray()
        payload[10] = 0x01

        assertNull(MeshDiscoveryBeacon.decode(payload.toByteString()))
    }

    private fun discoveryPayload(): okio.ByteString {
        val payload = ByteArray(MeshDiscoveryBeacon.ENCODED_SIZE)
        payload[0] = 0b0000_0101 // version 0, role WILL_FORWARD, forwarding CORE
        payload[1] = 0x0d
        payload[2] = 0xf6.toByte()
        payload[3] = 0x38 // 915000 kHz
        payload[4] = 0b0110_1000 // 250 kHz, SF7, CR 4/5
        payload[5] = 0x12
        payload[6] = 0x34
        payload[7] = 0x56
        payload[8] = 0x78
        payload[9] = 0x5a
        "ShortFast".encodeToByteArray().copyInto(payload, destinationOffset = 10)
        return payload.toByteString()
    }
}
