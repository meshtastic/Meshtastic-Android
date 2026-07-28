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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.feature.connections.model.DeviceListEntry
import kotlin.test.Test

/**
 * 0 dBm is the strongest value on the RSSI scale, so an unknown signal must not render as one. The null and zero cases
 * are pinned together — either alone lets the two states collapse back into a single "0".
 */
@OptIn(ExperimentalTestApi::class)
class DeviceListItemRssiTest {

    @Test
    fun zeroRssi_rendersAsZeroDbm() = runComposeUiTest {
        setDeviceListItem(rssi = 0)
        onNodeWithText("0 dBm", substring = true).assertIsDisplayed()
    }

    @Test
    fun negativeRssi_rendersTheReading() = runComposeUiTest {
        setDeviceListItem(rssi = -70)
        onNodeWithText("-70 dBm", substring = true).assertIsDisplayed()
    }

    @Test
    fun absentRssi_rendersNoReadingAtAll() = runComposeUiTest {
        setDeviceListItem(rssi = null)
        onNodeWithText("dBm", substring = true).assertDoesNotExist()
    }

    private fun ComposeUiTest.setDeviceListItem(rssi: Int?) = setContent {
        MaterialTheme {
            DeviceListItem(
                connectionState = ConnectionState.Disconnected,
                device = DeviceListEntry.Tcp(name = "Sensor", fullAddress = "t192.168.0.2"),
                onSelect = {},
                rssi = rssi,
            )
        }
    }
}
