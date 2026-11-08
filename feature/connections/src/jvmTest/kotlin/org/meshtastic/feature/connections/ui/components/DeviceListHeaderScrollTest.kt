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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.feature.connections.model.DeviceListEntry
import kotlin.test.Test

/**
 * The stacked Connections screen puts its connection card, warnings and permission cards in the device list's header,
 * so at a large font scale the header can be taller than the screen. Both the bottom of the header (where a permission
 * card's action sits) and the devices below it must stay reachable by scrolling the one list.
 */
@OptIn(ExperimentalTestApi::class)
class DeviceListHeaderScrollTest {

    @Test
    fun headerTallerThanTheViewportScrollsWithTheDevices() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(360.dp, 400.dp)) {
                    DeviceList(
                        connectionState = ConnectionState.Disconnected,
                        selectedDevice = "",
                        bleDevices = listOf(DeviceListEntry.Mock(name = "Field radio")),
                        usbDevices = emptyList(),
                        discoveredTcpDevices = emptyList(),
                        recentTcpDevices = emptyList(),
                        isBleScanning = false,
                        isNetworkScanning = false,
                        activeTransport = DeviceType.BLE,
                        onSelectDevice = {},
                        onToggleBleScan = {},
                        onToggleNetworkScan = {},
                        onAddManualAddress = { _, _ -> },
                        onRemoveRecentAddress = {},
                        header = {
                            Column {
                                Spacer(modifier = Modifier.fillMaxWidth().height(1200.dp))
                                Text("Grant permission")
                            }
                        },
                    )
                }
            }
        }

        onNodeWithText("Grant permission").performScrollTo().assertIsDisplayed()
        onNode(hasScrollAction()).performScrollToNode(hasText("Field radio"))
        onNodeWithText("Field radio").assertIsDisplayed()
    }
}
