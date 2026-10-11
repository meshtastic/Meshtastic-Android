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

import org.meshtastic.node.host.Bearer
import org.meshtastic.node.host.NodePhase
import org.meshtastic.node.host.NodeStatus
import org.meshtastic.node.host.Requirement
import org.meshtastic.node.transport.TransportAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeStatusUiTest {

    private fun row(
        bearer: Bearer = Bearer.UDP,
        enabled: Boolean = true,
        availability: TransportAvailability? = TransportAvailability.Active,
        requirement: Requirement = Requirement.Ready,
    ) = bearerRow(bearer, enabled, availability, requirement, received = 1, sent = 2, relayed = 3)

    @Test
    fun `only host bearers carry a switch`() {
        assertFalse(row(Bearer.UDP).switchable)
        assertFalse(row(Bearer.LORA).switchable)
        assertTrue(row(Bearer.WIFI_AWARE).switchable)
        assertTrue(row(Bearer.AUDIO).switchable)
        assertTrue(row(Bearer.VIDEO).switchable)
    }

    @Test
    fun `a requirement outranks the availability`() {
        val state = row(availability = TransportAvailability.Active, requirement = Requirement.NeedsPermission("mic"))
        assertEquals(BearerRowState.NeedsPermission("mic"), state.state)
        assertEquals(
            BearerRowState.NeedsUsbPermission("CH341"),
            row(requirement = Requirement.NeedsUsbPermission("CH341")).state,
        )
        assertEquals(BearerRowState.NeedsHardware("camera"), row(requirement = Requirement.NeedsHardware("camera")).state)
        assertEquals(BearerRowState.Unsupported("no wifi"), row(requirement = Requirement.Unsupported("no wifi")).state)
    }

    @Test
    fun `a disabled bearer is off whatever its availability says`() {
        assertEquals(BearerRowState.Off, row(enabled = false, availability = TransportAvailability.Active).state)
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
            NodeStatus(
                phase = NodePhase.Failed,
                nodeNum = 0x3a5806beL,
                longName = "Phone",
                shortName = "PHON",
                bearers = emptyMap(),
                peerCount = 4,
                failure = "boom",
                settingsReset = true,
            )
                .toUi()
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
        val status = NodeStatus(NodePhase.Running, 0x1fL, null, null, emptyMap(), 0).toUi()
        assertEquals("!0000001f", status.nodeId)
    }
}
