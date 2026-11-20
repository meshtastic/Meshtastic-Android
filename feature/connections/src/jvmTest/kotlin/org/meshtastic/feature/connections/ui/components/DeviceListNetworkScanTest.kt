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
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.network_scan_unavailable
import org.meshtastic.core.resources.no_network_devices_saved
import org.meshtastic.core.resources.no_network_devices_seen
import org.meshtastic.core.resources.scan_network_devices
import kotlin.test.Test

/** The Network pane offers a scan only where something can discover a radio; a browser adds one by address. */
@OptIn(ExperimentalTestApi::class)
class DeviceListNetworkScanTest {

    @Test
    fun withoutANetworkScanThePaneHasNoScanToggleAndSaysToAddByAddress() = runComposeUiTest {
        setNetworkPane(networkScanSupported = false)

        onNodeWithText(getString(Res.string.scan_network_devices)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.no_network_devices_seen)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.no_network_devices_saved)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.network_scan_unavailable)).assertIsDisplayed()
    }

    @Test
    fun withANetworkScanThePaneOffersTheScanToggle() = runComposeUiTest {
        setNetworkPane(networkScanSupported = true)

        onNodeWithText(getString(Res.string.scan_network_devices)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.no_network_devices_seen)).assertIsDisplayed()
    }

    private fun ComposeUiTest.setNetworkPane(networkScanSupported: Boolean) = setContent {
        MaterialTheme {
            DeviceList(
                connectionState = ConnectionState.Disconnected,
                selectedDevice = "",
                bleDevices = emptyList(),
                usbDevices = emptyList(),
                discoveredTcpDevices = emptyList(),
                recentTcpDevices = emptyList(),
                isBleScanning = false,
                isNetworkScanning = false,
                activeTransport = DeviceType.TCP,
                onSelectDevice = {},
                onToggleBleScan = {},
                onToggleNetworkScan = {},
                onAddManualAddress = { _, _ -> },
                onRemoveRecentAddress = {},
                networkScanSupported = networkScanSupported,
            )
        }
    }
}
