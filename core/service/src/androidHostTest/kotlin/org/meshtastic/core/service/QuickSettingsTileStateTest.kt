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

import android.service.quicksettings.Tile
import org.junit.Test
import org.meshtastic.core.model.ConnectionState
import kotlin.test.assertEquals

class QuickSettingsTileStateTest {

    private fun tile(
        connectionState: ConnectionState,
        hasSelectedDevice: Boolean = true,
        serviceRunning: Boolean = true,
        ourShortName: String? = "ABCD",
    ) = quickSettingsTileState(connectionState, hasSelectedDevice, serviceRunning, ourShortName)

    @Test
    fun `connected is active with our short name and taps disconnect`() {
        assertEquals(
            QuickSettingsTileState(
                Tile.STATE_ACTIVE,
                QuickSettingsTileSubtitle.NodeName("ABCD"),
                QuickSettingsTileAction.Disconnect,
            ),
            tile(ConnectionState.Connected),
        )
    }

    @Test
    fun `connected before our node is known falls back to the connected label`() {
        listOf(null, "", "  ").forEach { name ->
            assertEquals(
                QuickSettingsTileSubtitle.Connected,
                tile(ConnectionState.Connected, ourShortName = name).subtitle,
            )
        }
    }

    @Test
    fun `connecting is active and a tap cancels it`() {
        assertEquals(
            QuickSettingsTileState(
                Tile.STATE_ACTIVE,
                QuickSettingsTileSubtitle.Connecting,
                QuickSettingsTileAction.Disconnect,
            ),
            tile(ConnectionState.Connecting),
        )
    }

    @Test
    fun `a sleeping radio is inactive and a tap connects`() {
        assertEquals(
            QuickSettingsTileState(
                Tile.STATE_INACTIVE,
                QuickSettingsTileSubtitle.DeviceSleeping,
                QuickSettingsTileAction.Connect,
            ),
            tile(ConnectionState.DeviceSleep),
        )
    }

    @Test
    fun `a disconnected remembered radio is inactive and a tap connects`() {
        assertEquals(
            QuickSettingsTileState(
                Tile.STATE_INACTIVE,
                QuickSettingsTileSubtitle.Disconnected,
                QuickSettingsTileAction.Connect,
            ),
            tile(ConnectionState.Disconnected),
        )
    }

    @Test
    fun `with no radio selected every state opens the connect tab`() {
        listOf(
            ConnectionState.Connected,
            ConnectionState.Connecting,
            ConnectionState.DeviceSleep,
            ConnectionState.Disconnected,
        )
            .forEach { state ->
                assertEquals(
                    QuickSettingsTileState(
                        Tile.STATE_INACTIVE,
                        QuickSettingsTileSubtitle.NoDeviceSelected,
                        QuickSettingsTileAction.OpenConnections,
                    ),
                    tile(state, hasSelectedDevice = false),
                    "$state",
                )
            }
    }

    @Test
    fun `a stale connected state with the service stopped reads as disconnected`() {
        assertEquals(
            QuickSettingsTileState(
                Tile.STATE_INACTIVE,
                QuickSettingsTileSubtitle.Disconnected,
                QuickSettingsTileAction.Connect,
            ),
            tile(ConnectionState.Connected, serviceRunning = false),
        )
    }
}
