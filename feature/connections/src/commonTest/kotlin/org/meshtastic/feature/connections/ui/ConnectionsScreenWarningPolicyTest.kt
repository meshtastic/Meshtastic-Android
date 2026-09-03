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
package org.meshtastic.feature.connections.ui

import org.meshtastic.core.model.service.LockdownState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionsScreenWarningPolicyTest {

    @Test
    fun `config warnings wait for the active node handshake`() {
        assertFalse(
            canShowConfigWarnings(
                connectedWithNode = true,
                activeNodeInfoReady = false,
                lockdownState = LockdownState.Unlocked,
                isManaged = false,
                isPhysicalDevice = true,
            ),
        )
    }

    @Test
    fun `config warnings show once the active node is ready and policy allows writes`() {
        assertTrue(
            canShowConfigWarnings(
                connectedWithNode = true,
                activeNodeInfoReady = true,
                lockdownState = LockdownState.Unlocked,
                isManaged = false,
                isPhysicalDevice = true,
            ),
        )
    }

    @Test
    fun `config warnings stay hidden while config writes are locked`() {
        assertFalse(
            canShowConfigWarnings(
                connectedWithNode = true,
                activeNodeInfoReady = true,
                lockdownState = LockdownState.Locked("needs_auth"),
                isManaged = false,
                isPhysicalDevice = true,
            ),
        )
    }

    @Test
    fun `config warnings stay hidden while lockdown response is pending`() {
        assertFalse(
            canShowConfigWarnings(
                connectedWithNode = true,
                activeNodeInfoReady = true,
                lockdownState = LockdownState.AwaitingResponse,
                isManaged = false,
                isPhysicalDevice = true,
            ),
        )
    }

    @Test
    fun `config warnings stay hidden for managed devices after lockdown unlock`() {
        assertFalse(
            canShowConfigWarnings(
                connectedWithNode = true,
                activeNodeInfoReady = true,
                lockdownState = LockdownState.Unlocked,
                isManaged = true,
                isPhysicalDevice = true,
            ),
        )
    }

    @Test
    fun `config warnings show when lockdown is absent and managed mode allows local config`() {
        assertTrue(
            canShowConfigWarnings(
                connectedWithNode = true,
                activeNodeInfoReady = true,
                lockdownState = LockdownState.None,
                isManaged = false,
                isPhysicalDevice = true,
            ),
        )
    }

    @Test
    fun `config warnings show when lockdown is explicitly disabled`() {
        assertTrue(
            canShowConfigWarnings(
                connectedWithNode = true,
                activeNodeInfoReady = true,
                lockdownState = LockdownState.Disabled,
                isManaged = false,
                isPhysicalDevice = true,
            ),
        )
    }

    @Test
    fun `config warnings stay hidden without a connected node`() {
        assertFalse(
            canShowConfigWarnings(
                connectedWithNode = false,
                activeNodeInfoReady = true,
                lockdownState = LockdownState.Unlocked,
                isManaged = false,
                isPhysicalDevice = true,
            ),
        )
    }

    @Test
    fun `config warnings stay hidden for virtual devices`() {
        assertFalse(
            canShowConfigWarnings(
                connectedWithNode = true,
                activeNodeInfoReady = true,
                lockdownState = LockdownState.Unlocked,
                isManaged = false,
                isPhysicalDevice = false,
            ),
        )
    }
}
