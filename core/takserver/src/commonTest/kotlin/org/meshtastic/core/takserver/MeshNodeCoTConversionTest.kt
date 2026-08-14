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
package org.meshtastic.core.takserver

import org.meshtastic.core.model.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class MeshNodeCoTConversionTest {

    @Test
    fun `eligible node passes the filter`() {
        assertTrue(meshNode().isEligibleForCot(ourNodeNum = OTHER_NODE_NUM, lastHeardThreshold = STALE_THRESHOLD))
    }

    @Test
    fun `own node is excluded`() {
        assertFalse(meshNode().isEligibleForCot(ourNodeNum = NODE_NUM, lastHeardThreshold = STALE_THRESHOLD))
    }

    @Test
    fun `node without user info is excluded`() {
        val node = meshNode(hwModel = org.meshtastic.proto.HardwareModel.UNSET)
        assertFalse(node.isEligibleForCot(ourNodeNum = OTHER_NODE_NUM, lastHeardThreshold = STALE_THRESHOLD))
    }

    @Test
    fun `node heard outside the window is excluded`() {
        val node = meshNode(lastHeard = STALE_THRESHOLD - 1)
        assertFalse(node.isEligibleForCot(ourNodeNum = OTHER_NODE_NUM, lastHeardThreshold = STALE_THRESHOLD))
    }

    @Test
    fun `node without a valid position is excluded`() {
        val node = meshNode(latitudeI = 0, longitudeI = 0)
        assertFalse(node.isEligibleForCot(ourNodeNum = OTHER_NODE_NUM, lastHeardThreshold = STALE_THRESHOLD))
    }

    @Test
    fun `uid is derived from the node number and stays stable`() {
        // ATAK keys a contact by uid; a uid that drifts spawns duplicate markers.
        assertEquals("MESHTASTIC-A1B2C3D4", meshNode().cotUid())
        assertEquals(meshNode().cotUid(), meshNode(shortName = "DIFF", longName = "Renamed").cotUid())
    }

    @Test
    fun `uid hex is upper-case and zero-padded to match Apple`() {
        // Apple formats "MESHTASTIC-%08X". ATAK compares uids case-sensitively, so lower-case hex
        // would make an Android-bridged node a separate contact from the same iOS-bridged node.
        val uid = meshNode().cotUid()
        assertEquals(uid.uppercase(), uid)

        // Low node number must keep its leading zeros rather than collapsing to "MESHTASTIC-2A".
        val lowNum = Node(num = 0x2a, user = user(), position = position(), lastHeard = RECENT_LAST_HEARD)
        assertEquals("MESHTASTIC-0000002A", lowNum.cotUid())
    }

    @Test
    fun `callsign is short and long name joined`() {
        assertEquals("WOLF - Wolf Ridge Relay", meshNode().cotCallsign())
    }

    @Test
    fun `callsign falls back when a name is missing`() {
        assertEquals("WOLF", meshNode(longName = "").cotCallsign())
        assertEquals("Wolf Ridge Relay", meshNode(shortName = "").cotCallsign())
        assertEquals("!a1b2c3d4", meshNode(shortName = "", longName = "").cotCallsign())
    }

    @Test
    fun `remarks carry the reported telemetry`() {
        // Labels, order, and precision match Apple's createCoTFromNode (voltage at two decimals,
        // everything else at one) so remarks read identically whichever phone bridged the node.
        val remarks = meshNode(voltage = 3.95f).cotRemarks()
        assertEquals(
            "Battery: 76% | Voltage: 3.95V | Chan Util: 12.5% | Air Util Tx: 4.2% | RSSI: -92 dBm | SNR: 8.5 dB",
            remarks,
        )
    }

    @Test
    fun `remarks omit unreported telemetry rather than reporting zero`() {
        // A missing reading must not render as "0" — zero is a real value for every one of these.
        val node = Node(num = NODE_NUM, user = user(), position = position(), lastHeard = RECENT_LAST_HEARD)
        assertNull(node.cotRemarks())
    }

    @Test
    fun `zero is preserved as a real telemetry reading`() {
        val node = meshNode(batteryLevel = 0, voltage = 0f, channelUtilization = 0f, airUtilTx = 0f, snr = 0f, rssi = 0)
        // Apple suppresses each of these at zero; this repo reports them, since 0 dB SNR / 0 dBm
        // RSSI are real measurements and 0% battery is the value an operator most needs to see.
        val remarks = node.cotRemarks()
        assertTrue(remarks!!.contains("Battery: 0%"), remarks)
        assertTrue(remarks.contains("Voltage: 0.00V"), remarks)
        assertTrue(remarks.contains("Chan Util: 0.0%"), remarks)
        assertTrue(remarks.contains("Air Util Tx: 0.0%"), remarks)
        assertTrue(remarks.contains("RSSI: 0 dBm"), remarks)
        assertTrue(remarks.contains("SNR: 0.0 dB"), remarks)
    }

    @Test
    fun `negative fractional readings keep their sign`() {
        // -5 / 10 truncates to 0 in integer division; naive formatting rendered -0.5 as "0.5".
        val remarks = meshNode(snr = -0.5f).cotRemarks()
        assertTrue(remarks!!.contains("SNR: -0.5 dB"), remarks)

        val belowMinusOne = meshNode(snr = -12.5f).cotRemarks()
        assertTrue(belowMinusOne!!.contains("SNR: -12.5 dB"), belowMinusOne)
    }

    @Test
    fun `cot event matches the cross-platform contract`() {
        val cot = meshNode().toCoTMessage()

        assertEquals("MESHTASTIC-A1B2C3D4", cot.uid)
        assertEquals(DEFAULT_PLI_COT_TYPE, cot.type)
        assertEquals("WOLF - Wolf Ridge Relay", cot.contact?.callsign)
        assertEquals(MESH_NODE_TAK_TEAM, cot.group?.name)
        assertEquals(DEFAULT_TAK_ROLE_NAME, cot.group?.role)
        assertEquals(76, cot.status?.battery)
        assertEquals(MESH_NODE_STALE_MINUTES.minutes, cot.stale - cot.time)
        assertEquals(37.7749, cot.latitude, absoluteTolerance = 1e-6)
        assertEquals(-122.4194, cot.longitude, absoluteTolerance = 1e-6)
    }

    private companion object {
        const val NODE_NUM = 0xa1b2c3d4.toInt()
        const val OTHER_NODE_NUM = 0x11111111
        const val STALE_THRESHOLD = 1_000_000
        const val RECENT_LAST_HEARD = STALE_THRESHOLD + 1_000

        fun user(
            shortName: String = "WOLF",
            longName: String = "Wolf Ridge Relay",
            hwModel: org.meshtastic.proto.HardwareModel = org.meshtastic.proto.HardwareModel.TBEAM,
        ) = org.meshtastic.proto.User(
            id = "!a1b2c3d4",
            short_name = shortName,
            long_name = longName,
            hw_model = hwModel,
        )

        fun position(latitudeI: Int = 377_749_000, longitudeI: Int = -1_224_194_000) =
            org.meshtastic.proto.Position(latitude_i = latitudeI, longitude_i = longitudeI)

        @Suppress("LongParameterList")
        fun meshNode(
            shortName: String = "WOLF",
            longName: String = "Wolf Ridge Relay",
            hwModel: org.meshtastic.proto.HardwareModel = org.meshtastic.proto.HardwareModel.TBEAM,
            latitudeI: Int = 377_749_000,
            longitudeI: Int = -1_224_194_000,
            lastHeard: Int = RECENT_LAST_HEARD,
            snr: Float = 8.5f,
            rssi: Int = -92,
            // Exact tenths so NumberFormatter's rounding can't introduce ambiguity here —
            // rounding behavior itself belongs to NumberFormatterTest, not this class.
            batteryLevel: Int = 76,
            voltage: Float = 3.9f,
            channelUtilization: Float = 12.5f,
            airUtilTx: Float = 4.2f,
        ) =
            Node(
                num = NODE_NUM,
                user = user(shortName, longName, hwModel),
                position = position(latitudeI, longitudeI),
                lastHeard = lastHeard,
                snr = snr,
                rssi = rssi,
                deviceMetrics =
                org.meshtastic.proto.DeviceMetrics(
                    battery_level = batteryLevel,
                    voltage = voltage,
                    channel_utilization = channelUtilization,
                    air_util_tx = airUtilTx,
                ),
            )
    }
}
