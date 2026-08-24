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

import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.ui.component.preview.NodePreviewParameterProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MessageTimestampTest {
    @Test
    fun compactMessageTimestampDoesNotInvokeFullFormatter() {
        var formatterCalled = false

        val timestamp =
            formatMessageTimestamp(message(time = "compact"), showFullMessageTimestamp = false) {
                formatterCalled = true
                "full"
            }

        assertEquals("compact", timestamp)
        assertFalse(formatterCalled)
    }

    @Test
    fun fullMessageTimestampUsesRawMeshTime() {
        val meshTime = 1_700_000_000_000L
        val message = message(time = "compact", receivedTime = meshTime + 1_000L, meshTime = meshTime)

        val timestamp = formatMessageTimestamp(message, showFullMessageTimestamp = true) { "formatted:$it" }

        assertEquals("formatted:$meshTime", timestamp)
    }

    @Test
    fun fullMessageTimestampFallsBackToReceivedTime() {
        val receivedTime = 1_700_000_000_000L
        val message = message(time = "compact", receivedTime = receivedTime, meshTime = 0L)

        val timestamp = formatMessageTimestamp(message, showFullMessageTimestamp = true) { "formatted:$it" }

        assertEquals("formatted:$receivedTime", timestamp)
    }

    private fun message(time: String, receivedTime: Long = 1L, meshTime: Long = 0L): Message {
        val node = NodePreviewParameterProvider().mickeyMouse
        return Message(
            text = "Message",
            time = time,
            fromLocal = true,
            status = MessageStatus.RECEIVED,
            snr = null,
            rssi = null,
            hopsAway = 0,
            uuid = 1L,
            receivedTime = receivedTime,
            meshTime = meshTime,
            node = node,
            read = false,
            routingError = 0,
            packetId = 1,
            emojis = emptyList(),
            replyId = null,
        )
    }
}
