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
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import kotlin.test.Test

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
        onNodeWithContentDescription("via MQTT").assertIsDisplayed()
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
        onNodeWithContentDescription("via MQTT").assertDoesNotExist()
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
}
