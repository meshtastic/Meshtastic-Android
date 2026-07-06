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
package org.meshtastic.feature.messaging.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.proto.Routing
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MessageItemTest {

    @Test
    fun mqttIconIsDisplayedWhenViaMqttIsTrue() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().minnieMouse
        val messageWithMqtt =
            Message(
                text = "Test message via MQTT",
                time = "10:00",
                fromLocal = false,
                status = MessageStatus.RECEIVED,
                snr = 2.5f,
                rssi = 90,
                hopsAway = 0,
                uuid = 1L,
                receivedTime = nowMillis,
                node = testNode,
                read = false,
                routingError = 0,
                packetId = 1234,
                emojis = listOf(),
                replyId = null,
                viaMqtt = true,
            )

        setContent {
            MessageItem(
                message = messageWithMqtt,
                node = testNode,
                selected = false,
                onClick = {},
                onLongClick = {},
                onStatusClick = {},
                ourNode = testNode,
            )
        }

        // Check that the MQTT icon is displayed
        onNodeWithContentDescription("MQTT").assertIsDisplayed()
    }

    @Test
    fun mqttIconIsNotDisplayedWhenViaMqttIsFalse() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().minnieMouse
        val messageWithoutMqtt =
            Message(
                text = "Test message not via MQTT",
                time = "10:00",
                fromLocal = false,
                status = MessageStatus.RECEIVED,
                snr = 2.5f,
                rssi = 90,
                hopsAway = 0,
                uuid = 1L,
                receivedTime = nowMillis,
                node = testNode,
                read = false,
                routingError = 0,
                packetId = 1234,
                emojis = listOf(),
                replyId = null,
                viaMqtt = false,
            )

        setContent {
            MessageItem(
                message = messageWithoutMqtt,
                node = testNode,
                selected = false,
                onClick = {},
                onLongClick = {},
                onStatusClick = {},
                ourNode = testNode,
            )
        }

        // Check that the MQTT icon is not displayed
        onNodeWithContentDescription("MQTT").assertDoesNotExist()
    }

    @Test
    fun messageItem_hasCorrectSemanticContentDescription() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().minnieMouse
        val message =
            Message(
                text = "Hello World",
                time = "10:00",
                fromLocal = false,
                status = MessageStatus.RECEIVED,
                snr = 2.5f,
                rssi = 90,
                hopsAway = 0,
                uuid = 1L,
                receivedTime = nowMillis,
                node = testNode,
                read = false,
                routingError = 0,
                packetId = 1234,
                emojis = listOf(),
                replyId = null,
                viaMqtt = false,
            )

        setContent {
            MessageItem(
                message = message,
                node = testNode,
                selected = false,
                onClick = {},
                onLongClick = {},
                onStatusClick = {},
                ourNode = testNode,
            )
        }

        // Verify that the node containing the message text exists and matches the text
        onNodeWithContentDescription("Message from ${testNode.user.long_name}: Hello World").assertIsDisplayed()
    }

    @Test
    fun localMessage_displaysDeliveryStatusText() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().mickeyMouse
        val message = localMessage(node = testNode, status = MessageStatus.RECEIVED)

        setContent {
            MessageItem(message = message, node = testNode, selected = false, onStatusClick = {}, ourNode = testNode)
        }

        onNodeWithText("Delivered to recipient", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun localDirectMessage_displaysImplicitAckWarningText() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().mickeyMouse
        val message = localMessage(node = testNode, status = MessageStatus.DELIVERED)

        setContent {
            MessageItem(
                message = message,
                node = testNode,
                selected = false,
                onStatusClick = {},
                ourNode = testNode,
                isDirectMessage = true,
            )
        }

        onNodeWithText("Relayed, not confirmed by recipient", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun localMessage_displaysRoutingErrorStatusText() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().mickeyMouse
        val message =
            localMessage(
                node = testNode,
                status = MessageStatus.ERROR,
                routingError = Routing.Error.MAX_RETRANSMIT.value,
            )

        setContent {
            MessageItem(message = message, node = testNode, selected = false, onStatusClick = {}, ourNode = testNode)
        }

        onNodeWithText("Failed to deliver to mesh", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun localMessageStatus_invokesStatusClick() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().mickeyMouse
        val message = localMessage(node = testNode, status = MessageStatus.QUEUED)
        var statusClicks = 0

        setContent {
            MessageItem(
                message = message,
                node = testNode,
                selected = false,
                onStatusClick = { statusClicks += 1 },
                ourNode = testNode,
            )
        }

        onNodeWithText("Sending...", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(MESSAGE_STATUS_LABEL_TEST_TAG, useUnmergedTree = true).performClick()

        assertEquals(1, statusClicks)
    }

    @Test
    fun localMessageStatus_doesNotExposeGenericIconDescription() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().mickeyMouse
        val message = localMessage(node = testNode, status = MessageStatus.ENROUTE)

        setContent {
            MessageItem(message = message, node = testNode, selected = false, onStatusClick = {}, ourNode = testNode)
        }

        onNodeWithText("Sending...", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("Message delivery status", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun localMessage(node: Node, status: MessageStatus, routingError: Int = 0) = Message(
        text = "Local message",
        time = "10:00",
        fromLocal = true,
        status = status,
        snr = 2.5f,
        rssi = 90,
        hopsAway = 0,
        uuid = 1L,
        receivedTime = nowMillis,
        node = node,
        read = false,
        routingError = routingError,
        packetId = 1234,
        emojis = listOf(),
        replyId = null,
        viaMqtt = false,
    )
}
