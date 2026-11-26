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
package org.meshtastic.feature.connections.domain.usecase

import io.kotest.matchers.shouldBe
import org.meshtastic.core.common.util.normalizeAddress
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.UsbDeviceData
import kotlin.test.Test

/**
 * The issue #8228 scenario: the paired radio SD7T has a MAC ending `96:4e`, so firmware advertises it as `SD7T_964e`
 * (`getDeviceName` appends MAC bytes 4 and 5). On firmware 2.8 its node number is `crc32(public_key)`, unrelated to the
 * MAC, while another node on the mesh, ptzm, happens to have an id ending in `964e`.
 */
class LocalNodeNumsTest {
    private val sd7tMac = "C8:2B:96:01:96:4E"
    private val sd7t = TestDataFactory.createTestNode(num = 0x1A2B3C4D, userId = "!1a2b3c4d", longName = "SD7T")
    private val ptzm = TestDataFactory.createTestNode(num = 0x5E6F964E, userId = "!5e6f964e", longName = "ptzm")
    private val nodeDb = mapOf(sd7t.num to sd7t, ptzm.num to ptzm)
    private val sd7tEntry = DeviceListEntry.Ble(FakeBleDevice(address = sd7tMac, name = "SD7T_964e"))

    @Test
    fun `paired radio whose name suffix matches another node id shows its own recorded node`() {
        val nums = LocalNodeNums(recorded = mapOf(normalizeAddress(sd7tEntry.fullAddress) to sd7t.num), live = null)

        listOf(sd7tEntry).withLocalNodes(nodeDb, nums).single().node shouldBe sd7t
    }

    @Test
    fun `paired radio with no recorded node number shows no node even when another node id matches its suffix`() {
        val nums = LocalNodeNums(recorded = emptyMap(), live = null)

        listOf(sd7tEntry).withLocalNodes(nodeDb, nums).single().node shouldBe null
    }

    @Test
    fun `connected radio shows the node number of its live handshake over the recorded one`() {
        // Before 2.8 the node number was MAC bytes 2 to 5; the upgrade renumbered SD7T to crc32 of its key.
        val preUpgrade = TestDataFactory.createTestNode(num = 0x9601964E.toInt(), userId = "!9601964e")
        val db = nodeDb + (preUpgrade.num to preUpgrade)
        val nums =
            LocalNodeNums(
                recorded = mapOf(normalizeAddress(sd7tEntry.fullAddress) to preUpgrade.num),
                live = ConnectionIdentity(1L, sd7tEntry.fullAddress, sd7t.num, deviceId = null),
            )

        listOf(sd7tEntry).withLocalNodes(db, nums).single().node shouldBe sd7t
    }

    @Test
    fun `live handshake labels only the connected radio`() {
        val other = TestDataFactory.createTestNode(num = 0x0A0B0C0D, userId = "!0a0b0c0d", longName = "Other")
        val otherEntry = DeviceListEntry.Ble(FakeBleDevice(address = "11:22:33:44:55:66", name = "OTHR_5566"))
        val nums =
            LocalNodeNums(
                recorded = mapOf(normalizeAddress(otherEntry.fullAddress) to other.num),
                live = ConnectionIdentity(1L, sd7tEntry.fullAddress, sd7t.num, deviceId = null),
            )

        val labelled = listOf(sd7tEntry, otherEntry).withLocalNodes(nodeDb + (other.num to other), nums)

        labelled.map { it.node } shouldBe listOf(sd7t, other)
    }

    @Test
    fun `serial entry whose address names one radio shows its recorded node`() {
        val entry = usbEntry("s/dev/serial/by-id/usb-RAKwireless_WisCore_RAK4631_Board_7885014CB2C5B186-if00", true)
        val nums = LocalNodeNums(recorded = mapOf(normalizeAddress(entry.fullAddress) to sd7t.num), live = null)

        listOf(entry).withLocalNodes(nodeDb, nums).single().node shouldBe sd7t
    }

    @Test
    fun `serial entry whose address names only a port or model shows no recorded node`() {
        val entry = usbEntry("s/dev/serial/by-id/usb-1a86_USB_Serial-if00-port0", false)
        val nums = LocalNodeNums(recorded = mapOf(normalizeAddress(entry.fullAddress) to sd7t.num), live = null)

        listOf(entry).withLocalNodes(nodeDb, nums).single().node shouldBe null
    }

    private fun usbEntry(fullAddress: String, addressNamesRadio: Boolean) = DeviceListEntry.Usb(
        usbData = object : UsbDeviceData {},
        name = "ttyACM3",
        fullAddress = fullAddress,
        bonded = true,
        addressNamesRadio = addressNamesRadio,
    )
}
