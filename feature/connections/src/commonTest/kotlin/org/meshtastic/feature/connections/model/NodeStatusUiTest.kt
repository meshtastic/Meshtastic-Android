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
import org.meshtastic.feature.connections.model.host.ExtraRadio
import org.meshtastic.feature.connections.model.host.ExtraRadioDevice
import org.meshtastic.feature.connections.model.host.NodePhase
import org.meshtastic.node.host.Requirement
import org.meshtastic.node.transport.TransportAvailability
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeStatusUiTest {

    private fun row(
        bearer: Bearer = Bearer.UDP,
        enabled: Boolean = true,
        availability: TransportAvailability? = TransportAvailability.Active,
        requirement: Requirement = Requirement.Ready,
    ) = bearerRow(bearer, enabled, availability, requirement, received = 1, sent = 2, relayed = 3)

    @Test
    fun `every bearer but MQTT carries a switch`() {
        assertTrue(row(Bearer.UDP).switchable)
        assertTrue(row(Bearer.BLE_ADV).switchable)
        assertTrue(row(Bearer.GATT).switchable)
        assertTrue(row(Bearer.WIFI_AWARE).switchable)
        assertTrue(row(Bearer.AUDIO).switchable)
        assertTrue(row(Bearer.VIDEO).switchable)
        assertTrue(row(Bearer.LORA).switchable)
        assertFalse(row(Bearer.MQTT).switchable)
    }

    private fun extra(
        name: String = "lora-2",
        details: ExtraRadio? = ExtraRadio(ExtraRadioDevice.RTL_SDR),
        off: Set<String> = emptySet(),
        loraOn: Boolean = true,
    ): ExtraRadioUi = assertNotNull(extraRadioUi(name, details, off, loraOn))

    private fun extraRow(extra: ExtraRadioUi, enabled: Boolean = true) = bearerRow(
        Bearer.LORA,
        enabled = enabled,
        availability = TransportAvailability.Active,
        requirement = Requirement.Ready,
        received = 4,
        sent = 0,
        relayed = 0,
        extra = extra,
    )

    @Test
    fun `a further LoRa radio is a row of its own with a switch of its own`() {
        val row = extraRow(extra())
        assertEquals(2, row.radio)
        assertEquals("lora-2", row.extra?.name)
        assertTrue(row.switchable)
        assertEquals(BearerRowState.Active, row.state)
        assertNull(row(Bearer.LORA).radio)
        assertNull(extraRadioUi("lora", null, emptySet(), loraOn = true))
    }

    @Test
    fun `every device kind is described with its role and that it only receives`() {
        ExtraRadioDevice.entries.forEach { device ->
            val described = extra(details = ExtraRadio(device))
            assertEquals(device, described.details?.device)
            assertEquals(
                listOf(ExtraRadioPart.KIND, ExtraRadioPart.ROLE, ExtraRadioPart.RECEIVE_ONLY),
                described.parts(),
                "$device",
            )
        }
    }

    @Test
    fun `an id is described only where the host named one`() {
        val named = extra(details = ExtraRadio(ExtraRadioDevice.USB_STICK, id = " 3A7F01 "))
        assertEquals("3A7F01", named.id)
        assertTrue(ExtraRadioPart.ID in named.parts())

        listOf(null, "", "  ").forEach { id ->
            val unnamed = extra(details = ExtraRadio(ExtraRadioDevice.USB_STICK, id = id))
            assertNull(unnamed.id)
            assertFalse(ExtraRadioPart.ID in unnamed.parts())
        }
    }

    @Test
    fun `a mirror and a listening radio differ by their preset`() {
        val mirror = extra(details = ExtraRadio(ExtraRadioDevice.RTL_SDR))
        val listener = extra(details = ExtraRadio(ExtraRadioDevice.RTL_SDR, preset = ModemPreset.SHORT_FAST))
        assertNull(mirror.details?.preset)
        assertEquals(ModemPreset.SHORT_FAST, listener.details?.preset)
        assertTrue(ExtraRadioPart.ROLE in mirror.parts())
        assertTrue(ExtraRadioPart.ROLE in listener.parts())
    }

    @Test
    fun `a duplex radio transmits and a bridging radio says so`() {
        val details =
            ExtraRadio(
                ExtraRadioDevice.USB_STICK,
                id = "3A7F01",
                preset = ModemPreset.SHORT_FAST,
                bridge = true,
                duplex = true,
            )
        assertEquals(
            listOf(
                ExtraRadioPart.KIND,
                ExtraRadioPart.ID,
                ExtraRadioPart.ROLE,
                ExtraRadioPart.TRANSMITS,
                ExtraRadioPart.BRIDGES,
            ),
            extra(details = details).parts(),
        )
        val bridgeOnly =
            extra(details = ExtraRadio(ExtraRadioDevice.BOARD, preset = ModemPreset.SHORT_FAST, bridge = true))
        assertEquals(
            listOf(ExtraRadioPart.KIND, ExtraRadioPart.ROLE, ExtraRadioPart.RECEIVE_ONLY, ExtraRadioPart.BRIDGES),
            bridgeOnly.parts(),
        )
    }

    @Test
    fun `a short row gives up receive only then the id then the bridge and keeps the rest`() {
        val receiver =
            extra(
                details =
                ExtraRadio(
                    ExtraRadioDevice.RTL_SDR,
                    id = "00000001",
                    preset = ModemPreset.SHORT_FAST,
                    bridge = true,
                ),
            )
        assertEquals(3, receiver.droppable)
        assertEquals(
            listOf(ExtraRadioPart.KIND, ExtraRadioPart.ID, ExtraRadioPart.ROLE, ExtraRadioPart.BRIDGES),
            receiver.parts(dropped = 1),
        )
        assertEquals(
            listOf(ExtraRadioPart.KIND, ExtraRadioPart.ROLE, ExtraRadioPart.BRIDGES),
            receiver.parts(dropped = 2),
        )
        assertEquals(listOf(ExtraRadioPart.KIND, ExtraRadioPart.ROLE), receiver.parts(dropped = 3))
        assertEquals(listOf(ExtraRadioPart.KIND, ExtraRadioPart.ROLE), receiver.parts(dropped = 9))

        val transmitter =
            extra(details = ExtraRadio(ExtraRadioDevice.USB_STICK, preset = ModemPreset.SHORT_FAST, duplex = true))
        assertEquals(0, transmitter.droppable)
        assertTrue(ExtraRadioPart.TRANSMITS in transmitter.parts(dropped = 9))
    }

    @Test
    fun `a radio the host does not describe has no description`() {
        val undescribed = extra(details = null)
        assertTrue(undescribed.parts().isEmpty())
        assertEquals(0, undescribed.droppable)
    }

    @Test
    fun `the switch position comes from the off set`() {
        val off = setOf("lora-3")
        assertTrue(extra("lora-2", off = off).switchedOn)
        assertFalse(extra("lora-3", off = off).switchedOn)
    }

    @Test
    fun `an extra radio that is off reads off and never region unset`() {
        val row = extraRow(extra(off = setOf("lora-2")), enabled = false)
        assertEquals(BearerRowState.Off, row.state)
        assertFalse(row.offWithLora, "its own switch already says why")
    }

    @Test
    fun `LoRa off disables the switch and the row says LoRa is why`() {
        val on = extra(loraOn = false)
        assertFalse(on.loraOn)
        assertTrue(on.switchedOn, "the radio's own choice is kept under the master")
        assertTrue(extraRow(on, enabled = false).offWithLora)

        assertFalse(extraRow(extra(off = setOf("lora-2"), loraOn = false), enabled = false).offWithLora)
        assertFalse(extraRow(extra(loraOn = true)).offWithLora)
        assertFalse(row(Bearer.LORA, enabled = false).offWithLora)
    }

    @Test
    fun `the extra radio failure is carried only on a host with extra radios`() {
        fun card(bearers: List<BearerRowUi>) = nodeStatusUi(
            phase = NodePhase.Running,
            nodeNum = 1L,
            longName = null,
            shortName = null,
            peerCount = 0,
            failure = null,
            settingsReset = false,
            bearers = bearers,
            extraRadioFailure = "could not be read: denied",
        )
        assertEquals("could not be read: denied", card(listOf(row(Bearer.LORA), extraRow(extra()))).extraRadioFailure)
        assertNull(card(listOf(row(Bearer.LORA))).extraRadioFailure)
    }

    @Test
    fun `an extra radio is numbered from its transport name`() {
        assertEquals(2, extraRadioNumber("lora-2"))
        assertEquals(12, extraRadioNumber("lora-12"))
        assertEquals(null, extraRadioNumber("lora"))
        assertEquals(null, extraRadioNumber("lora-1"))
        assertEquals(null, extraRadioNumber("lora-x"))
    }

    @Test
    fun `MQTT stays off the card`() {
        assertEquals(Bearer.entries - Bearer.MQTT, Bearer.entries.filter { it.onCard })
    }

    @Test
    fun `a requirement outranks the availability`() {
        val state = row(availability = TransportAvailability.Active, requirement = Requirement.NeedsPermission("mic"))
        assertEquals(BearerRowState.NeedsPermission("mic"), state.state)
        assertEquals(
            BearerRowState.NeedsUsbPermission("CH341"),
            row(requirement = Requirement.NeedsUsbPermission("CH341")).state,
        )
        assertEquals(
            BearerRowState.NeedsHardware("camera"),
            row(requirement = Requirement.NeedsHardware("camera")).state,
        )
        assertEquals(BearerRowState.Unsupported("no wifi"), row(requirement = Requirement.Unsupported("no wifi")).state)
    }

    @Test
    fun `a disabled bearer is off whatever its availability says`() {
        assertEquals(BearerRowState.Off, row(enabled = false, availability = TransportAvailability.Active).state)
    }

    @Test
    fun `LoRa with a ready device and no region reads region unset`() {
        assertEquals(BearerRowState.RegionUnset, row(Bearer.LORA, enabled = false, availability = null).state)
    }

    @Test
    fun `LoRa with no device asks for one whatever the region`() {
        assertEquals(
            BearerRowState.NeedsHardware("CH341A stick"),
            row(Bearer.LORA, enabled = false, requirement = Requirement.NeedsHardware("CH341A stick")).state,
        )
        assertEquals(
            BearerRowState.NeedsUsbPermission("CH341"),
            row(Bearer.LORA, enabled = false, requirement = Requirement.NeedsUsbPermission("CH341")).state,
        )
    }

    @Test
    fun `an enabled bearer reports what the transport sees`() {
        assertEquals(BearerRowState.Active, row(availability = TransportAvailability.Active).state)
        assertEquals(BearerRowState.Ready, row(availability = TransportAvailability.Ready).state)
        assertEquals(BearerRowState.Ready, row(availability = null).state)
        assertEquals(
            BearerRowState.Unavailable("bt off"),
            row(availability = TransportAvailability.Unavailable("bt off")).state,
        )
        assertEquals(
            BearerRowState.NeedsPermission("scan"),
            row(availability = TransportAvailability.NeedsPermission("scan")).state,
        )
    }

    @Test
    fun `counters ride along`() {
        val r = row()
        assertEquals(listOf(1L, 2L, 3L), listOf(r.received, r.sent, r.relayed))
    }

    @Test
    fun `the node id is the firmware form and the header fields carry over`() {
        val status =
            nodeStatusUi(
                phase = NodePhase.Failed,
                nodeNum = 0x3a5806beL,
                longName = "Phone",
                shortName = "PHON",
                peerCount = 4,
                failure = "boom",
                settingsReset = true,
                bearers = emptyList(),
            )
        assertEquals("!3a5806be", status.nodeId)
        assertEquals(NodePhase.Failed, status.phase)
        assertEquals("Phone", status.longName)
        assertEquals("PHON", status.shortName)
        assertEquals(4, status.peerCount)
        assertEquals("boom", status.failure)
        assertTrue(status.settingsReset)
        assertTrue(status.bearers.isEmpty())
    }

    @Test
    fun `a small node number is zero padded`() {
        val status = nodeStatusUi(NodePhase.Running, 0x1fL, null, null, 0, null, false, emptyList())
        assertEquals("!0000001f", status.nodeId)
    }
}
