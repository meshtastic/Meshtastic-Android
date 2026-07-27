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
package org.meshtastic.core.service

import org.junit.Test
import org.meshtastic.core.model.ConnectionState
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rules for [CompanionPresencePolicy]: presence events act only on the selected radio, and appeared events never fight
 * a live connection.
 */
class CompanionPresencePolicyTest {

    private companion object {
        const val MAC = "AA:BB:CC:DD:EE:FF"
        const val OTHER_MAC = "11:22:33:44:55:66"
    }

    // region appeared → start

    @Test
    fun `appeared selected radio starts the service while disconnected`() {
        listOf(ConnectionState.Disconnected, ConnectionState.Connecting, ConnectionState.DeviceSleep).forEach { state ->
            assertTrue(
                CompanionPresencePolicy.shouldStartOnAppeared(MAC, MAC, state),
                "appeared should start while $state",
            )
        }
    }

    @Test
    fun `appeared event matches the selected radio case-insensitively`() {
        assertTrue(CompanionPresencePolicy.shouldStartOnAppeared(MAC.lowercase(), MAC, ConnectionState.Disconnected))
    }

    @Test
    fun `appeared event never starts for a non-selected radio`() {
        // Other associations are just older radios the user still owns; their presence is not a connect request.
        assertFalse(CompanionPresencePolicy.shouldStartOnAppeared(OTHER_MAC, MAC, ConnectionState.Disconnected))
        assertFalse(CompanionPresencePolicy.shouldStartOnAppeared(null, MAC, ConnectionState.Disconnected))
        assertFalse(CompanionPresencePolicy.shouldStartOnAppeared(MAC, null, ConnectionState.Disconnected))
    }

    @Test
    fun `appeared event does not restart a live connection`() {
        assertFalse(CompanionPresencePolicy.shouldStartOnAppeared(MAC, MAC, ConnectionState.Connected))
    }

    // endregion
}
