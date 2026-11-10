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
package org.meshtastic.feature.node.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.mute_notifications
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.DeviceMetadata
import kotlin.test.Test

/**
 * Muting is `toggle_muted_node` sent to our own node, so our firmware decides whether it works. A remote node rarely
 * has metadata at all - it only arrives in a remote-admin reply - so gating on the remote node hid Mute almost always.
 */
@OptIn(ExperimentalTestApi::class)
class MuteActionGateTest {

    private fun nodeOn(num: Int, firmware: String?) = Node(
        num = num,
        metadata = firmware?.let { DeviceMetadata.Builder().also { wb -> wb.firmware_version = it }.build() },
    )

    private val remoteWithoutMetadata = nodeOn(num = 2, firmware = null)

    @Test
    fun `node detail offers mute for a remote node without metadata when our firmware supports it`() =
        runComposeUiTest {
            setContent {
                AppTheme {
                    DeviceActions(
                        node = remoteWithoutMetadata,
                        ourNode = nodeOn(num = 1, firmware = "2.7.18"),
                        lastTracerouteTime = null,
                        lastRequestNeighborsTime = null,
                        availableLogs = emptySet(),
                        onAction = {},
                        displayUnits = MeasurementSystem.METRIC,
                        isFahrenheit = false,
                    )
                }
            }

            onNodeWithText(getString(Res.string.mute_notifications)).assertExists()
        }

    @Test
    fun `node detail hides mute when our firmware predates it`() = runComposeUiTest {
        setContent {
            AppTheme {
                DeviceActions(
                    node = nodeOn(num = 2, firmware = "2.8.0"),
                    ourNode = nodeOn(num = 1, firmware = "2.7.15"),
                    lastTracerouteTime = null,
                    lastRequestNeighborsTime = null,
                    availableLogs = emptySet(),
                    onAction = {},
                    displayUnits = MeasurementSystem.METRIC,
                    isFahrenheit = false,
                )
            }
        }

        onNodeWithText(getString(Res.string.mute_notifications)).assertDoesNotExist()
    }

    @Test
    fun `node list menu offers mute for a remote node without metadata when our firmware supports it`() =
        runComposeUiTest {
            setContent {
                AppTheme {
                    NodeContextMenu(
                        expanded = true,
                        node = remoteWithoutMetadata,
                        ourNode = nodeOn(num = 1, firmware = "2.7.18"),
                        onFavorite = {},
                        onMute = {},
                        onMessage = {},
                        onTraceRoute = {},
                        lastTracerouteTime = null,
                        onIgnore = {},
                        onRemove = {},
                        onDismiss = {},
                    )
                }
            }

            onNodeWithText(getString(Res.string.mute_notifications)).assertExists()
        }
}
