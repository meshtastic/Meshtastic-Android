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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow
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
    fun directMessageWithoutSnrDoesNotFabricateAZeroReading() = runComposeUiTest {
        // Before DataPacket/Message.snr became nullable, an absent SNR narrowed to 0f on the way through the mapper
        // and this row rendered "SNR 0.00 dB" — a measurement the radio never took.
        val testNode = NodePreviewParameterProvider().minnieMouse
        val message = directMessage(node = testNode, snr = null)

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

        onNodeWithText("SNR 0.00 dB", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun directMessageWithZeroSnrShowsTheReading() = runComposeUiTest {
        // The other half: 0 dB is a real, strong reading and must still render.
        val testNode = NodePreviewParameterProvider().minnieMouse
        val message = directMessage(node = testNode, snr = 0f)

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

        onNodeWithText("SNR 0.00 dB", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun directMessage(node: Node, snr: Float?) = Message(
        text = "Direct message",
        time = "10:00",
        fromLocal = false,
        status = MessageStatus.RECEIVED,
        snr = snr,
        rssi = -90,
        hopsAway = 0,
        uuid = 1L,
        receivedTime = nowMillis,
        node = node,
        read = false,
        routingError = 0,
        packetId = 1234,
        emojis = listOf(),
        replyId = null,
        viaMqtt = false,
    )

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
    fun channelKeyMismatch_displaysTerminalStatusText() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().mickeyMouse
        val message =
            localMessage(node = testNode, status = MessageStatus.ERROR, routingError = Routing.Error.NO_CHANNEL.value)

        setContent {
            MessageItem(message = message, node = testNode, selected = false, onStatusClick = {}, ourNode = testNode)
        }

        onNodeWithText("Channel/key mismatch", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun retryableRoutingError_usesWarningStatusColor() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().mickeyMouse
        val message =
            localMessage(
                node = testNode,
                status = MessageStatus.ERROR,
                routingError = Routing.Error.MAX_RETRANSMIT.value,
            )
        var warningColor = Color.Unspecified

        setContent {
            AppTheme {
                warningColor = MaterialTheme.colorScheme.StatusYellow
                MessageItem(
                    message = message,
                    node = testNode,
                    selected = false,
                    onStatusClick = {},
                    ourNode = testNode,
                )
            }
        }

        onNodeWithTag(MESSAGE_STATUS_LABEL_TEST_TAG, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(MessageStatusColorKey, warningColor))
    }

    @Test
    fun messageStatusDialog_displaysRoutingFailureExplanation() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().mickeyMouse
        val message =
            localMessage(
                node = testNode,
                status = MessageStatus.ERROR,
                routingError = Routing.Error.MAX_RETRANSMIT.value,
            )

        setContent { MessageStatusDialog(message = message, resendOption = true, onResend = {}, onDismiss = {}) }

        onNodeWithText("Failed to deliver to mesh").assertIsDisplayed()
        onNodeWithText("No node confirmed this message. Try again when you have better signal or more mesh coverage.")
            .assertIsDisplayed()
        onNodeWithText("Resend").assertIsDisplayed()
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

    @Test
    fun fullMessageTimestampIsDisplayedInMessageHeader() = runComposeUiTest {
        val testNode = NodePreviewParameterProvider().mickeyMouse
        val meshTime = 1_700_000_000_000L
        val message =
            localMessage(node = testNode, status = MessageStatus.RECEIVED).copy(time = "compact", meshTime = meshTime)
        val expectedTimestamp = DateFormatter.formatDateTime(meshTime)

        setContent {
            MessageItem(
                message = message,
                node = testNode,
                selected = false,
                onStatusClick = {},
                ourNode = testNode,
                showFullMessageTimestamp = true,
            )
        }

        onNodeWithText(expectedTimestamp, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("compact", useUnmergedTree = true).assertDoesNotExist()
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
