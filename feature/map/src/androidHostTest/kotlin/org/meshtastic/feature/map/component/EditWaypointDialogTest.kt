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
package org.meshtastic.feature.map.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import okio.ByteString.Companion.encodeUtf8
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.send
import org.meshtastic.core.resources.waypoint_recipient_broadcast
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import org.meshtastic.proto.Waypoint
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditWaypointDialogTest {

    private val waypoint = Waypoint(id = 1, name = "Test Waypoint")
    private val node = TestDataFactory.createTestNode(num = 42, userId = "!a1b2c3d4", longName = "Alice")
    private val channelSet =
        ChannelSet(settings = listOf(ChannelSettings(name = "Primary"), ChannelSettings(name = "Secondary")))

    @Test
    fun sendDefaultsToPrimaryChannel() = runComposeUiTest {
        var sentContactKey: String? = null
        setContent {
            EditWaypointDialog(
                waypoint = waypoint,
                displayUnits = DisplayUnits.METRIC,
                nodes = listOf(node),
                ourNode = null,
                channelSet = channelSet,
                onSend = { _, contactKey -> sentContactKey = contactKey },
                onDelete = {},
                onDismissRequest = {},
            )
        }

        // The primary channel (index 0) is labelled with its configured name, not a generic "Broadcast" string.
        onNodeWithText("Primary").assertIsDisplayed()
        onNodeWithText(getString(Res.string.send)).performClick()

        assertEquals("0${NodeAddress.ID_BROADCAST}", sentContactKey)
    }

    @Test
    fun sendDefaultsToGenericBroadcastLabelWhenChannelSetUnavailable() = runComposeUiTest {
        setContent {
            EditWaypointDialog(
                waypoint = waypoint,
                displayUnits = DisplayUnits.METRIC,
                nodes = listOf(node),
                ourNode = null,
                channelSet = null,
                onSend = { _, _ -> },
                onDelete = {},
                onDismissRequest = {},
            )
        }

        // No channel info (e.g. disconnected) falls back to the generic "Broadcast" label.
        onNodeWithText(getString(Res.string.waypoint_recipient_broadcast)).assertIsDisplayed()
    }

    @Test
    fun sendOffersOnlyBroadcastWhenChannelSetSettingsIsEmpty() = runComposeUiTest {
        setContent {
            EditWaypointDialog(
                waypoint = waypoint,
                displayUnits = DisplayUnits.METRIC,
                nodes = listOf(node),
                ourNode = null,
                // Non-null but empty settings (e.g. right after a fresh install, before channel sync completes) must
                // still offer a Broadcast option rather than rendering zero channel rows.
                channelSet = ChannelSet(settings = emptyList()),
                onSend = { _, _ -> },
                onDelete = {},
                onDismissRequest = {},
            )
        }

        onNodeWithText(getString(Res.string.waypoint_recipient_broadcast)).assertIsDisplayed()
    }

    @Test
    fun pickingANonPkcNodeRoutesViaItsOwnChannel() = runComposeUiTest {
        var sentContactKey: String? = null
        setContent {
            EditWaypointDialog(
                waypoint = waypoint,
                displayUnits = DisplayUnits.METRIC,
                nodes = listOf(node),
                ourNode = null,
                channelSet = channelSet,
                onSend = { _, contactKey -> sentContactKey = contactKey },
                onDelete = {},
                onDismissRequest = {},
            )
        }

        // The clickable element is the TextButton showing the *current* recipient value ("Primary" before any
        // selection), not the static "Send to" label beside it.
        onNodeWithText("Primary").performClick()
        onNodeWithText("Alice").assertIsDisplayed()
        onNodeWithText("Alice").performClick()
        onNodeWithText(getString(Res.string.send)).performClick()

        // Neither this node nor our local node advertises PKC, so the DM routes on the node's own channel (0),
        // not the PKC channel index — matching NodeListViewModel.getDirectMessageRoute's convention.
        assertEquals("0!a1b2c3d4", sentContactKey)
    }

    @Test
    fun pickingAPkcCapableNodeRoutesViaPkcChannel() = runComposeUiTest {
        var sentContactKey: String? = null
        val pkcNode = node.copy(publicKey = "node-key".encodeUtf8())
        val ourPkcNode =
            TestDataFactory.createTestNode(num = 1, userId = "!local0001").copy(publicKey = "our-key".encodeUtf8())
        setContent {
            EditWaypointDialog(
                waypoint = waypoint,
                displayUnits = DisplayUnits.METRIC,
                nodes = listOf(pkcNode),
                ourNode = ourPkcNode,
                channelSet = channelSet,
                onSend = { _, contactKey -> sentContactKey = contactKey },
                onDelete = {},
                onDismissRequest = {},
            )
        }

        onNodeWithText("Primary").performClick()
        onNodeWithText("Alice").performClick()
        onNodeWithText(getString(Res.string.send)).performClick()

        // Both ends support PKC, so the DM routes on the dedicated PKC channel index.
        assertEquals("${NodeAddress.PKC_CHANNEL_INDEX}!a1b2c3d4", sentContactKey)
    }

    @Test
    fun pickingASecondaryChannelRoutesSendToThatChannel() = runComposeUiTest {
        var sentContactKey: String? = null
        setContent {
            EditWaypointDialog(
                waypoint = waypoint,
                displayUnits = DisplayUnits.METRIC,
                nodes = listOf(node),
                ourNode = null,
                channelSet = channelSet,
                onSend = { _, contactKey -> sentContactKey = contactKey },
                onDelete = {},
                onDismissRequest = {},
            )
        }

        onNodeWithText("Primary").performClick()
        onNodeWithText("Secondary").assertIsDisplayed()
        onNodeWithText("Secondary").performClick()
        onNodeWithText(getString(Res.string.send)).performClick()

        assertEquals("1${NodeAddress.ID_BROADCAST}", sentContactKey)
    }

    @Test
    fun initialContactKeySeedsTheSelectedRecipient() = runComposeUiTest {
        var sentContactKey: String? = null
        setContent {
            EditWaypointDialog(
                waypoint = waypoint,
                displayUnits = DisplayUnits.METRIC,
                nodes = listOf(node),
                ourNode = null,
                channelSet = channelSet,
                // Simulates re-opening the dialog after geofence box authoring with a previously-picked recipient.
                initialContactKey = "1${NodeAddress.ID_BROADCAST}",
                onSend = { _, contactKey -> sentContactKey = contactKey },
                onDelete = {},
                onDismissRequest = {},
            )
        }

        onNodeWithText("Secondary").assertIsDisplayed()
        onNodeWithText(getString(Res.string.send)).performClick()

        assertEquals("1${NodeAddress.ID_BROADCAST}", sentContactKey)
    }
}
