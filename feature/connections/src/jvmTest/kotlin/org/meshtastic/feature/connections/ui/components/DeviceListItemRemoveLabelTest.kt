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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.feature.connections.model.DeviceListEntry
import kotlin.test.Test

/** A recent address is removed by long-press only, so TalkBack has to announce what that action does. */
@OptIn(ExperimentalTestApi::class)
class DeviceListItemRemoveLabelTest {

    @Test
    fun longPressToRemoveIsLabeledForScreenReaders() = runComposeUiTest {
        setContent {
            MaterialTheme {
                DeviceListItem(
                    connectionState = ConnectionState.Disconnected,
                    device = DeviceListEntry.Tcp(name = "Sensor", fullAddress = "t192.168.0.2"),
                    onSelect = {},
                    onDelete = {},
                )
            }
        }

        val longClickLabeled =
            SemanticsMatcher("long-click labeled Remove") {
                it.config.getOrElseNullable(SemanticsActions.OnLongClick) { null }?.label == "Remove"
            }
        waitUntil(timeoutMillis = 5_000) { onAllNodes(longClickLabeled).fetchSemanticsNodes().isNotEmpty() }
    }
}
