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
package org.meshtastic.feature.node.list

import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.proto.DeviceMetadata
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * design#115: the status-message edit action belongs to the connected local node on firmware that has the module, and
 * is absent — never disabled — everywhere else.
 */
class StatusMessageActionGateTest {

    private fun node(num: Int, firmware: String?) =
        Node(num = num, metadata = firmware?.let { DeviceMetadata(firmware_version = it) })

    private val ourNode = node(num = 1, firmware = "2.8.0")

    @Test
    fun `the local node offers the action while connected on 2 8 firmware`() {
        assertTrue(canEditStatusMessage(ourNode, ourNode, ConnectionState.Connected))
    }

    @Test
    fun `another node never offers the action`() {
        val other = node(num = 2, firmware = "2.8.0")

        assertFalse(canEditStatusMessage(other, ourNode, ConnectionState.Connected))
    }

    @Test
    fun `older firmware does not offer the action`() {
        val old = node(num = 1, firmware = "2.7.21")

        assertFalse(canEditStatusMessage(old, old, ConnectionState.Connected))
    }

    @Test
    fun `firmware metadata we have not read yet does not offer the action`() {
        val unknown = node(num = 1, firmware = null)

        assertFalse(canEditStatusMessage(unknown, unknown, ConnectionState.Connected))
    }

    @Test
    fun `a disconnected radio does not offer the action`() {
        assertFalse(canEditStatusMessage(ourNode, ourNode, ConnectionState.Disconnected))
    }

    @Test
    fun `a node list with no local node offers the action nowhere`() {
        assertFalse(canEditStatusMessage(ourNode, null, ConnectionState.Connected))
    }
}
